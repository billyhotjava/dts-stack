package com.yuzhi.dts.platform.service.goldenchain;

import java.util.Optional;

public enum GoldenChainStage {
    DRAFT(0, "草稿"),
    SOURCE_READY(10, "数据源就绪"),
    INGESTION_READY(20, "入湖任务就绪"),
    ODS_READY(30, "ODS 就绪"),
    MODEL_READY(40, "DWD/DWS/ADS 模型就绪"),
    GOVERNANCE_READY(50, "治理证据就绪"),
    RELEASE_READY(60, "发布门禁通过"),
    CONSUMABLE(70, "消费资产可用"),
    OPERATED(80, "运维纳管");

    private final int sequence;
    private final String label;

    GoldenChainStage(int sequence, String label) {
        this.sequence = sequence;
        this.label = label;
    }

    public int sequence() {
        return sequence;
    }

    public String label() {
        return label;
    }

    public Optional<GoldenChainStage> next() {
        int nextOrdinal = ordinal() + 1;
        GoldenChainStage[] stages = values();
        if (nextOrdinal >= stages.length) {
            return Optional.empty();
        }
        return Optional.of(stages[nextOrdinal]);
    }
}
