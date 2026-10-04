package com.yuzhi.dts.platform.service.goldenchain.consumption;

import com.yuzhi.dts.platform.service.goldenchain.governance.GoldenChainPermissionConsistencyDecision;
import java.util.List;

public record GoldenChainDataServiceSubscriptionRequest(
    String serviceKey,
    String serviceName,
    String assetKey,
    String apiVersion,
    String owner,
    String subscriberRef,
    List<GoldenChainBusinessField> productSchema,
    List<GoldenChainDataServiceConsumptionMode> consumptionModes,
    String sla,
    String refreshFrequency,
    GoldenChainPermissionConsistencyDecision permissionDecision,
    GoldenChainApiAuthorizationSnapshot authorizationSnapshot
) {
    public GoldenChainDataServiceSubscriptionRequest {
        serviceKey = requireText(serviceKey, "服务 key 不能为空");
        serviceName = requireText(serviceName, "服务名称不能为空");
        assetKey = requireText(assetKey, "资产 key 不能为空");
        apiVersion = requireText(apiVersion, "API 版本不能为空");
        owner = requireText(owner, "负责人不能为空");
        subscriberRef = requireText(subscriberRef, "订阅方不能为空");
        productSchema = productSchema == null ? List.of() : List.copyOf(productSchema);
        consumptionModes = consumptionModes == null ? List.of() : List.copyOf(consumptionModes);
        sla = normalize(sla);
        refreshFrequency = normalize(refreshFrequency);
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
