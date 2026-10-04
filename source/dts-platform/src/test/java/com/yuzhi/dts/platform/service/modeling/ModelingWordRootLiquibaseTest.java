package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModelingWordRootLiquibaseTest {

    @Test
    void createsTheIndependentWordRootLedgerAndRegistersItInMaster() throws Exception {
        String migration = read("/config/liquibase/changelog/20260822_02_modeling_word_root.xml");
        String master = read("/config/liquibase/master.xml");

        assertThat(migration)
            .contains("tableName=\"modeling_word_root\"")
            .contains("name=\"code\"")
            .contains("name=\"name_cn\"")
            .contains("name=\"name_en\"")
            .contains("name=\"abbreviation\"")
            .contains("constraintName=\"uk_modeling_word_root_code\"")
            .contains("<rollback>")
            .contains("dropTable tableName=\"modeling_word_root\"");
        assertThat(master).contains(
            "<include file=\"config/liquibase/changelog/20260822_02_modeling_word_root.xml\" relativeToChangelogFile=\"false\"/>"
        );
    }

    @Test
    void registersWordRootAuditActions() throws Exception {
        String catalog = read("/config/audit-action-catalog.json");
        assertThat(catalog).contains("MODELING_WORD_ROOT_LIST", "MODELING_WORD_ROOT_EDIT");
    }

    private String read(String resource) throws Exception {
        try (var input = getClass().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
