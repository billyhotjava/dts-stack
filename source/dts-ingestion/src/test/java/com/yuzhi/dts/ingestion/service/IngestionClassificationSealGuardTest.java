package com.yuzhi.dts.ingestion.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import org.junit.jupiter.api.Test;

class IngestionClassificationSealGuardTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IngestionClassificationSealGuard guard = new IngestionClassificationSealGuard();

    @Test
    void productionWriteRequiresASeal() {
        assertThatThrownBy(() -> guard.requireProductionSeal(new IngestionTask()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("CLASSIFICATION_SEAL_REQUIRED");
    }

    @Test
    void unknownLevelFailsClosed() {
        IngestionTask task = taskWithValidSeal();
        ((ObjectNode) task.getClassificationSeal()).put("effectiveLevel", "UNKNOWN");

        assertThatThrownBy(() -> guard.requireProductionSeal(task))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("CLASSIFICATION_SEAL_INVALID");
    }

    @Test
    void malformedEvidenceCannotReachProductionWrite() {
        IngestionTask task = taskWithValidSeal();
        ((ObjectNode) task.getClassificationSeal()).put("checksum", "short");

        assertThatThrownBy(() -> guard.requireProductionSeal(task))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("checksum");
    }

    @Test
    void completeImmutableSealAllowsProductionWrite() {
        assertThatCode(() -> guard.requireProductionSeal(taskWithValidSeal())).doesNotThrowAnyException();
    }

    @Test
    void jdbcSealMustCoverTheHighestDeclaredField() {
        IngestionTask task = taskWithValidSeal();
        ((ObjectNode) task.getClassificationSeal()).put("effectiveLevel", "INTERNAL");
        ObjectNode fields = objectMapper.createObjectNode();
        fields.put("identity_no", "SECRET");
        task.setFieldClassifications(fields);

        assertThatThrownBy(() -> guard.requireProductionSeal(task))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("CLASSIFICATION_SEAL_STALE");
    }

    @Test
    void fileSealRequiresEveryFieldToRespectTheFileFloor() {
        IngestionTask task = taskWithValidSeal();
        ((ObjectNode) task.getClassificationSeal()).put("subjectType", "FILE");
        ObjectNode fields = objectMapper.createObjectNode();
        fields.put("identity_no", "INTERNAL");
        fields.put("name", "SECRET");
        task.setFieldClassifications(fields);

        assertThatThrownBy(() -> guard.requireProductionSeal(task))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("CLASSIFICATION_DOWNGRADE_FORBIDDEN");
    }

    @Test
    void fileSealCannotReachProductionWithoutFieldEvidence() {
        IngestionTask task = taskWithValidSeal();
        ((ObjectNode) task.getClassificationSeal()).put("subjectType", "FILE");

        assertThatThrownBy(() -> guard.requireProductionSeal(task))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("CLASSIFICATION_FIELD_SEAL_REQUIRED");
    }

    @Test
    void fileSealAllowsInheritedFloorAndExplicitRaises() {
        IngestionTask task = taskWithValidSeal();
        ((ObjectNode) task.getClassificationSeal()).put("fileFloor", "SECRET");
        ObjectNode fields = objectMapper.createObjectNode();
        fields.put("name", "SECRET");
        fields.put("identity_no", "CONFIDENTIAL");
        task.setFieldClassifications(fields);
        ((ObjectNode) task.getClassificationSeal()).put("effectiveLevel", "CONFIDENTIAL");

        assertThatCode(() -> guard.requireProductionSeal(task)).doesNotThrowAnyException();
    }

    private IngestionTask taskWithValidSeal() {
        ObjectNode seal = objectMapper.createObjectNode();
        seal.put("sealId", "seal-sprint-72");
        seal.put("subjectType", "ASSET");
        seal.put("subjectKey", "dataset:sprint72");
        seal.put("effectiveLevel", "SECRET");
        seal.put("snapshotVersion", 1);
        seal.put("checksum", "0123456789abcdef0123456789abcdef");
        seal.put("sealedAt", "2026-07-26T00:00:00Z");
        IngestionTask task = new IngestionTask();
        task.setClassificationSeal(seal);
        return task;
    }
}
