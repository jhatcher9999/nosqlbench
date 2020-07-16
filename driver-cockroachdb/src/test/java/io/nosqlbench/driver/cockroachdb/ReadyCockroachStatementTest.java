package io.nosqlbench.driver.cockroachdb;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import io.nosqlbench.engine.api.activityconfig.yaml.OpTemplate;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nosqlbench.engine.api.activityconfig.ParsedStmt;
import io.nosqlbench.engine.api.activityconfig.StatementsLoader;
import io.nosqlbench.engine.api.activityconfig.yaml.StmtDef;
import io.nosqlbench.engine.api.activityconfig.yaml.StmtsDocList;
import io.nosqlbench.engine.api.activityimpl.ActivityDef;
import io.nosqlbench.engine.api.templating.StrInterpolator;
import io.nosqlbench.virtdata.core.templates.BindPoint;

import static org.assertj.core.api.Assertions.assertThat;

public class ReadyCockroachStatementTest {
    private final static Logger logger = LoggerFactory.getLogger(ReadyCockroachStatementTest.class);

    private ActivityDef activityDef;
    private StmtsDocList stmtsDocList;

    @Before
    public void setup() {
        String[] params = {
                "workload=activities/cockroachdb-basic.yaml",
                "connectionString=jdbc:postgresql://maxroach@localhost:26257/bank?sslmode=disable"
        };
        activityDef = ActivityDef.parseActivityDef(String.join(";", params));
        String yaml_loc = activityDef.getParams().getOptionalString("yaml", "workload").orElse("default");
        stmtsDocList = StatementsLoader.loadPath(logger, yaml_loc, new StrInterpolator(activityDef), "activities");
    }

    @Test
    public void testResolvePhaseRampup() {
        String tagfilter = activityDef.getParams().getOptionalString("tags").orElse("phase:rampup");

        List<OpTemplate> stmts = stmtsDocList.getStmts(tagfilter);
        assertThat(stmts).hasSize(1);
        for (OpTemplate stmt : stmts) {
            ParsedStmt parsed = stmt.getParsed().orError();
            assertThat(parsed.getBindPoints()).hasSize(2);

            BindPoint seqKey = new BindPoint("seq_key", "Mod(1000000L); ToInt()");
            BindPoint seqValue = new BindPoint("seq_value", "Mod(1000000000L); Hash(); ToString() -> String");
            assertThat(parsed.getBindPoints()).containsExactly(seqKey, seqValue);

            String statement = parsed.getPositionalStatement(Function.identity());
            Objects.requireNonNull(statement);

            ReadyCockroachStatement readyCockroachStatement = new ReadyCockroachStatement(stmt);
            String boundStatement = readyCockroachStatement.bind(1L);
            assertThat(boundStatement).isNotNull();
        }
    }

    @Test
    public void testResolvePhaseMainRead() {
        String tagfilter = activityDef.getParams().getOptionalString("tags").orElse("phase:main,name:main-find");

        List<OpTemplate> stmts = stmtsDocList.getStmts(tagfilter);
        assertThat(stmts).hasSize(1);
        for (OpTemplate stmt : stmts) {
            ParsedStmt parsed = stmt.getParsed().orError();
            assertThat(parsed.getBindPoints()).hasSize(2);

            BindPoint rwKey = new BindPoint("rw_key", "Uniform(0,1000000)->long; ToInt()");
            BindPoint rwValue = new BindPoint("rw_value", "Uniform(0,1000000000)->int; Hash(); ToString() -> String");
            assertThat(parsed.getBindPoints()).containsExactly(rwKey, rwValue);

            String statement = parsed.getPositionalStatement(Function.identity());
            Objects.requireNonNull(statement);

            ReadyCockroachStatement readyCockroachStatement = new ReadyCockroachStatement(stmt);
            String boundStatement = readyCockroachStatement.bind(1L);
            assertThat(boundStatement).isNotNull();
        }
    }

    @Test
    public void testResolvePhaseMainWrite() {
        String tagfilter = activityDef.getParams().getOptionalString("tags").orElse("phase:main,name:main-insert");

        List<OpTemplate> stmts = stmtsDocList.getStmts(tagfilter);
        assertThat(stmts).hasSize(1);
        for (OpTemplate stmt : stmts) {
            ParsedStmt parsed = stmt.getParsed().orError();
            assertThat(parsed.getBindPoints()).hasSize(2);

            BindPoint seq_key = new BindPoint("seq_key", "Mod(1000000L); ToInt()");
            BindPoint seq_value = new BindPoint("seq_value", "Mod(1000000000L); Hash(); ToString() -> String");
            assertThat(parsed.getBindPoints()).containsExactly(seq_key, seq_value);

            String statement = parsed.getPositionalStatement(Function.identity());
            Objects.requireNonNull(statement);

            ReadyCockroachStatement readyCockroachStatement = new ReadyCockroachStatement(stmt);
            String boundStatement = readyCockroachStatement.bind(1L);
            assertThat(boundStatement).isNotNull();
        }
    }
}
