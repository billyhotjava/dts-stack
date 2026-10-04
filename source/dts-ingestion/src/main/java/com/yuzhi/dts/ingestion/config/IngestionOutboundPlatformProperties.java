package com.yuzhi.dts.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 出站客户端配置:dts-ingestion 调 dts-platform 时的目标地址、自报身份与共享 token。
 * <p>
 * Sprint-28 F4 起启用,语义"我作为 ingestion 调 platform 用什么"。
 * 旧 {@code dts.platform.*}(包括 {@code DTS_PLATFORM_SERVICE_TOKEN} / {@code DTS_ADMIN_SERVICE_TOKEN})
 * 通过 application.yml 内三级 fallback 链保留兼容,Sprint-29 起移除。
 */
@ConfigurationProperties(prefix = "dts.ingestion.outbound.platform")
public class IngestionOutboundPlatformProperties {

    /** 是否启用对 dts-platform 的出站调用。 */
    private boolean enabled = true;

    /** dts-platform 服务的根 URL。 */
    private String baseUrl = "http://dts-platform:8081";

    /** 公开门户接口的相对路径。 */
    private String apiPath = "/api";

    /** ingestion 出站调 platform 时携带的共享 token(对应 platform 入站 trustedServices.dts-ingestion)。 */
    private String serviceToken;

    /** ingestion 通过 X-DTS-Service header 自报的服务名,默认 dts-ingestion。 */
    private String serviceName = "dts-ingestion";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiPath() {
        return apiPath;
    }

    public void setApiPath(String apiPath) {
        this.apiPath = apiPath;
    }

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}
