package com.yuzhi.dts.platform.service.goldenchain.consumption;

import com.yuzhi.dts.platform.service.goldenchain.governance.GoldenChainPermissionConsistencyDecision;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainModelReleaseDecision;
import java.util.List;

public record GoldenChainReportDatasetPublishRequest(
    String chainKey,
    String assetKey,
    String reportName,
    String owner,
    GoldenChainModelReleaseDecision releaseDecision,
    GoldenChainPermissionConsistencyDecision permissionDecision,
    List<GoldenChainBusinessField> fields
) {
    public GoldenChainReportDatasetPublishRequest {
        chainKey = requireText(chainKey, "链路 key 不能为空");
        assetKey = requireText(assetKey, "资产 key 不能为空");
        reportName = requireText(reportName, "报表名称不能为空");
        owner = requireText(owner, "负责人不能为空");
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    private static String requireText(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
