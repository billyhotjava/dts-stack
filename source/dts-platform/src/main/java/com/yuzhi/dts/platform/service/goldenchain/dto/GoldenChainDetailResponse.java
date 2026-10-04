package com.yuzhi.dts.platform.service.goldenchain.dto;

import com.yuzhi.dts.platform.domain.goldenchain.GoldenChainSourceKind;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageStatus;
import java.util.List;

public record GoldenChainDetailResponse(
    String chainKey,
    String displayName,
    GoldenChainSourceKind sourceKind,
    GoldenChainStage currentStage,
    String currentStageLabel,
    GoldenChainStageStatus status,
    String owner,
    List<GoldenChainStageResponse> stages
) {}
