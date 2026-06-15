package com.yuzhi.dts.platform.service.goldenchain.consumption;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainBlockerCode;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageSnapshot;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainModelLayer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class GoldenChainReportDatasetPublisherService {

    public GoldenChainReportDatasetPublication publish(GoldenChainReportDatasetPublishRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        List<String> blockers = collectBlockers(request);
        if (!blockers.isEmpty()) {
            return new GoldenChainReportDatasetPublication(
                false,
                null,
                null,
                null,
                request.fields(),
                blockers,
                GoldenChainStageSnapshot.blocked(
                    GoldenChainStage.CONSUMABLE,
                    request.owner(),
                    GoldenChainBlockerCode.BLOCKED_CONSUMPTION,
                    String.join("；", blockers)
                )
            );
        }

        return new GoldenChainReportDatasetPublication(
            true,
            "bi-dataset://%s".formatted(request.assetKey()),
            "metrics://%s".formatted(request.assetKey()),
            buildConsumerSummary(request),
            request.fields(),
            List.of(),
            GoldenChainStageSnapshot.ready(
                GoldenChainStage.CONSUMABLE,
                request.owner(),
                "consumption://dataset/%s".formatted(request.assetKey())
            )
        );
    }

    private List<String> collectBlockers(GoldenChainReportDatasetPublishRequest request) {
        List<String> blockers = new ArrayList<>();
        if (request.releaseDecision() == null || !request.releaseDecision().publishable()) {
            blockers.add("模型发布门禁未通过");
        } else if (
            request.releaseDecision().layer() != GoldenChainModelLayer.DWS &&
            request.releaseDecision().layer() != GoldenChainModelLayer.ADS
        ) {
            blockers.add("只允许 DWS/ADS 资产发布到报表和指标入口");
        }
        if (request.permissionDecision() == null || !request.permissionDecision().consistent()) {
            blockers.add(permissionSafeMessage(request));
        }
        if (request.fields().isEmpty()) {
            blockers.add("缺少业务字段");
        } else if (request.fields().stream().noneMatch(field -> field.role() == GoldenChainBusinessFieldRole.METRIC)) {
            blockers.add("至少需要一个指标字段");
        }
        if (businessTextLeaksImplementation(request)) {
            blockers.add("业务说明不能暴露 SQL/dbt 内部实现");
        }
        return blockers;
    }

    private String permissionSafeMessage(GoldenChainReportDatasetPublishRequest request) {
        if (request.permissionDecision() != null && hasText(request.permissionDecision().safeMessage())) {
            return request.permissionDecision().safeMessage();
        }
        return "权限门禁未通过";
    }

    private String buildConsumerSummary(GoldenChainReportDatasetPublishRequest request) {
        String fieldNames = String.join("、", request.fields().stream().map(GoldenChainBusinessField::displayName).toList());
        return "报表数据集《%s》已发布，业务用户可按 %s 选择指标和维度；负责人：%s。".formatted(
            request.reportName(),
            fieldNames,
            request.owner()
        );
    }

    private boolean businessTextLeaksImplementation(GoldenChainReportDatasetPublishRequest request) {
        if (containsImplementationDetail(request.reportName())) {
            return true;
        }
        return request
            .fields()
            .stream()
            .anyMatch(field ->
                containsImplementationDetail(field.displayName()) ||
                containsImplementationDetail(field.businessDefinition()) ||
                containsImplementationDetail(field.unit()) ||
                containsImplementationDetail(field.threshold())
            );
    }

    private boolean containsImplementationDetail(String value) {
        if (!hasText(value)) {
            return false;
        }
        String lower = " " + value.toLowerCase(Locale.ROOT) + " ";
        return lower.contains(" select ") ||
        lower.contains(" from ") ||
        lower.contains(" join ") ||
        lower.contains(" where ") ||
        lower.contains(" dbt") ||
        lower.contains(".sql") ||
        lower.contains(" ods_") ||
        lower.contains(" stg_") ||
        lower.contains(" dwd_") ||
        lower.contains(" dws_") ||
        lower.contains(" ads_");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
