package com.yuzhi.dts.platform.service.admin.gateway.support;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AdminGatewayRequestOptions {

    private final boolean auditSilent;
    private final boolean forwardAuthorization;
    private final boolean includeServiceAuthorization;
    private final Map<String, String> extraHeaders;

    private AdminGatewayRequestOptions(Builder builder) {
        this.auditSilent = builder.auditSilent;
        this.forwardAuthorization = builder.forwardAuthorization;
        this.includeServiceAuthorization = builder.includeServiceAuthorization;
        this.extraHeaders = Collections.unmodifiableMap(new LinkedHashMap<>(builder.extraHeaders));
    }

    public static AdminGatewayRequestOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean auditSilent() {
        return auditSilent;
    }

    public boolean forwardAuthorization() {
        return forwardAuthorization;
    }

    public boolean includeServiceAuthorization() {
        return includeServiceAuthorization;
    }

    public Map<String, String> extraHeaders() {
        return extraHeaders;
    }

    public static final class Builder {

        private boolean auditSilent;
        private boolean forwardAuthorization;
        private boolean includeServiceAuthorization = true;
        private final Map<String, String> extraHeaders = new LinkedHashMap<>();

        public Builder auditSilent(boolean auditSilent) {
            this.auditSilent = auditSilent;
            return this;
        }

        public Builder forwardAuthorization(boolean forwardAuthorization) {
            this.forwardAuthorization = forwardAuthorization;
            return this;
        }

        public Builder includeServiceAuthorization(boolean includeServiceAuthorization) {
            this.includeServiceAuthorization = includeServiceAuthorization;
            return this;
        }

        public Builder header(String name, String value) {
            if (name != null && value != null) {
                this.extraHeaders.put(name, value);
            }
            return this;
        }

        public AdminGatewayRequestOptions build() {
            return new AdminGatewayRequestOptions(this);
        }
    }
}
