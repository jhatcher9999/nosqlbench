package io.nosqlbench.driver.cockroachdb;

import java.sql.*;
import java.util.concurrent.TimeUnit;

import io.nosqlbench.virtdata.library.realer.todo.userinfo.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Timer;
import io.nosqlbench.engine.api.activityapi.core.SyncAction;
import io.nosqlbench.engine.api.activityapi.planning.OpSequence;

public class CockroachAction implements SyncAction {

    private final static Logger logger = LoggerFactory.getLogger(CockroachAction.class);

    private final CockroachActivity cockroachActivity;
    private final int slot;

    private OpSequence<ReadyCockroachStatement> sequencer;

    public CockroachAction(CockroachActivity activity, int slot) {
        this.cockroachActivity = activity;
        this.slot = slot;
    }

    @Override
    public void init() {
        this.sequencer = cockroachActivity.getOpSequencer();
    }

    @Override
    public int runCycle(long cycleValue) {

        ReadyCockroachStatement readyCockroachStatement;
        String boundString;
        try (Timer.Context bindTime = cockroachActivity.bindTimer.time()) {
            readyCockroachStatement = sequencer.get(cycleValue);
            boundString = readyCockroachStatement.bind(cycleValue);

            // Maybe show the query in log/console - only for diagnostic use
            if (cockroachActivity.isShowQuery()) {
                logger.info("Query(cycle={}):\n{}", cycleValue, boundString);
            }
        }

        long nanoStartTime = System.nanoTime();
        for (int i = 1; i <= cockroachActivity.getMaxTries(); i++) {
            cockroachActivity.triesHisto.update(i);

            try (Timer.Context resultTime = cockroachActivity.resultTimer.time()) {

                Connection connection = cockroachActivity.getConnection();

                //TODO: change this to use a prepared statement

                Statement statement = connection.createStatement();
                if (boundString.toUpperCase().startsWith("SELECT")) { //statement with results
                    statement.executeQuery(boundString);
                } else { //statement with no results (i.e., a mutation)
                    statement.execute(boundString);
                }

                long resultNanos = System.nanoTime() - nanoStartTime;

                return 1;

            } catch (Exception e) {
                logger.error("Failed to runCommand {} on cycle {}, tries {}", boundString, cycleValue, i, e);
            }
        }

        throw new RuntimeException(String.format("Exhausted max tries (%s) on cycle %s", cockroachActivity.getMaxTries(), cycleValue));
    }
}
