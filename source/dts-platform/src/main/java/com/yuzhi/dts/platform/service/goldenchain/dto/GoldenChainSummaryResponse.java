package com.yuzhi.dts.platform.service.goldenchain.dto;

import com.yuzhi.dts.platform.domain.goldenchain.GoldenChainSourceKind;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageStatus;

public record GoldenChainSummaryResponse(
    String chainKey,
    String displayName,
    GoldenChainSourceKind sourceKind,
    GoldenChainStage currentStage,
    String currentStageLabel,
    GoldenChainStageStatus status,
    String owner
) {}
