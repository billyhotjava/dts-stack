package com.yuzhi.dts.platform.service.admin.gateway.support;

public class AdminGatewayException extends RuntimeException {

    private final Integer upstreamStatus;
    private final String upstreamPath;

    public AdminGatewayException(String message) {
        this(message, null, null, null);
    }

    public AdminGatewayException(String message, Throwable cause) {
        this(message, null, null, cause);
    }

    public AdminGatewayException(String message, Integer upstreamStatus, String upstreamPath) {
        this(message, upstreamStatus, upstreamPath, null);
    }

    public AdminGatewayException(String message, Integer upstreamStatus, String upstreamPath, Throwable cause) {
        super(message, cause);
        this.upstreamStatus = upstreamStatus;
        this.upstreamPath = upstreamPath;
    }

    public Integer getUpstreamStatus() {
        return upstreamStatus;
    }

    public String getUpstreamPath() {
        return upstreamPath;
    }
}
