package com.yuzhi.dts.platform.service.admin.gateway.support;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AdminGatewayEnvelope<T>(
    @JsonProperty("status") String status,
    @JsonProperty("message") String message,
    @JsonProperty("data") T data
) {
    public boolean isSuccess() {
        return status != null && ("SUCCESS".equalsIgnoreCase(status) || "OK".equalsIgnoreCase(status) || "200".equalsIgnoreCase(status));
    }
}
