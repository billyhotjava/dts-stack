package com.yuzhi.dts.platform.service.modeling.imports.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ModelSpecImportReconciliationUndoLiquibaseTest {

    @Test
    void extendsTheExistingLedgerWithoutCreatingAParallelHistoryTable() throws Exception {
        String xml = Files.readString(
            Path.of("src/main/resources/config/liquibase/changelog/20260802_04_model_spec_import_reconciliation_undo.xml")
        );

        assertThat(xml).contains("operation_type", "target_attempt_id", "accepted_external_checksum");
        assertThat(xml).contains("merge_checkpoint_json", "pre_attempt_pins_json");
        assertThat(xml).contains("APPLY", "FORWARD_UNDO");
        assertThat(xml).contains("ROLLBACK_BLOCKED_MODEL_SPEC_IMPORT_RECONCILIATION_UNDO_FORWARD_ONLY");
        assertThat(xml).doesNotContain("createTable tableName=\"modeling_model_spec_import_undo");
        assertThat(xml).doesNotContain("createTable tableName=\"modeling_model_spec_import_checkpoint");
    }
}
