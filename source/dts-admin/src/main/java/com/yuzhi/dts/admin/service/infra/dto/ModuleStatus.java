package com.yuzhi.dts.admin.service.infra.dto;

public class ModuleStatus {

    private String module;

    private String status;

    private String message;

    private String updatedAt;

    public ModuleStatus() {}

    public ModuleStatus(String module, String status, String message, String updatedAt) {
        this.module = module;
        this.status = status;
        this.message = message;
        this.updatedAt = updatedAt;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
