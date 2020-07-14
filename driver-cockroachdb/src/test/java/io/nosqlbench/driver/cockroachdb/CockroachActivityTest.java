package io.nosqlbench.driver.cockroachdb;

import org.junit.Before;
import org.junit.Test;

import java.sql.*;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;


import io.nosqlbench.engine.api.activityapi.planning.OpSequence;
import io.nosqlbench.engine.api.activityimpl.ActivityDef;

import static org.assertj.core.api.Assertions.assertThat;

public class CockroachActivityTest {

    private ActivityDef activityDef;

    @Before
    public void setup() {
        //TODO: pass correct values here
        String[] params = {
                "yaml=activities/cockroachdb-basic.yaml",
                "connection=something",
                "database=bank"
        };
        activityDef = ActivityDef.parseActivityDef(String.join(";", params));
    }

    @Test
    public void testInitOpSequencer() {
        CockroachActivity cockroachActivity = new CockroachActivity(activityDef);
        cockroachActivity.initActivity();

        OpSequence<ReadyCockroachStatement> sequence = cockroachActivity.initOpSequencer();
        assertThat(sequence.getOps()).hasSize(3);
    }
}
