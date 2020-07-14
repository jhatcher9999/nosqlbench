package io.nosqlbench.driver.cockroachdb;

import io.nosqlbench.engine.api.activityapi.core.Action;
import io.nosqlbench.engine.api.activityapi.core.ActionDispenser;
import io.nosqlbench.engine.api.activityapi.core.ActivityType;
import io.nosqlbench.engine.api.activityimpl.ActivityDef;
import io.nosqlbench.nb.annotations.Service;

@Service(ActivityType.class)
public class CockroachActivityType implements ActivityType<CockroachActivity> {

    @Override
    public String getName() {
        return "cockroachdb";
    }

    @Override
    public CockroachActivity getActivity(ActivityDef activityDef) {
        return new CockroachActivity(activityDef);
    }

    @Override
    public ActionDispenser getActionDispenser(CockroachActivity activity) {
        return new CockroachActionDispenser(activity);
    }

    private static class CockroachActionDispenser implements ActionDispenser {

        private final CockroachActivity activity;

        public CockroachActionDispenser(CockroachActivity activity) {
            this.activity = activity;
        }

        @Override
        public Action getAction(int slot) {
            return new CockroachAction(activity, slot);
        }
    }
}
