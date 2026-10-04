package com.yuzhi.dts.platform.service.governance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.UUID;

final class PinnedQualitySnapshotCodec {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private PinnedQualitySnapshotCodec() {}

    static String encode(List<PinnedQualityBinding> pins) {
        try {
            return JSON.writeValueAsString(new Snapshot(pins));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("模型质量规则快照无法登记", ex);
        }
    }

    static List<PinnedQualityBinding> decode(String contextJson, UUID legacyRuleId) {
        try {
            JsonNode root = JSON.readTree(contextJson);
            JsonNode pinnedRules = root.path("pinnedRules");
            if (pinnedRules.isArray() && !pinnedRules.isEmpty()) {
                return List.copyOf(
                    JSON.convertValue(
                        pinnedRules,
                        JSON.getTypeFactory().constructCollectionType(List.class, PinnedQualityBinding.class)
                    )
                );
            }
            if (legacyRuleId != null && root.hasNonNull("ruleVersionId") && root.hasNonNull("bindingId")) {
                return List.of(
                    new PinnedQualityBinding(
                        legacyRuleId,
                        UUID.fromString(root.get("ruleVersionId").asText()),
                        UUID.fromString(root.get("bindingId").asText())
                    )
                );
            }
            throw new IllegalArgumentException("模型质量工作流缺少固定规则快照");
        } catch (JsonProcessingException | NullPointerException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("模型质量工作流固定规则快照无效", ex);
        }
    }

    private record Snapshot(List<PinnedQualityBinding> pinnedRules) {}
}
