package com.yuzhi.dts.admin.service.infra.dto;

import java.time.Instant;
import java.util.UUID;

public class ConnectionTestLogDto {

    private UUID id;

    private UUID dataSourceId;

    private String result;

    private String message;

    private Long elapsedMs;

    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(UUID dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(Long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public ConnectionTestLogDto copy() {
        ConnectionTestLogDto dto = new ConnectionTestLogDto();
        dto.setId(id);
        dto.setDataSourceId(dataSourceId);
        dto.setResult(result);
        dto.setMessage(message);
        dto.setElapsedMs(elapsedMs);
        dto.setCreatedAt(createdAt);
        return dto;
    }
}
