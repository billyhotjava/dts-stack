package com.yuzhi.dts.platform.service.goldenchain.ops;

import java.util.Locale;

public class GoldenChainOpsErrorClassifier {

    public GoldenChainOpsErrorClassification classify(String message) {
        String text = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (containsAny(text, "401", "403", "unauthorized", "invalid token", "credential", "secret")) {
            return classification(
                GoldenChainOpsErrorCategory.CREDENTIAL_FAILED,
                GoldenChainOpsRetryAction.ROTATE_CREDENTIAL,
                true,
                "检查凭据、token 和 secretRef 后重试"
            );
        }
        if (containsAny(text, "timeout", "connection", "connect refused", "network", "dns")) {
            return classification(
                GoldenChainOpsErrorCategory.CONNECTION_FAILED,
                GoldenChainOpsRetryAction.RETRY_INGESTION,
                true,
                "检查网络连通性和数据源可达性后重试入湖"
            );
        }
        if (containsAny(text, "dbt test", "not_null", "unique", "accepted_values", "quality")) {
            return classification(
                GoldenChainOpsErrorCategory.DATA_QUALITY_FAILED,
                GoldenChainOpsRetryAction.OPEN_BACKFILL,
                true,
                "查看质量规则失败明细，修复数据后发起补数"
            );
        }
        if (containsAny(text, "dbt build", "dbt run", "model build", "relation not found", "compile")) {
            return classification(
                GoldenChainOpsErrorCategory.MODEL_BUILD_FAILED,
                GoldenChainOpsRetryAction.REBUILD_MODEL,
                true,
                "检查 dbt 模型依赖和 source 配置后重新构建"
            );
        }
        if (containsAny(text, "lineage", "openlineage", "manifest", "cannot resolve source")) {
            return classification(
                GoldenChainOpsErrorCategory.LINEAGE_FAILED,
                GoldenChainOpsRetryAction.REFRESH_LINEAGE,
                true,
                "补齐 source/target 映射后刷新血缘"
            );
        }
        if (containsAny(text, "permission", "rls", "masking", "access denied")) {
            return classification(
                GoldenChainOpsErrorCategory.PERMISSION_FAILED,
                GoldenChainOpsRetryAction.REQUEST_PERMISSION,
                false,
                "检查授权、RLS 和 masking 策略"
            );
        }
        return classification(
            GoldenChainOpsErrorCategory.SYSTEM_EXCEPTION,
            GoldenChainOpsRetryAction.MANUAL_REVIEW,
            false,
            "排查系统异常日志并人工确认恢复动作"
        );
    }

    private GoldenChainOpsErrorClassification classification(
        GoldenChainOpsErrorCategory category,
        GoldenChainOpsRetryAction retryAction,
        boolean recoverable,
        String suggestedAction
    ) {
        return new GoldenChainOpsErrorClassification(category, retryAction, recoverable, suggestedAction);
    }

    private boolean containsAny(String text, String... patterns) {
        for (String pattern : patterns) {
            if (text.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}
