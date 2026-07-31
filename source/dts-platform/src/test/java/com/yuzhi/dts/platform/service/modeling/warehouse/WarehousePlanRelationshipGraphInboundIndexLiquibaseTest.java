package com.yuzhi.dts.platform.service.modeling.warehouse;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WarehousePlanRelationshipGraphInboundIndexLiquibaseTest {

    private static final String CHANGELOG =
        "20260731_01_relationship_graph_dependency_index.xml";

    @Test
    void registersConcurrentJsonbPathIndexesWithOwnedRollback()
        throws Exception {
        String master = read("/config/liquibase/master.xml");
        String migration = read(
            "/config/liquibase/changelog/" + CHANGELOG
        );

        assertThat(master).contains(
            "config/liquibase/changelog/" + CHANGELOG
        );
        assertThat(
            changeSet(
                migration,
                "20260731-01-relationship-graph-index-prerequisites"
            )
        )
            .contains(
                "onFail=\"HALT\"",
                "<tableExists tableName=\"modeling_model_spec\"/>",
                "<columnExists tableName=\"modeling_model_spec\" columnName=\"depends_on\"/>",
                "<columnExists tableName=\"modeling_model_spec\" columnName=\"dimension_refs\"/>"
            );
        assertOwnedConcurrentCreate(
            migration,
            "depends-on",
            "idx_modeling_model_spec_depends_on_gin",
            "depends_on"
        );
        assertOwnedConcurrentCreate(
            migration,
            "dimension-refs",
            "idx_modeling_model_spec_dimension_refs_gin",
            "dimension_refs"
        );
    }

    @Test
    void dropsInvalidOrNotReadySameNameIndexesBeforeRetrying()
        throws Exception {
        String migration = read(
            "/config/liquibase/changelog/" + CHANGELOG
        );

        assertInvalidCleanup(
            migration,
            "depends-on",
            "idx_modeling_model_spec_depends_on_gin"
        );
        assertInvalidCleanup(
            migration,
            "dimension-refs",
            "idx_modeling_model_spec_dimension_refs_gin"
        );
    }

    @Test
    void acceptsValidEquivalentPrebuiltIndexesWithoutOwningThem()
        throws Exception {
        String migration = read(
            "/config/liquibase/changelog/" + CHANGELOG
        );

        assertEquivalentMarker(
            migration,
            "depends-on",
            "depends_on"
        );
        assertEquivalentMarker(
            migration,
            "dimension-refs",
            "dimension_refs"
        );
    }

    @Test
    void haltsForValidSameNameIndexesWithMismatchedDefinitions()
        throws Exception {
        String migration = read(
            "/config/liquibase/changelog/" + CHANGELOG
        );

        assertMismatchGuard(
            migration,
            "depends-on",
            "RELATIONSHIP_GRAPH_DEPENDS_ON_GIN_INDEX_MISMATCH"
        );
        assertMismatchGuard(
            migration,
            "dimension-refs",
            "RELATIONSHIP_GRAPH_DIMENSION_REFS_GIN_INDEX_MISMATCH"
        );
    }

    private static void assertOwnedConcurrentCreate(
        String migration,
        String key,
        String indexName,
        String columnName
    ) {
        assertThat(
            changeSet(
                migration,
                "20260731-01-relationship-graph-" + key + "-gin"
            )
        )
            .contains(
                "runAlways=\"true\"",
                "runInTransaction=\"false\"",
                "onFail=\"CONTINUE\"",
                "<not><indexExists indexName=\"" +
                indexName +
                "\"/></not>",
                "CREATE INDEX CONCURRENTLY " + indexName,
                "USING gin (" + columnName + " jsonb_path_ops)",
                "DROP INDEX CONCURRENTLY IF EXISTS " + indexName
            )
            .doesNotContain("IF NOT EXISTS " + indexName);
    }

    private static void assertInvalidCleanup(
        String migration,
        String key,
        String indexName
    ) {
        assertThat(
            changeSet(
                migration,
                "20260731-01-clean-invalid-" + key + "-gin"
            )
        )
            .contains(
                "runAlways=\"true\"",
                "runInTransaction=\"false\"",
                "NOT index_state.indisvalid OR NOT index_state.indisready",
                "DROP INDEX CONCURRENTLY IF EXISTS " + indexName
            );
    }

    private static void assertEquivalentMarker(
        String migration,
        String key,
        String columnName
    ) {
        assertThat(
            changeSet(
                migration,
                "20260731-01-accept-equivalent-" + key + "-gin"
            )
        )
            .contains(
                "onFail=\"CONTINUE\"",
                "expectedResult=\"1\"",
                "index_state.indisvalid",
                "index_state.indisready",
                "pg_catalog.pg_opclass operator_class",
                "operator_class.opcname = 'jsonb_path_ops'",
                "pg_get_indexdef(index_relation.oid, 1, true)",
                "access_method.amname = 'gin'",
                ") = '" + columnName + "'",
                "<sql>SELECT 1</sql>"
            );
    }

    private static void assertMismatchGuard(
        String migration,
        String key,
        String errorCode
    ) {
        assertThat(
            changeSet(
                migration,
                "20260731-01-guard-mismatched-" + key + "-gin"
            )
        )
            .contains(
                "runAlways=\"true\"",
                "onFail=\"HALT\"",
                "onFailMessage=\"" + errorCode,
                "expectedResult=\"0\"",
                "index_state.indisvalid",
                "index_state.indisready",
                "pg_catalog.pg_opclass operator_class",
                "operator_class.opcname = 'jsonb_path_ops'",
                "pg_get_indexdef(index_relation.oid, 1, true)",
                "NOT ("
            );
    }

    private static String changeSet(String xml, String id) {
        int idOffset = xml.indexOf("id=\"" + id + "\"");
        assertThat(idOffset).isNotNegative();
        int start = xml.lastIndexOf("<changeSet", idOffset);
        int end = xml.indexOf("</changeSet>", idOffset);
        assertThat(start).isNotNegative();
        assertThat(end).isGreaterThan(idOffset);
        return xml.substring(start, end + "</changeSet>".length());
    }

    private static String read(String path) throws Exception {
        try (
            var input =
                WarehousePlanRelationshipGraphInboundIndexLiquibaseTest.class.getResourceAsStream(
                    path
                )
        ) {
            assertThat(input).isNotNull();
            return new String(
                input.readAllBytes(),
                StandardCharsets.UTF_8
            );
        }
    }
}
