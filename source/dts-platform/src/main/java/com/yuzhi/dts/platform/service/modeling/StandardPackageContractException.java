package com.yuzhi.dts.platform.service.modeling;

public final class StandardPackageContractException extends IllegalArgumentException {

    private final String code;

    public StandardPackageContractException(String code, String message) {
        super("[" + code + "] " + message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
