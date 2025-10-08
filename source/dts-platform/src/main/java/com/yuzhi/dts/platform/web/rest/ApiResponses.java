package com.yuzhi.dts.platform.web.rest;

public final class ApiResponses {

    private ApiResponses() {}

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ResultStatus.SUCCESS.getCode(), "OK", data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(ResultStatus.ERROR.getCode(), message, (String) null, null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(ResultStatus.ERROR.getCode(), message, code, null);
    }
}
