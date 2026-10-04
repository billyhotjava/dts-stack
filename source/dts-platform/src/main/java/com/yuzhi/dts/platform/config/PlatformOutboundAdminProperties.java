package com.yuzhi.dts.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 出站客户端配置:platform 调 dts-admin 时携带的 base URL、API path、bearer 凭据与自报身份。
 * <p>
 * 与 {@link PlatformInboundServiceAuthProperties} 互不相干 —— 后者描述"谁能调 platform"。
 */
@ConfigurationProperties(prefix = "dts.platform.outbound.admin")
public class PlatformOutboundAdminProperties {

    /** 是否启用对 dts-admin 的出站调用。 */
    private boolean enabled = true;

    /** dts-admin 服务的根 URL,例如 http://dts-admin:8081。 */
    private String baseUrl = "http://dts-admin:8081";

    /** 公开门户接口的相对路径。 */
    private String apiPath = "/api";

    /** 管理端 admin 接口的相对路径。 */
    private String adminApiPath = "/api/admin";

    /** platform 出站调 admin 时携带的 bearer 凭据。 */
    private String serviceToken;

    /** platform 出站时通过 X-DTS-Service header 自报的服务名,默认即 platform 自身。 */
    private String serviceName = "dts-platform";

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

    public String getAdminApiPath() {
        return adminApiPath;
    }

    public void setAdminApiPath(String adminApiPath) {
        this.adminApiPath = adminApiPath;
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
