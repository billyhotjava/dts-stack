package com.yuzhi.dts.platform.service.goldenchain.consumption;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainBlockerCode;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class GoldenChainCustomerScenarioPackageService {

    public GoldenChainCustomerAcceptancePackage create(GoldenChainCustomerScenarioPackageRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        List<String> blockers = collectBlockers(request);
        if (!blockers.isEmpty()) {
            return new GoldenChainCustomerAcceptancePackage(
                false,
                request.scenarioName(),
                null,
                request.steps(),
                request.businessMetrics(),
                evidenceChecklist(request.steps()),
                blockers,
                GoldenChainStageSnapshot.blocked(
                    GoldenChainStage.CONSUMABLE,
                    request.owner(),
                    GoldenChainBlockerCode.BLOCKED_CONSUMPTION,
                    String.join("；", blockers)
                )
            );
        }

        return new GoldenChainCustomerAcceptancePackage(
            true,
            request.scenarioName(),
            buildNarrative(request),
            request.steps(),
            request.businessMetrics(),
            evidenceChecklist(request.steps()),
            List.of(),
            GoldenChainStageSnapshot.ready(
                GoldenChainStage.CONSUMABLE,
                request.owner(),
                "customer-acceptance://%s".formatted(request.scenarioName())
            )
        );
    }

    private List<String> collectBlockers(GoldenChainCustomerScenarioPackageRequest request) {
        List<String> blockers = new ArrayList<>();
        if (request.sourceKinds().isEmpty()) {
            blockers.add("缺少数据源路径");
        }
        if (request.steps().isEmpty()) {
            blockers.add("缺少验收步骤");
        }
        for (GoldenChainCustomerScenarioStep step : request.steps()) {
            if (!hasText(step.evidenceRef())) {
                blockers.add("第 %d 步缺少证据引用".formatted(step.sequence()));
            }
        }
        if (request.businessMetrics().isEmpty()) {
            blockers.add("缺少业务指标说明");
        }
        if (request.reportPublication() == null || !request.reportPublication().publishable()) {
            blockers.add("报表数据集未就绪");
        }
        if (request.dataServiceDecision() == null || !request.dataServiceDecision().operable()) {
            blockers.add("数据服务未就绪");
        }
        if (request.permissionView() == null || !request.permissionView().consumable()) {
            blockers.add("消费权限未就绪");
        }
        if (customerTextLeaksImplementation(request)) {
            blockers.add("客户说明不能暴露 SQL/dbt 内部实现");
        }
        return blockers;
    }

    private String buildNarrative(GoldenChainCustomerScenarioPackageRequest request) {
        String sources = String.join("、", request.sourceKinds().stream().filter(this::hasText).toList());
        String metrics = String.join("、", request.businessMetrics().stream().map(GoldenChainBusinessField::displayName).toList());
        return "%s 已完成从 %s 接入、治理、报表到数据服务的闭环验收；业务可直接查看 %s，并通过数据服务跟踪授权和调用统计。%s".formatted(
            request.scenarioName(),
            sources,
            metrics,
            request.dataServiceDecision().serviceSummary()
        );
    }

    private List<String> evidenceChecklist(List<GoldenChainCustomerScenarioStep> steps) {
        return steps
            .stream()
            .map(step -> "%02d %s: %s (%s)".formatted(step.sequence(), step.title(), step.evidenceRef(), step.evidenceType()))
            .toList();
    }

    private boolean customerTextLeaksImplementation(GoldenChainCustomerScenarioPackageRequest request) {
        if (containsImplementationDetail(request.scenarioName())) {
            return true;
        }
        if (request.sourceKinds().stream().anyMatch(this::containsImplementationDetail)) {
            return true;
        }
        if (
            request.reportPublication() != null &&
            containsImplementationDetail(request.reportPublication().consumerSummary())
        ) {
            return true;
        }
        if (
            request.dataServiceDecision() != null &&
            containsImplementationDetail(request.dataServiceDecision().serviceSummary())
        ) {
            return true;
        }
        return request.steps().stream().anyMatch(this::stepLeaksImplementation) ||
        request.businessMetrics().stream().anyMatch(this::businessFieldLeaksImplementation);
    }

    private boolean stepLeaksImplementation(GoldenChainCustomerScenarioStep step) {
        return containsImplementationDetail(step.title()) || containsImplementationDetail(step.businessOutcome());
    }

    private boolean businessFieldLeaksImplementation(GoldenChainBusinessField field) {
        return containsImplementationDetail(field.displayName()) ||
        containsImplementationDetail(field.businessDefinition()) ||
        containsImplementationDetail(field.unit()) ||
        containsImplementationDetail(field.threshold());
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
