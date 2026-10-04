package com.yuzhi.dts.platform.service.goldenchain.dto;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageStatus;

public record GoldenChainStageResponse(
    GoldenChainStage stage,
    String stageLabel,
    GoldenChainStageStatus status,
    String owner,
    String evidenceRef,
    String failureReason,
    String nextAction
) {}
