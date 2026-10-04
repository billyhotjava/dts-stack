package com.yuzhi.dts.platform.domain.service;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "infra_connection_test_log")
public class InfraConnectionTestLog extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "data_source_id", columnDefinition = "uuid")
    private UUID dataSourceId;

    @Lob
    @Column(name = "request_payload")
    private String requestPayload;

    @Column(name = "result", length = 32)
    private String result;

    @Column(name = "message", length = 512)
    private String message;

    @Column(name = "elapsed_ms")
    private Integer elapsedMs;

    @Override
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

    public String getRequestPayload() {
        return requestPayload;
    }

    public void setRequestPayload(String requestPayload) {
        this.requestPayload = requestPayload;
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

    public Integer getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(Integer elapsedMs) {
        this.elapsedMs = elapsedMs;
    }
}
