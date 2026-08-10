package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SchemaDriftDetectorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SchemaDriftDetector detector = new SchemaDriftDetector(objectMapper);

    @ParameterizedTest(name = "{0}")
    @MethodSource("impactCases")
    void classifiesSchemaChanges(
        String scenario,
        SchemaDriftDetector.ColumnSnapshot before,
        SchemaDriftDetector.ColumnSnapshot after,
        Set<String> referencedFields,
        String expectedImpact
    ) throws Exception {
        Map<String, SchemaDriftDetector.ColumnSnapshot> beforeMap = before == null
            ? Map.of()
            : Map.of(before.name().toLowerCase(), before);
        List<SchemaDriftDetector.ColumnSnapshot> afterList = after == null ? List.of() : List.of(after);

        SchemaDriftDetector.DriftSummary summary = detector.diff(beforeMap, afterList, referencedFields);

        JsonNode payload = objectMapper.readTree(summary.detailsJson());
        assertThat(payload.path("contractVersion").asInt()).isEqualTo(1);
        assertThat(payload.path("impactLevel").asText()).isEqualTo(expectedImpact);
        assertThat(payload.path("changes")).hasSize(1);
        assertThat(payload.path("changes").get(0).path("impact").asText()).isEqualTo(expectedImpact);
        assertThat(payload.has("added") || payload.has("removed") || payload.has("changed"))
            .as("legacy payload arrays remain readable")
            .isTrue();
    }

    static Stream<Arguments> impactCases() {
        return Stream.of(
            Arguments.of("nullable field added", null, column("note", "varchar(64)", true), Set.of(), "COMPATIBLE"),
            Arguments.of("required field added", null, column("code", "varchar(32)", false), Set.of(), "REVIEW_REQUIRED"),
            Arguments.of("referenced field removed", column("amount", "decimal(18,2)", true), null, Set.of("amount"), "BREAKING"),
            Arguments.of("unreferenced field removed", column("legacy", "varchar(20)", true), null, Set.of(), "COMPATIBLE"),
            Arguments.of("varchar widened", column("code", "varchar(20)", true), column("code", "varchar(64)", true), Set.of(), "COMPATIBLE"),
            Arguments.of("varchar narrowed", column("code", "varchar(64)", true), column("code", "varchar(20)", true), Set.of(), "BREAKING"),
            Arguments.of("decimal widened", column("amount", "decimal(10,2)", true), column("amount", "decimal(18,4)", true), Set.of(), "COMPATIBLE"),
            Arguments.of("decimal narrowed", column("amount", "decimal(18,4)", true), column("amount", "decimal(10,2)", true), Set.of(), "BREAKING"),
            Arguments.of("integer widened", column("quantity", "integer", true), column("quantity", "bigint", true), Set.of(), "COMPATIBLE"),
            Arguments.of("type family changed", column("event_at", "timestamp", true), column("event_at", "varchar(64)", true), Set.of(), "BREAKING"),
            Arguments.of("nullable tightened", column("code", "varchar(32)", true), column("code", "varchar(32)", false), Set.of(), "BREAKING"),
            Arguments.of("nullable relaxed", column("code", "varchar(32)", false), column("code", "varchar(32)", true), Set.of(), "COMPATIBLE"),
            Arguments.of("unknown parameter change", column("shape", "geometry(point)", true), column("shape", "geometry(polygon)", true), Set.of(), "REVIEW_REQUIRED")
        );
    }

    @Test
    void treatsAUniqueSameShapeRemoveAndAddAsPossibleRename() throws Exception {
        SchemaDriftDetector.DriftSummary summary = detector.diff(
            Map.of("old_code", column("old_code", "varchar(32)", false)),
            List.of(column("new_code", "varchar(32)", false)),
            Set.of("old_code")
        );

        JsonNode payload = objectMapper.readTree(summary.detailsJson());
        assertThat(payload.path("impactLevel").asText()).isEqualTo("REVIEW_REQUIRED");
        assertThat(payload.path("changes")).hasSize(1);
        assertThat(payload.path("changes").get(0).path("kind").asText()).isEqualTo("POSSIBLE_RENAME");
    }

    @Test
    void ignoresPreviouslyRemovedColumnsAndEmitsNothingForEqualSnapshots() {
        CatalogColumnSchema active = catalogColumn("id", "bigint", false, "ACTIVE");
        CatalogColumnSchema removed = catalogColumn("legacy", "varchar(20)", true, "REMOVED");

        Map<String, SchemaDriftDetector.ColumnSnapshot> before = detector.snapshotExisting(List.of(active, removed));
        SchemaDriftDetector.DriftSummary summary = detector.diff(
            before,
            List.of(column("id", "BIGINT", false)),
            Set.of("id")
        );

        assertThat(before).containsOnlyKeys("id");
        assertThat(summary).isEqualTo(new SchemaDriftDetector.DriftSummary(0, 0, 0, null));
    }

    private static SchemaDriftDetector.ColumnSnapshot column(String name, String type, Boolean nullable) {
        return new SchemaDriftDetector.ColumnSnapshot(name, type, nullable);
    }

    private static CatalogColumnSchema catalogColumn(String name, String type, Boolean nullable, String status) {
        CatalogColumnSchema column = new CatalogColumnSchema();
        column.setName(name);
        column.setDataType(type);
        column.setNullable(nullable);
        column.setStatus(status);
        return column;
    }
}
