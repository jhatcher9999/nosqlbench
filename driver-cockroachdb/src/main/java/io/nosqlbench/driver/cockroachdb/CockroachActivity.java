package io.nosqlbench.driver.cockroachdb;

import java.util.*;
import java.util.function.Function;

import io.nosqlbench.engine.api.activityconfig.yaml.OpTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Timer;
import io.nosqlbench.engine.api.activityapi.core.ActivityDefObserver;
import io.nosqlbench.engine.api.activityapi.planning.OpSequence;
import io.nosqlbench.engine.api.activityapi.planning.SequencePlanner;
import io.nosqlbench.engine.api.activityapi.planning.SequencerType;
import io.nosqlbench.engine.api.activityconfig.ParsedStmt;
import io.nosqlbench.engine.api.activityconfig.StatementsLoader;
import io.nosqlbench.engine.api.activityconfig.yaml.StmtDef;
import io.nosqlbench.engine.api.activityconfig.yaml.StmtsDocList;
import io.nosqlbench.engine.api.activityimpl.ActivityDef;
import io.nosqlbench.engine.api.activityimpl.SimpleActivity;
import io.nosqlbench.engine.api.metrics.ActivityMetrics;
import io.nosqlbench.engine.api.templating.StrInterpolator;
import io.nosqlbench.engine.api.util.TagFilter;

public class CockroachActivity extends SimpleActivity implements ActivityDefObserver {

    private final static Logger logger = LoggerFactory.getLogger(CockroachActivity.class);

    private String yamlLoc;

    //Cockroach-specific stuff
    private DataSource ds;
    private Connection connection;
    private String connectionString;

    private boolean showQuery;
    private int maxTries;

    private OpSequence<ReadyCockroachStatement> opSequence;

    Timer bindTimer;
    Timer resultTimer;
    Timer resultSuccessTimer;
    Histogram triesHisto;
    Histogram resultSetSizeHisto;

    public CockroachActivity(ActivityDef activityDef) {
        super(activityDef);
    }

    @Override
    public synchronized void onActivityDefUpdate(ActivityDef activityDef) {
        super.onActivityDefUpdate(activityDef);

        // sanity check
        yamlLoc = activityDef.getParams().getOptionalString("yaml", "workload")
                             .orElseThrow(() -> new IllegalArgumentException("yaml is not defined"));

        connectionString = activityDef.getParams().getOptionalString("connectionString")
                                      .orElseThrow(() -> new IllegalArgumentException("connectionString is not defined"));
    }

    @Override
    public void initActivity() {
        logger.debug("initializing activity: " + this.activityDef.getAlias());
        onActivityDefUpdate(activityDef);

        opSequence = initOpSequencer();
        setDefaultsFromOpSequence(opSequence);

        //cockroach-specific stuff
        //TODO: make this handle secure connections (certs, etc.)
        try {
            //expecting the connection string to look something like this:
            //jdbc:postgresql://maxroach@localhost:26257/bank?sslmode=disable
            Properties propertiesDefault = new Properties();
            Properties properties = org.postgresql.Driver.parseURL(connectionString, propertiesDefault);

            if (properties == null) {
                throw new IllegalArgumentException("Illegal Postgresql JDBC connection string: " + connectionString);
            }

            String user = null;
            PGSimpleDataSource ds = new PGSimpleDataSource();
            Set<Map.Entry<Object, Object>> entries = properties.entrySet();
            for(Map.Entry<Object, Object> entry : entries) {
                if (entry.getKey().equals("PGHOST")) {
                    String[] hostsRaw = properties.getProperty("PGHOST", "localhost").split(",");
                    String[] hosts = new String[hostsRaw.length];
                    for(int i = 0; i < hostsRaw.length; i++)
                    {
                        String hostRaw = hostsRaw[i];
                        //the connection string parser doesn't handle user@domain syntax, so I'm manually handling here
                        if (hostRaw.contains("@")) {
                            String[] parts = hostRaw.split("@");
                            if (parts.length != 2) {
                                throw new IllegalArgumentException("Unknown element in Postgresql JDBC connection string: " + hostRaw);
                            } else {
                                user = parts[0];
                                hosts[i] = parts[1];
                            }
                        } else {
                            hosts[i] = hostsRaw[i];
                        }
                    }
                    ds.setServerNames(hosts);
                } else if (entry.getKey().equals("PGPORT")) {
                    String[] portsAsString = properties.getProperty("PGPORT", "25267").split(",");
                    int[] ports = new int[portsAsString.length];
                    for(int i = 0; i < portsAsString.length; i++)
                    {
                        ports[i] = Integer.parseInt(portsAsString[i]);
                    }
                    ds.setPortNumbers(ports);
                } else if (entry.getKey().equals("PGDBNAME")) {
                    ds.setDatabaseName(properties.getProperty("PGDBNAME"));
                } else {
                    ds.setProperty(entry.getKey().toString(), entry.getValue().toString());
                }

            }
            if (user != null) {
                ds.setUser(user);
            }
            ds.setPassword(null);
            ds.setReWriteBatchedInserts(true); // add `rewriteBatchedInserts=true` to pg connection string
            ds.setApplicationName("NoSqlBench CockroachDB Driver");

            connection = ds.getConnection();

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        showQuery = activityDef.getParams().getOptionalBoolean("showquery")
                               .orElse(false);
        maxTries = activityDef.getParams().getOptionalInteger("maxtries")
                              .orElse(10);

        bindTimer = ActivityMetrics.timer(activityDef, "bind");
        resultTimer = ActivityMetrics.timer(activityDef, "result");
        resultSuccessTimer = ActivityMetrics.timer(activityDef, "result-success");
        resultSetSizeHisto = ActivityMetrics.histogram(activityDef, "resultset-size");
        triesHisto = ActivityMetrics.histogram(activityDef, "tries");
    }

    @Override
    public void shutdownActivity() {
        logger.debug("shutting down activity: " + this.activityDef.getAlias());
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }
    }

    OpSequence<ReadyCockroachStatement> initOpSequencer() {
        SequencerType sequencerType = SequencerType.valueOf(
                activityDef.getParams().getOptionalString("seq").orElse("bucket")
        );
        SequencePlanner<ReadyCockroachStatement> sequencer = new SequencePlanner<>(sequencerType);

        StmtsDocList stmtsDocList = StatementsLoader.loadPath(logger, yamlLoc, new StrInterpolator(activityDef),
                "activities");

        String tagfilter = activityDef.getParams().getOptionalString("tags").orElse("");

        TagFilter tagFilter = new TagFilter(tagfilter);
        stmtsDocList.getStmts().stream().map(tagFilter::matchesTaggedResult).forEach(r -> logger.info(r.getLog()));

        List<OpTemplate> stmts = stmtsDocList.getStmts(tagfilter);
        if (stmts.isEmpty()) {
            logger.error("No statements found for this activity");
        } else {
            for (OpTemplate stmt : stmts) {
                ParsedStmt parsed = stmt.getParsed().orError();
                String statement = parsed.getPositionalStatement(Function.identity());
                Objects.requireNonNull(statement);

                sequencer.addOp(new ReadyCockroachStatement(stmt), stmt.getParamOrDefault("ratio",1));
            }
        }

        return sequencer.resolve();
    }

    protected Connection getConnection() {
        return connection;
    }

    protected OpSequence<ReadyCockroachStatement> getOpSequencer() {
        return opSequence;
    }

    protected boolean isShowQuery() {
        return showQuery;
    }

    protected int getMaxTries() {
        return maxTries;
    }
}
