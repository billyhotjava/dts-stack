package com.yuzhi.dts.platform.security.policy;

public final class PolicyErrorCodes {
    private PolicyErrorCodes() {}

    public static final String RBAC_DENY = "dts-sec-0001";
    public static final String SCOPE_MISMATCH = "dts-sec-0002";
    public static final String LEVEL_TOO_LOW = "dts-sec-0003";
    public static final String TEMP_PERMIT_REQUIRED = "dts-sec-0004";
    public static final String CONTEXT_REQUIRED = "dts-sec-0005";
    public static final String INVALID_CONTEXT = "dts-sec-0006";
    public static final String RESOURCE_NOT_VISIBLE = "dts-sec-0007";
    public static final String ENGINE_FILTER_ERROR = "dts-sec-0008";
    public static final String POLICY_CONFIG_MISSING = "dts-sec-0009";
    public static final String TOKEN_CLAIMS_MISSING = "dts-sec-0010";
}

