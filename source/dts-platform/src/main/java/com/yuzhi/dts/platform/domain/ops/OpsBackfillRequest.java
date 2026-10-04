package com.yuzhi.dts.platform.domain.ops;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ops_backfill_request")
public class OpsBackfillRequest extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "dag_id", length = 128, nullable = false)
    private String dagId;

    @Column(name = "name", length = 160)
    private String name;

    @Column(name = "date_from")
    private LocalDate dateFrom;

    @Column(name = "date_to")
    private LocalDate dateTo;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "message", length = 1024)
    private String message;

    @Column(name = "triggered_at")
    private Instant triggeredAt;

    @Column(name = "external_run_id", length = 256)
    private String externalRunId;

    @Column(name = "payload_json", columnDefinition = "text")
    private String payloadJson;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getDagId() {
        return dagId;
    }

    public void setDagId(String dagId) {
        this.dagId = dagId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(LocalDate dateFrom) {
        this.dateFrom = dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public void setDateTo(LocalDate dateTo) {
        this.dateTo = dateTo;
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

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(Instant triggeredAt) {
        this.triggeredAt = triggeredAt;
    }

    public String getExternalRunId() {
        return externalRunId;
    }

    public void setExternalRunId(String externalRunId) {
        this.externalRunId = externalRunId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }
}
