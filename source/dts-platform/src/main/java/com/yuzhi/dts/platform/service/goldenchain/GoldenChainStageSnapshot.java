package com.yuzhi.dts.platform.service.goldenchain;

import java.util.Objects;

public record GoldenChainStageSnapshot(
    GoldenChainStage stage,
    GoldenChainStageStatus status,
    String owner,
    String evidenceRef,
    GoldenChainBlockerCode blockerCode,
    String blockerReason
) {
    public GoldenChainStageSnapshot {
        Objects.requireNonNull(stage, "stage must not be null");
        Objects.requireNonNull(status, "status must not be null");
        owner = normalize(owner);
        evidenceRef = normalize(evidenceRef);
        blockerReason = normalize(blockerReason);

        if (!hasText(owner)) {
            throw new IllegalArgumentException("阶段快照必须包含负责人");
        }
        if (status == GoldenChainStageStatus.READY && !hasText(evidenceRef)) {
            throw new IllegalArgumentException("就绪状态必须包含证据引用");
        }
        if (status == GoldenChainStageStatus.BLOCKED) {
            validateBlockedSnapshot(stage, blockerCode, blockerReason);
        } else if (blockerCode != null || hasText(blockerReason)) {
            throw new IllegalArgumentException("非阻断状态不能包含阻断信息");
        }
    }

    public static GoldenChainStageSnapshot pending(GoldenChainStage stage, String owner) {
        return new GoldenChainStageSnapshot(stage, GoldenChainStageStatus.PENDING, owner, null, null, null);
    }

    public static GoldenChainStageSnapshot ready(GoldenChainStage stage, String owner, String evidenceRef) {
        return new GoldenChainStageSnapshot(stage, GoldenChainStageStatus.READY, owner, evidenceRef, null, null);
    }

    public static GoldenChainStageSnapshot blocked(
        GoldenChainStage stage,
        String owner,
        GoldenChainBlockerCode blockerCode,
        String blockerReason
    ) {
        return new GoldenChainStageSnapshot(stage, GoldenChainStageStatus.BLOCKED, owner, null, blockerCode, blockerReason);
    }

    private static void validateBlockedSnapshot(
        GoldenChainStage stage,
        GoldenChainBlockerCode blockerCode,
        String blockerReason
    ) {
        if (blockerCode == null) {
            throw new IllegalArgumentException("阻断状态必须包含阻断码");
        }
        if (!hasText(blockerReason)) {
            throw new IllegalArgumentException("阻断状态必须包含阻断原因");
        }
        if (blockerCode.blockedStage() != stage) {
            throw new IllegalArgumentException("阻断码与阶段不匹配");
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
