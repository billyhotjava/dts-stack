package com.yuzhi.dts.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScreenAiGenerationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void generate_tabScenarioPrompt_containsTabSwitcherAndRules() {
        ScreenAiGenerationService service = new ScreenAiGenerationService(objectMapper);
        ObjectNode result = service.generate("制造运营看板，支持tab切换场景", 1920, 1080);
        JsonNode screenSpec = result.path("screenSpec");
        ArrayNode components = (ArrayNode) screenSpec.path("components");
        ArrayNode variables = (ArrayNode) screenSpec.path("globalVariables");

        assertThat(hasVariableKey(variables, "tabKey")).isTrue();
        List<ObjectNode> componentList = objectNodes(components);
        assertThat(componentList.stream().anyMatch(item -> "tab-switcher".equals(item.path("type").asText()))).isTrue();

        long visibilityBoundCount = componentList.stream()
                .filter(item -> item.path("config").path("visibilityVariableKey").asText("").equals("tabKey"))
                .count();
        assertThat(visibilityBoundCount).isGreaterThan(0L);
    }

    @Test
    void revise_tabScenarioInstruction_addsTabSwitcherAndVisibilityRules() {
        ScreenAiGenerationService service = new ScreenAiGenerationService(objectMapper);
        ObjectNode screenSpec = baseScreenSpec();
        ArrayNode components = (ArrayNode) screenSpec.path("components");
        components.add(component("c-line", "line-chart"));
        components.add(component("c-bar", "bar-chart"));
        components.add(component("c-table", "table"));

        ObjectNode result = service.revise("请加一个tab切换场景", screenSpec);
        JsonNode revised = result.path("screenSpec");

        assertThat(result.path("actions").toString()).contains("已生成 Tab 场景切换");
        assertThat(hasVariableKey((ArrayNode) revised.path("globalVariables"), "tabKey")).isTrue();

        List<ObjectNode> revisedComponents = objectNodes((ArrayNode) revised.path("components"));
        assertThat(revisedComponents.stream().anyMatch(item -> "tab-switcher".equals(item.path("type").asText())))
                .isTrue();

        List<ObjectNode> targets = revisedComponents.stream()
                .filter(item -> {
                    String type = item.path("type").asText();
                    return "line-chart".equals(type) || "bar-chart".equals(type) || "table".equals(type);
                })
                .toList();
        assertThat(targets).hasSize(3);
        for (ObjectNode target : targets) {
            JsonNode config = target.path("config");
            assertThat(config.path("visibilityRuleEnabled").asBoolean(false)).isTrue();
            assertThat(config.path("visibilityVariableKey").asText("")).isEqualTo("tabKey");
            assertThat(config.path("visibilityMatchMode").asText("")).isEqualTo("equals");
            assertThat(config.path("visibilityMatchValues").isArray()).isTrue();
            assertThat(config.path("visibilityMatchValues").size()).isGreaterThan(0);
        }
    }

    @Test
    void revise_tabScenarioInstruction_updatesExistingTabSwitcherWithoutDuplication() {
        ScreenAiGenerationService service = new ScreenAiGenerationService(objectMapper);
        ObjectNode screenSpec = baseScreenSpec();
        ArrayNode variables = (ArrayNode) screenSpec.path("globalVariables");
        variables.add(objectMapper.createObjectNode()
                .put("key", "tabKey")
                .put("label", "旧场景")
                .put("type", "string")
                .put("defaultValue", "overview"));

        ArrayNode components = (ArrayNode) screenSpec.path("components");
        ObjectNode existingSwitcher = component("existing-tab", "tab-switcher");
        ObjectNode existingConfig = existingSwitcher.with("config");
        existingConfig.put("label", "旧切换");
        existingConfig.put("variableKey", "legacyKey");
        components.add(existingSwitcher);
        components.add(component("c-map", "map-chart"));
        components.add(component("c-radar", "radar-chart"));

        ObjectNode result = service.revise("请做tab切换，并按区域分场景展示", screenSpec);
        JsonNode revised = result.path("screenSpec");

        List<ObjectNode> revisedComponents = objectNodes((ArrayNode) revised.path("components"));
        long tabCount = revisedComponents.stream().filter(item -> "tab-switcher".equals(item.path("type").asText())).count();
        assertThat(tabCount).isEqualTo(1L);
        ObjectNode tabSwitcher = revisedComponents.stream()
                .filter(item -> "tab-switcher".equals(item.path("type").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(tabSwitcher.path("config").path("variableKey").asText("")).isEqualTo("tabKey");
        assertThat(tabSwitcher.path("config").path("defaultValue").asText("")).isNotBlank();
        assertThat(tabSwitcher.path("config").path("options").isArray()).isTrue();
        assertThat(tabSwitcher.path("config").path("options").size()).isGreaterThanOrEqualTo(3);

        List<String> optionValues = new ArrayList<>();
        for (JsonNode item : tabSwitcher.path("config").path("options")) {
            optionValues.add(item.path("value").asText(""));
        }
        assertThat(optionValues).contains("region");
        assertThat(optionValues).contains("overview");

        ArrayNode revisedVariables = (ArrayNode) revised.path("globalVariables");
        long tabVariableCount = objectNodes(revisedVariables).stream()
                .filter(item -> "tabKey".equals(item.path("key").asText()))
                .count();
        assertThat(tabVariableCount).isEqualTo(1L);
        assertThat(result.path("actions").toString()).contains("已生成 Tab 场景切换");
    }

    @Test
    void revise_removeTabScenarioInstruction_cleansTabSwitcherAndVisibilityRules() {
        ScreenAiGenerationService service = new ScreenAiGenerationService(objectMapper);
        ObjectNode screenSpec = baseScreenSpec();
        ArrayNode variables = (ArrayNode) screenSpec.path("globalVariables");
        variables.add(objectMapper.createObjectNode()
                .put("key", "tabKey")
                .put("label", "场景切换")
                .put("type", "string")
                .put("defaultValue", "overview"));

        ArrayNode components = (ArrayNode) screenSpec.path("components");
        ObjectNode tabSwitcher = component("tab-1", "tab-switcher");
        tabSwitcher.with("config").put("variableKey", "tabKey");
        components.add(tabSwitcher);

        ObjectNode line = component("line-1", "line-chart");
        ObjectNode lineConfig = line.with("config");
        lineConfig.put("visibilityRuleEnabled", true);
        lineConfig.put("visibilityVariableKey", "tabKey");
        lineConfig.put("visibilityMatchMode", "equals");
        lineConfig.set("visibilityMatchValues", objectMapper.createArrayNode().add("overview"));
        components.add(line);

        ObjectNode result = service.revise("请移除tab切换", screenSpec);
        JsonNode revised = result.path("screenSpec");
        List<ObjectNode> revisedComponents = objectNodes((ArrayNode) revised.path("components"));

        assertThat(revisedComponents.stream().noneMatch(item -> "tab-switcher".equals(item.path("type").asText()))).isTrue();
        ObjectNode revisedLine = revisedComponents.stream()
                .filter(item -> "line-chart".equals(item.path("type").asText()))
                .findFirst()
                .orElseThrow();
        JsonNode lineCfg = revisedLine.path("config");
        assertThat(lineCfg.has("visibilityRuleEnabled")).isFalse();
        assertThat(lineCfg.has("visibilityVariableKey")).isFalse();
        assertThat(hasVariableKey((ArrayNode) revised.path("globalVariables"), "tabKey")).isFalse();
        assertThat(result.path("actions").toString()).contains("已移除 Tab 场景切换");
    }

    @Test
    void revise_removeTabScenarioInstruction_cleansCustomTabVariableAndLegacyTabKey() {
        ScreenAiGenerationService service = new ScreenAiGenerationService(objectMapper);
        ObjectNode screenSpec = baseScreenSpec();
        ArrayNode variables = (ArrayNode) screenSpec.path("globalVariables");
        variables.add(objectMapper.createObjectNode()
                .put("key", "sceneKey")
                .put("label", "自定义场景")
                .put("type", "string")
                .put("defaultValue", "overview"));
        variables.add(objectMapper.createObjectNode()
                .put("key", "tabKey")
                .put("label", "历史场景")
                .put("type", "string")
                .put("defaultValue", "detail"));

        ArrayNode components = (ArrayNode) screenSpec.path("components");
        ObjectNode tabSwitcher = component("tab-custom", "tab-switcher");
        tabSwitcher.with("config").put("variableKey", "sceneKey");
        components.add(tabSwitcher);

        ObjectNode chartByScene = component("line-scene", "line-chart");
        ObjectNode sceneConfig = chartByScene.with("config");
        sceneConfig.put("visibilityRuleEnabled", true);
        sceneConfig.put("visibilityVariableKey", "sceneKey");
        sceneConfig.put("visibilityMatchMode", "equals");
        sceneConfig.set("visibilityMatchValues", objectMapper.createArrayNode().add("overview"));
        components.add(chartByScene);

        ObjectNode chartByLegacy = component("bar-legacy", "bar-chart");
        ObjectNode legacyConfig = chartByLegacy.with("config");
        legacyConfig.put("visibilityRuleEnabled", true);
        legacyConfig.put("visibilityVariableKey", "tabKey");
        legacyConfig.put("visibilityMatchMode", "equals");
        legacyConfig.set("visibilityMatchValues", objectMapper.createArrayNode().add("detail"));
        components.add(chartByLegacy);

        ObjectNode result = service.revise("取消场景切换", screenSpec);
        JsonNode revised = result.path("screenSpec");
        List<ObjectNode> revisedComponents = objectNodes((ArrayNode) revised.path("components"));

        assertThat(revisedComponents.stream().noneMatch(item -> "tab-switcher".equals(item.path("type").asText()))).isTrue();
        for (ObjectNode component : revisedComponents) {
            JsonNode cfg = component.path("config");
            assertThat(cfg.has("visibilityRuleEnabled")).isFalse();
            assertThat(cfg.has("visibilityVariableKey")).isFalse();
            assertThat(cfg.has("visibilityMatchMode")).isFalse();
            assertThat(cfg.has("visibilityMatchValues")).isFalse();
        }
        ArrayNode revisedVars = (ArrayNode) revised.path("globalVariables");
        assertThat(hasVariableKey(revisedVars, "sceneKey")).isFalse();
        assertThat(hasVariableKey(revisedVars, "tabKey")).isFalse();
        assertThat(result.path("actions").toString()).contains("已移除 Tab 场景切换");
    }

    @Test
    void revise_suggestMode_marksAsNotApplied() {
        ScreenAiGenerationService service = new ScreenAiGenerationService(objectMapper);
        ObjectNode screenSpec = baseScreenSpec();
        ((ArrayNode) screenSpec.path("components")).add(component("c-line", "line-chart"));

        ObjectNode result = service.revise("改成柱状图并放大字体", screenSpec, List.of("上下文"), false);

        assertThat(result.path("applyMode").asText("")).isEqualTo("suggest");
        assertThat(result.path("applied").asBoolean(true)).isFalse();
        assertThat(result.path("contextCount").asInt(0)).isEqualTo(1);
        assertThat(result.path("usedContextCount").asInt(0)).isEqualTo(1);
        assertThat(result.path("quality").path("warnings").toString()).contains("建议模式");
    }

    @Test
    void revise_contextNormalization_reportsUsedContextCount() {
        ScreenAiGenerationService service = new ScreenAiGenerationService(objectMapper);
        ObjectNode screenSpec = baseScreenSpec();
        List<String> context = List.of(
                "   产线A 昨日总产量   ",
                "产线A 昨日总产量",
                "",
                "设备告警按等级分布");

        ObjectNode result = service.revise("放大字体", screenSpec, context, true);

        assertThat(result.path("contextCount").asInt(0)).isEqualTo(4);
        assertThat(result.path("usedContextCount").asInt(0)).isEqualTo(2);
    }

    private ObjectNode baseScreenSpec() {
        ObjectNode screenSpec = objectMapper.createObjectNode();
        screenSpec.put("name", "AI 草稿");
        screenSpec.put("width", 1920);
        screenSpec.put("height", 1080);
        screenSpec.put("backgroundColor", "#0d1b2a");
        screenSpec.set("components", objectMapper.createArrayNode());
        screenSpec.set("globalVariables", objectMapper.createArrayNode());
        return screenSpec;
    }

    private ObjectNode component(String id, String type) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", id);
        node.put("type", type);
        node.put("name", id);
        node.put("x", 40);
        node.put("y", 120);
        node.put("width", 320);
        node.put("height", 220);
        node.put("zIndex", 10);
        node.put("locked", false);
        node.put("visible", true);
        node.set("config", objectMapper.createObjectNode());
        return node;
    }

    private boolean hasVariableKey(ArrayNode vars, String key) {
        for (JsonNode item : vars) {
            if (item != null && item.isObject() && key.equals(item.path("key").asText())) {
                return true;
            }
        }
        return false;
    }

    private List<ObjectNode> objectNodes(ArrayNode nodes) {
        List<ObjectNode> out = new ArrayList<>();
        for (JsonNode item : nodes) {
            if (item != null && item.isObject()) {
                out.add((ObjectNode) item);
            }
        }
        return out;
    }
}
