package com.yuzhi.dts.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Sprint-104 F10: the menu seed mirrors the implementation-order migration exactly. */
class PortalMenuImplementationOrderSeedContractTest {

    private static final String SEED = "config/data/portal-menu-seed.json";
    private static final String CHANGELOG =
        "config/liquibase/changelog/20260916_03_portal_menu_implementation_order.xml";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void seedRootsFollowImplementationOrderAndKeepScreenManagementAsRoot() throws Exception {
        List<Map<String, Object>> roots = roots();

        assertThat(keys(roots)).containsExactly(
            "workbench",
            "data-architecture",
            "resource",
            "modeling",
            "studio",
            "governance",
            "consumption",
            "screens"
        );
        Map<String, Object> screens = child(roots, "screens");
        assertThat(screens)
            .containsEntry("path", "bi/screens")
            .containsEntry("titleKey", "sys.nav.portal.biScreens")
            .containsEntry("title", "大屏管理")
            .containsEntry("externalLink", "/bi/screens");
        assertThat(screens.get("children")).isNull();
    }

    @Test
    void seedChildrenFollowImplementationOrderWithoutMovingNodes() throws Exception {
        List<Map<String, Object>> roots = roots();
        List<Map<String, Object>> modeling = children(child(roots, "modeling"));

        assertThat(keys(modeling)).containsExactly(
            "standards",
            "dimensional-modeling",
            "data-metrics",
            "modeling-graphs",
            "modeling-home-workspace",
            "modeling-tools"
        );
        assertThat(keys(children(child(modeling, "data-metrics")))).containsExactly(
            "metrics-atomic",
            "metrics-derived",
            "metrics-composite",
            "metrics-modifiers",
            "metrics-periods"
        );
        assertThat(keys(children(child(modeling, "modeling-graphs"))))
            .containsExactly("graphs-models", "graphs-standards", "graphs-metrics");
        assertThat(keys(children(child(roots, "governance"))))
            .containsExactly("assets", "classification", "qualityRules", "qualityReport");
        assertThat(keys(children(child(roots, "consumption")))).containsExactly("services", "bi-apps");
        assertThat(keys(children(child(roots, "data-architecture")))).containsExactly(
            "architecture-business-domains",
            "architecture-processes",
            "architecture-layers",
            "architecture-marts",
            "architecture-subjects"
        );
    }

    @Test
    void migrationSeedHashMatchesSeedFileBytes() throws Exception {
        String xml = new ClassPathResource(CHANGELOG).getContentAsString(StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("seed_hash CONSTANT TEXT := '([0-9a-f]{64})'").matcher(xml);

        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group(1)).isEqualTo(sha256(SEED));
    }

    @Test
    void migrationOnlyReordersAndNeverTargetsScreenManagement() throws Exception {
        String xml = new ClassPathResource(CHANGELOG).getContentAsString(StandardCharsets.UTF_8);
        String forward = xml.substring(0, xml.indexOf("<rollback>")).toLowerCase(java.util.Locale.ROOT);

        assertThat(forward).doesNotContain("insert into portal_menu ");
        assertThat(forward).doesNotContain("delete from");
        assertThat(forward).doesNotContain("set parent_id");
        assertThat(forward).doesNotContain("set metadata");
        assertThat(forward).doesNotContain("portal_menu_visibility (");
        assertThat(forward).doesNotContain("('sys.nav.portal.biscreens'");
    }

    private List<Map<String, Object>> roots() throws Exception {
        try (InputStream input = new ClassPathResource(SEED).getInputStream()) {
            Map<String, Object> seed = objectMapper.readValue(input, new TypeReference<Map<String, Object>>() {});
            return castList(seed.get("portalNavSections"));
        }
    }

    private static Map<String, Object> child(List<Map<String, Object>> nodes, String key) {
        return nodes.stream().filter(node -> key.equals(node.get("key"))).findFirst().orElseThrow();
    }

    private static List<Map<String, Object>> children(Map<String, Object> node) {
        return castList(node.get("children"));
    }

    private static List<String> keys(List<Map<String, Object>> nodes) {
        return nodes.stream().map(node -> String.valueOf(node.get("key"))).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object value) {
        return value == null ? List.of() : (List<Map<String, Object>>) value;
    }

    private static String sha256(String resource) throws Exception {
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
        }
    }
}
