package com.yuzhi.dts.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 出站客户端配置:dts-analytics 调 dts-platform 时的目标地址、自报身份、共享 token 与超时设置。
 * <p>
 * Sprint-28 F5 起启用,语义"我作为 analytics 调 platform 用什么"。
 * 旧 {@code dts.analytics.platform.*} 通过 application.yml 三级 fallback 链保留兼容,Sprint-29 移除。
 * <p>
 * 与 analytics 自有的 {@link DtsAdminProperties}(prefix=dts.admin)互不相干 —— 后者描述"我调 admin 用什么"。
 */
@ConfigurationProperties(prefix = "dts.analytics.outbound.platform")
public class AnalyticsOutboundPlatformProperties {

    /** 是否启用对 dts-platform 的出站调用。 */
    private boolean enabled = true;

    /** dts-platform 服务的根 URL。 */
    private String baseUrl = "http://dts-platform:8081";

    /** 公开门户接口的相对路径。 */
    private String apiPath = "/api";

    /** analytics 调 platform 时携带的 token(对应 platform 入站 trustedServices.dts-analytics)。 */
    private String serviceToken;

    /** analytics 通过 X-DTS-Service header 自报的服务名,默认 dts-analytics。 */
    private String serviceName = "dts-analytics";

    /** RestTemplate connect/read timeout, seconds. */
    private long timeoutSeconds = 10L;

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

    public long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
