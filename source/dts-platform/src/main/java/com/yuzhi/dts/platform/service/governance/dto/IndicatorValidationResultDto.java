package com.yuzhi.dts.platform.service.governance.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class IndicatorValidationResultDto {

    private UUID indicatorId;
    private String status;
    private String message;
    private Instant validatedAt;
    private String signature;
    private String effectiveSql;
    private List<String> headers;
    private Long rowCount;
    private Long durationMs;

    public UUID getIndicatorId() {
        return indicatorId;
    }

    public void setIndicatorId(UUID indicatorId) {
        this.indicatorId = indicatorId;
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

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public void setValidatedAt(Instant validatedAt) {
        this.validatedAt = validatedAt;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getEffectiveSql() {
        return effectiveSql;
    }

    public void setEffectiveSql(String effectiveSql) {
        this.effectiveSql = effectiveSql;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public Long getRowCount() {
        return rowCount;
    }

    public void setRowCount(Long rowCount) {
        this.rowCount = rowCount;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }
}

