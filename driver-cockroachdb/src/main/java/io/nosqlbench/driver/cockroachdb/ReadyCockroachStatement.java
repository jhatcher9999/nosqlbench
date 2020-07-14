package io.nosqlbench.driver.cockroachdb;

import io.nosqlbench.engine.api.activityconfig.yaml.OpTemplate;
import io.nosqlbench.virtdata.core.bindings.BindingsTemplate;
import io.nosqlbench.virtdata.core.templates.ParsedTemplate;
import io.nosqlbench.virtdata.core.templates.StringBindings;
import io.nosqlbench.virtdata.core.templates.StringBindingsTemplate;

import java.sql.PreparedStatement;

public class ReadyCockroachStatement {

    private final StringBindings bindings;
    private PreparedStatement statement;


    public ReadyCockroachStatement(OpTemplate stmtDef) {

        ParsedTemplate paramTemplate = new ParsedTemplate(stmtDef.getStmt(), stmtDef.getBindings());
        BindingsTemplate paramBindings = new BindingsTemplate(paramTemplate.getBindPoints());
        StringBindingsTemplate template = new StringBindingsTemplate(stmtDef.getStmt(), paramBindings);

        this.bindings = template.resolve();
    }

    public String bind(long value) {
        return bindings.bind(value);
    }
}
