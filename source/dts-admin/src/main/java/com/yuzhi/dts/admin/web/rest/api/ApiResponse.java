package com.yuzhi.dts.admin.web.rest.api;

public class ApiResponse<T> {
    private ResultStatus status;
    private String message;
    private T data;

    public ApiResponse() {}

    public ApiResponse(ResultStatus status, String message, T data) {
        this.status = status;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ResultStatus.SUCCESS, "success", data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(ResultStatus.ERROR, message, null);
    }

    public ResultStatus getStatus() {
        return status;
    }

    public void setStatus(ResultStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}

