package com.yuzhi.dts.platform.domain.event;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_event_outbox")
public class PlatformEventOutbox extends AbstractAuditingEntity<UUID> implements Serializable {

    public static final String DISPATCH_PENDING = "PENDING";
    public static final String DISPATCH_SKIPPED = "SKIPPED";
    public static final String DISPATCH_SENT = "SENT";
    public static final String DISPATCH_FAILED = "FAILED";

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "event_id", length = 80, nullable = false, unique = true)
    private String eventId;

    @Column(name = "event_type", length = 128, nullable = false)
    private String eventType;

    @Column(name = "domain", length = 64, nullable = false)
    private String domain;

    @Column(name = "source_app", length = 64, nullable = false)
    private String sourceApp;

    @Column(name = "aggregate_type", length = 96)
    private String aggregateType;

    @Column(name = "aggregate_id", length = 128)
    private String aggregateId;

    @Column(name = "aggregate_name", length = 256)
    private String aggregateName;

    @Column(name = "action", length = 64, nullable = false)
    private String action;

    @Column(name = "severity", length = 32, nullable = false)
    private String severity;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor", length = 128)
    private String actor;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "trace_id", length = 128)
    private String traceId;

    @Column(name = "audit_action_code", length = 128)
    private String auditActionCode;

    @Column(name = "policy_ref", length = 256)
    private String policyRef;

    @Column(name = "payload_json", columnDefinition = "text")
    private String payloadJson;

    @Column(name = "dispatch_status", length = 32, nullable = false)
    private String dispatchStatus = DISPATCH_PENDING;

    @Column(name = "dispatch_attempts", nullable = false)
    private Integer dispatchAttempts = 0;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "dispatch_error", length = 2048)
    private String dispatchError;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getSourceApp() {
        return sourceApp;
    }

    public void setSourceApp(String sourceApp) {
        this.sourceApp = sourceApp;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getAggregateName() {
        return aggregateName;
    }

    public void setAggregateName(String aggregateName) {
        this.aggregateName = aggregateName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getAuditActionCode() {
        return auditActionCode;
    }

    public void setAuditActionCode(String auditActionCode) {
        this.auditActionCode = auditActionCode;
    }

    public String getPolicyRef() {
        return policyRef;
    }

    public void setPolicyRef(String policyRef) {
        this.policyRef = policyRef;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public String getDispatchStatus() {
        return dispatchStatus;
    }

    public void setDispatchStatus(String dispatchStatus) {
        this.dispatchStatus = dispatchStatus;
    }

    public Integer getDispatchAttempts() {
        return dispatchAttempts;
    }

    public void setDispatchAttempts(Integer dispatchAttempts) {
        this.dispatchAttempts = dispatchAttempts;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public void setDispatchedAt(Instant dispatchedAt) {
        this.dispatchedAt = dispatchedAt;
    }

    public String getDispatchError() {
        return dispatchError;
    }

    public void setDispatchError(String dispatchError) {
        this.dispatchError = dispatchError;
    }
}
