package com.yuzhi.dts.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.pki")
public class PkiAuthProperties {

    private boolean enabled = false;
    private String mode = "disabled"; // mtls | assertion | disabled
    private boolean acceptForwardedClientCert = false;
    private String clientCertHeaderName = "X-Forwarded-Tls-Client-Cert";
    private String issuerCn;
    private String apiBaseUrl;
    private String apiToken;
    private int apiTimeoutMs = 3000;
    private boolean allowMock = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isAcceptForwardedClientCert() {
        return acceptForwardedClientCert;
    }

    public void setAcceptForwardedClientCert(boolean acceptForwardedClientCert) {
        this.acceptForwardedClientCert = acceptForwardedClientCert;
    }

    public String getClientCertHeaderName() {
        return clientCertHeaderName;
    }

    public void setClientCertHeaderName(String clientCertHeaderName) {
        this.clientCertHeaderName = clientCertHeaderName;
    }

    public String getIssuerCn() {
        return issuerCn;
    }

    public void setIssuerCn(String issuerCn) {
        this.issuerCn = issuerCn;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public int getApiTimeoutMs() {
        return apiTimeoutMs;
    }

    public void setApiTimeoutMs(int apiTimeoutMs) {
        this.apiTimeoutMs = apiTimeoutMs;
    }

    public boolean isAllowMock() {
        return allowMock;
    }

    public void setAllowMock(boolean allowMock) {
        this.allowMock = allowMock;
    }
}

