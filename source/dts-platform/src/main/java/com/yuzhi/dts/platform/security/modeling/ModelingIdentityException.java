package com.yuzhi.dts.platform.security.modeling;

public class ModelingIdentityException extends RuntimeException {
    private final int status;
    private final String code;
    public ModelingIdentityException(int status, String code, String message) { super(message); this.status = status; this.code = code; }
    public int status() { return status; }
    public String code() { return code; }
}
