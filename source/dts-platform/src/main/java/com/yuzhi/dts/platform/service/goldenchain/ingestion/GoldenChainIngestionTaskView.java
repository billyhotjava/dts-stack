package com.yuzhi.dts.platform.service.goldenchain.ingestion;

import com.yuzhi.dts.platform.domain.goldenchain.GoldenChainSourceKind;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageSnapshot;
import java.util.List;

public record GoldenChainIngestionTaskView(
    GoldenChainSourceKind sourceKind,
    String taskRefType,
    String taskRefId,
    String sourceRefType,
    String sourceRefId,
    List<String> odsOutputs,
    String checkpointRef,
    GoldenChainStageSnapshot stageSnapshot
) {}
