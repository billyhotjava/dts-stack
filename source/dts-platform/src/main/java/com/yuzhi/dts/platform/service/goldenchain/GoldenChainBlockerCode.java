package com.yuzhi.dts.platform.service.goldenchain;

public enum GoldenChainBlockerCode {
    BLOCKED_SOURCE(GoldenChainStage.SOURCE_READY, "数据源阻断", "检查连接配置、凭据和可达性"),
    BLOCKED_INGESTION(GoldenChainStage.INGESTION_READY, "入湖阻断", "检查入湖任务、调度实例和目标表写入"),
    BLOCKED_MODEL(GoldenChainStage.MODEL_READY, "建模阻断", "检查 ODS 映射、dbt source、模型构建和测试结果"),
    BLOCKED_GOVERNANCE(GoldenChainStage.GOVERNANCE_READY, "治理阻断", "补齐 owner、分级分类、质量和血缘证据"),
    BLOCKED_PERMISSION(GoldenChainStage.RELEASE_READY, "权限阻断", "补齐发布审批、资产授权和行列级安全策略"),
    BLOCKED_CONSUMPTION(GoldenChainStage.CONSUMABLE, "消费阻断", "检查指标、报表、大屏或数据服务的消费出口");

    private final GoldenChainStage blockedStage;
    private final String label;
    private final String remediation;

    GoldenChainBlockerCode(GoldenChainStage blockedStage, String label, String remediation) {
        this.blockedStage = blockedStage;
        this.label = label;
        this.remediation = remediation;
    }

    public GoldenChainStage blockedStage() {
        return blockedStage;
    }

    public String label() {
        return label;
    }

    public String remediation() {
        return remediation;
    }
}
