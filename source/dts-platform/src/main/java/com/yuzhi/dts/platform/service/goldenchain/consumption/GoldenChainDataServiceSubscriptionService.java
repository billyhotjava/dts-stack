package com.yuzhi.dts.platform.service.goldenchain.consumption;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainBlockerCode;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class GoldenChainDataServiceSubscriptionService {

    public GoldenChainDataServiceSubscriptionDecision evaluate(GoldenChainDataServiceSubscriptionRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        if (request.permissionDecision() == null || !request.permissionDecision().consistent()) {
            String safeMessage = permissionSafeMessage(request);
            return denied(request, List.of(safeMessage), safeMessage);
        }

        List<String> blockers = collectConfigurationBlockers(request);
        if (!blockers.isEmpty()) {
            return denied(request, blockers, String.join("；", blockers));
        }

        GoldenChainApiAuthorizationSnapshot authorization = request.authorizationSnapshot();
        if (authorization == null || !authorization.approved()) {
            return new GoldenChainDataServiceSubscriptionDecision(
                false,
                GoldenChainDataServiceSubscriptionStatus.PENDING_APPROVAL,
                serviceViewRef(request),
                authorization == null ? null : authorization.tokenRef(),
                authorization == null ? 0L : authorization.callCount(),
                List.of(request.assetKey()),
                null,
                List.of("订阅审批未完成"),
                "订阅审批未完成",
                GoldenChainStageSnapshot.pending(GoldenChainStage.CONSUMABLE, request.owner())
            );
        }
        if (!isSecretRef(authorization.tokenRef())) {
            return denied(request, List.of("token 必须使用 secretRef 引用"), "token 必须使用 secretRef 引用");
        }

        return new GoldenChainDataServiceSubscriptionDecision(
            true,
            GoldenChainDataServiceSubscriptionStatus.APPROVED,
            serviceViewRef(request),
            authorization.tokenRef(),
            authorization.callCount(),
            List.of(request.assetKey()),
            buildServiceSummary(request),
            List.of(),
            "服务可用",
            GoldenChainStageSnapshot.ready(
                GoldenChainStage.CONSUMABLE,
                request.owner(),
                "%s/subscribers/%s".formatted(serviceViewRef(request), request.subscriberRef())
            )
        );
    }

    private GoldenChainDataServiceSubscriptionDecision denied(
        GoldenChainDataServiceSubscriptionRequest request,
        List<String> blockers,
        String safeMessage
    ) {
        GoldenChainApiAuthorizationSnapshot authorization = request.authorizationSnapshot();
        return new GoldenChainDataServiceSubscriptionDecision(
            false,
            GoldenChainDataServiceSubscriptionStatus.DENIED,
            serviceViewRef(request),
            authorization == null ? null : authorization.tokenRef(),
            authorization == null ? 0L : authorization.callCount(),
            List.of(request.assetKey()),
            null,
            blockers,
            safeMessage,
            GoldenChainStageSnapshot.blocked(
                GoldenChainStage.CONSUMABLE,
                request.owner(),
                GoldenChainBlockerCode.BLOCKED_CONSUMPTION,
                safeMessage
            )
        );
    }

    private List<String> collectConfigurationBlockers(GoldenChainDataServiceSubscriptionRequest request) {
        List<String> blockers = new ArrayList<>();
        if (request.productSchema().isEmpty()) {
            blockers.add("缺少数据产品 schema");
        }
        if (request.consumptionModes().isEmpty()) {
            blockers.add("缺少消费方式");
        }
        if (!hasText(request.sla())) {
            blockers.add("缺少 SLA");
        }
        if (!hasText(request.refreshFrequency())) {
            blockers.add("缺少刷新频率");
        }
        if (businessTextLeaksImplementation(request)) {
            blockers.add("服务说明不能暴露 SQL/dbt 内部实现");
        }
        return blockers;
    }

    private String permissionSafeMessage(GoldenChainDataServiceSubscriptionRequest request) {
        if (request.permissionDecision() != null && hasText(request.permissionDecision().safeMessage())) {
            return request.permissionDecision().safeMessage();
        }
        return "无权限访问该资产";
    }

    private String serviceViewRef(GoldenChainDataServiceSubscriptionRequest request) {
        return "data-service://%s/%s".formatted(request.serviceKey(), request.apiVersion());
    }

    private String buildServiceSummary(GoldenChainDataServiceSubscriptionRequest request) {
        String schemaFields = String.join("、", request.productSchema().stream().map(GoldenChainBusinessField::displayName).toList());
        return "%s 已授权，schema 包含 %s；刷新频率：%s；SLA：%s。".formatted(
            request.serviceName(),
            schemaFields,
            request.refreshFrequency(),
            request.sla()
        );
    }

    private boolean businessTextLeaksImplementation(GoldenChainDataServiceSubscriptionRequest request) {
        if (
            containsImplementationDetail(request.serviceName()) ||
            containsImplementationDetail(request.sla()) ||
            containsImplementationDetail(request.refreshFrequency())
        ) {
            return true;
        }
        return request
            .productSchema()
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

    private boolean isSecretRef(String value) {
        return hasText(value) && value.startsWith("secretRef://");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
