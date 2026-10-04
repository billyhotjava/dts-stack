package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class QualityAuditCatalogContractTest {

    private static final List<String> REQUIRED_CODES = List.of(
        "GOV_RULE_VERSION_LIST",
        "GOV_RULE_VERSION_VIEW",
        "GOV_RULE_VERSION_STATUS",
        "GOV_RULE_DRY_RUN",
        "GOV_ISSUE_METRICS",
        "GOV_AUTO_TRIGGER",
        "GOV_OPS_OVERVIEW_VIEW",
        "GOV_OPS_TREND_VIEW",
        "GOV_OPS_RELEASE_GATE_VIEW",
        "GOV_QUALITY_RUN_EXECUTE",
        "GOV_QUALITY_FAILING_ROW_VIEW",
        "GOV_QUALITY_TEMPLATE_LIST",
        "GOV_QUALITY_TEMPLATE_VIEW",
        "GOV_QUALITY_TEMPLATE_CREATE",
        "GOV_QUALITY_TEMPLATE_UPDATE",
        "GOV_QUALITY_TEMPLATE_DELETE",
        "GOV_QUALITY_TEMPLATE_PREVIEW",
        "GOV_QUALITY_TASK_LIST",
        "GOV_QUALITY_TASK_CREATE",
        "GOV_QUALITY_TASK_UPDATE",
        "GOV_QUALITY_TASK_DELETE",
        "GOV_QUALITY_TASK_TOGGLE",
        "GOV_QUALITY_TASK_EXECUTE",
        "GOV_QUALITY_CLEANSING_PREVIEW",
        "GOV_QUALITY_CLEANSING_EXECUTE",
        "GOV_QUALITY_CLEANSING_FUNCTION_LIST",
        "GOV_QUALITY_CLEANSING_FUNCTION_CREATE",
        "GOV_QUALITY_CLEANSING_FUNCTION_UPDATE",
        "GOV_QUALITY_CLEANSING_FUNCTION_DELETE",
        "GOV_QUALITY_SQL_REPAIR_PREVIEW",
        "GOV_QUALITY_SQL_REPAIR_EXECUTE",
        "GOV_QUALITY_REPORT_EXPORT"
    );

    @Test
    void canonicalAndFallbackCatalogsAreIdenticalAndRegisterEveryQualityAction() throws Exception {
        List<Path> catalogs = List.of(
            Path.of("../dts-common/src/main/resources/config/audit-action-catalog.json"),
            Path.of("src/main/docker/dts-common-fallback/src/main/resources/config/audit-action-catalog.json"),
            Path.of("../dts-admin/src/main/docker/dts-common-fallback/src/main/resources/config/audit-action-catalog.json")
        );
        ObjectMapper mapper = new ObjectMapper();
        String canonical = Files.readString(catalogs.getFirst());
        JsonNode canonicalTree = mapper.readTree(canonical);
        Set<String> codes = new TreeSet<>();
        canonicalTree.findValues("code").forEach(node -> codes.add(node.asText()));

        assertThat(codes).containsAll(REQUIRED_CODES);
        assertFlowAction(canonicalTree, "GOV_QUALITY_REPORT_EXPORT");
        assertFlowAction(canonicalTree, "GOV_QUALITY_RUN_EXECUTE");
        assertFlowAction(canonicalTree, "GOV_QUALITY_TASK_EXECUTE");
        for (Path fallback : catalogs.subList(1, catalogs.size())) {
            assertThat(mapper.readTree(Files.readString(fallback))).isEqualTo(canonicalTree);
        }
    }

    private static JsonNode findAction(JsonNode catalog, String code) {
        return catalog.findParents("code")
            .stream()
            .filter(action -> code.equals(action.path("code").asText()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing audit action: " + code));
    }

    private static void assertFlowAction(JsonNode catalog, String code) {
        JsonNode action = findAction(catalog, code);
        assertThat(action.path("supportsFlow").asBoolean()).as(code).isTrue();
        assertThat(action.path("phases").toString()).as(code).isEqualTo("[\"BEGIN\",\"SUCCESS\",\"FAIL\"]");
    }
}
