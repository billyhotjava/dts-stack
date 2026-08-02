package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SqlTemplateRendererTest {

    private final SqlTemplateRenderer renderer = new SqlTemplateRenderer();

    @Test
    void appliesAndEscapesSchemaDefaultWhenRequestOmitsOptionalParameter() {
        String rendered = renderer.render(
            "SELECT * FROM {{table}} WHERE {{column}} RLIKE '{{pattern}}'",
            Map.of("table", "default.ods_budget", "column", "biz_date"),
            List.of(
                Map.of("name", "table", "type", "table_select", "required", true),
                Map.of("name", "column", "type", "column_select", "required", true),
                Map.of("name", "pattern", "type", "text", "required", false, "default", "^\\d{4}-O'Reilly$")
            )
        );

        assertThat(rendered).isEqualTo(
            "SELECT * FROM default.ods_budget WHERE biz_date RLIKE '^\\d{4}-O''Reilly$'"
        );
    }

    @Test
    void explicitParameterOverridesSchemaDefault() {
        String rendered = renderer.render(
            "SELECT '{{pattern}}'",
            Map.of("pattern", "^[0-9]+$"),
            List.of(Map.of("name", "pattern", "type", "text", "required", false, "default", "ignored"))
        );

        assertThat(rendered).isEqualTo("SELECT '^[0-9]+$'");
    }
}
