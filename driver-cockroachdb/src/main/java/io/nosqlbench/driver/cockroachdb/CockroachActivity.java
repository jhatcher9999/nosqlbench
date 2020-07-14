package io.nosqlbench.driver.cockroachdb;

import java.util.List;
import java.util.Objects;
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
//    private String connectionString;
//    private String databaseName;

    //Cockroach-specific stuff
    private DataSource ds;
    private Connection connection;


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

        //TODO: set all this stuff up
//        connectionString = activityDef.getParams().getOptionalString("connection")
//                                      .orElseThrow(() -> new IllegalArgumentException("connection is not defined"));
    }

    @Override
    public void initActivity() {
        logger.debug("initializing activity: " + this.activityDef.getAlias());
        onActivityDefUpdate(activityDef);

        opSequence = initOpSequencer();
        setDefaultsFromOpSequence(opSequence);

        //cockroach-specific stuff
        //TODO: get this all from the activity params
        //TODO: put this in some kind of connection cache or move it somewhere so that it only gets instantiated once?
        //TODO: make this handle secure connections (certs, etc.)
        //TODO: change this to a data source that just takes a connection string
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setServerName("localhost");
        ds.setPortNumber(26257);
        ds.setDatabaseName("bank");
        ds.setUser("maxroach");
        ds.setPassword(null);
        ds.setReWriteBatchedInserts(true);
        ds.setApplicationName("BasicExample");
        try {
            connection = ds.getConnection();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
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

//    CockroachClient createCockroachClient(String connectionString) {
//        CodecRegistry codecRegistry = fromRegistries(fromCodecs(new UuidCodec(UuidRepresentation.STANDARD)),
//            CockroachClientSettings.getDefaultCodecRegistry());
//        CockroachClientSettings settings = CockroachClientSettings.builder()
//                                                          .applyConnectionString(new ConnectionString(connectionString))
//                                                          .codecRegistry(codecRegistry)
//                                                          .uuidRepresentation(UuidRepresentation.STANDARD)
//                                                          .build();
//        return CockroachClients.create(settings);
//    }

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
