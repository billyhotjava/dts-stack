package com.yuzhi.dts.admin.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_classification_miss")
public class AuditClassificationMiss {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_system", length = 32, nullable = false)
    private String sourceSystem;

    @Column(name = "action_code", length = 128, nullable = false)
    private String actionCode;

    @Column(name = "module_key_raw", length = 128)
    private String moduleKeyRaw;

    @Column(name = "request_uri", length = 512)
    private String requestUri;

    @Column(name = "http_method", length = 16)
    private String httpMethod;

    @Column(name = "actor_id", length = 128)
    private String actorId;

    @Column(name = "reason", length = 128, nullable = false)
    private String reason;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "occurrence_count", nullable = false)
    private Long occurrenceCount = 1L;

    public Long getId() {
        return id;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public String getModuleKeyRaw() {
        return moduleKeyRaw;
    }

    public void setModuleKeyRaw(String moduleKeyRaw) {
        this.moduleKeyRaw = moduleKeyRaw;
    }

    public String getRequestUri() {
        return requestUri;
    }

    public void setRequestUri(String requestUri) {
        this.requestUri = requestUri;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(Instant firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Long getOccurrenceCount() {
        return occurrenceCount;
    }

    public void setOccurrenceCount(Long occurrenceCount) {
        this.occurrenceCount = occurrenceCount;
    }
}
