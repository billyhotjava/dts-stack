package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovQualityWorkflowRun;
import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;

/** Validates immutable model-release rule pins and their idempotent replay scope. */
final class PinnedQualityCommandValidator {

    private PinnedQualityCommandValidator() {}

    static List<PinnedQualityBinding> validateCommand(List<PinnedQualityBinding> pins, int maxRules) {
        if (pins == null || pins.isEmpty() || pins.size() > maxRules) {
            throw new IllegalArgumentException("模型质量验证必须包含 1 到 100 条规则绑定快照");
        }
        boolean invalid = pins
            .stream()
            .anyMatch(pin ->
                pin == null || pin.ruleId() == null || pin.ruleVersionId() == null || pin.bindingId() == null
            );
        if (invalid) {
            throw new IllegalArgumentException("模型质量验证规则绑定快照无效或重复");
        }
        List<PinnedQualityBinding> normalized = List.copyOf(pins);
        long distinctBindings = normalized.stream().map(PinnedQualityBinding::bindingId).distinct().count();
        if (distinctBindings != normalized.size()) {
            throw new IllegalArgumentException("模型质量验证规则绑定快照无效或重复");
        }
        return normalized;
    }

    static void validateBinding(PinnedQualityBinding pin, GovRuleBinding binding, boolean allowHistoricalVersion) {
        String versionStatus = binding.getRuleVersion() == null
            ? ""
            : normalizeStatus(binding.getRuleVersion().getStatus());
        boolean executableStatus = allowHistoricalVersion
            ? Set.of("PUBLISHED", "ARCHIVED").contains(versionStatus)
            : "PUBLISHED".equals(versionStatus);
        if (
            binding.getDatasetId() == null ||
            binding.getRuleVersion() == null ||
            !pin.ruleVersionId().equals(binding.getRuleVersion().getId()) ||
            !executableStatus ||
            binding.getRuleVersion().getRule() == null ||
            !pin.ruleId().equals(binding.getRuleVersion().getRule().getId()) ||
            !Boolean.TRUE.equals(binding.getRuleVersion().getRule().getEnabled())
        ) {
            throw new IllegalStateException("模型质量规则绑定已变化，请刷新候选证据");
        }
    }

    static void requireReplayScope(
        GovQualityWorkflowRun workflow,
        List<PinnedQualityBinding> pins,
        String triggerType,
        String triggerRef,
        UUID retryOfId
    ) {
        boolean mismatch = !triggerType.equals(normalizeStatus(workflow.getTriggerType())) ||
            !triggerRef.equals(workflow.getTriggerRef()) ||
            !Objects.equals(retryOfId, workflow.getRetryOfId());
        if (!mismatch) {
            try {
                mismatch = !pins.equals(PinnedQualitySnapshotCodec.decode(workflow.getContextJson(), workflow.getRuleId()));
            } catch (IllegalArgumentException ex) {
                mismatch = true;
            }
        }
        if (mismatch) {
            throw new IllegalStateException("模型质量工作流幂等标识已用于其他验证");
        }
    }

    static boolean isPinnedModelWorkflow(GovQualityWorkflowRun workflow) {
        return "MODEL_RELEASE".equals(normalizeStatus(workflow.getTriggerType())) ||
            StringUtils.contains(workflow.getContextJson(), "pinnedRules") ||
            StringUtils.contains(workflow.getContextJson(), "ruleVersionId");
    }

    private static String normalizeStatus(String status) {
        return StringUtils.trimToEmpty(status).toUpperCase(Locale.ROOT);
    }
}
