package com.yuzhi.dts.platform.domain.audit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_event")
public class AuditEvent implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor", nullable = false, length = 128)
    private String actor;

    @Column(name = "actor_role", length = 64)
    private String actorRole;

    @Column(name = "module", nullable = false, length = 64)
    private String module;

    @Column(name = "action", nullable = false, length = 128)
    private String action;

    @Column(name = "resource_type", length = 64)
    private String resourceType;

    @Column(name = "resource_id", length = 256)
    private String resourceId;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "client_ip")
    @JsonIgnore
    private InetAddress clientIp;

    @Column(name = "client_agent", length = 256)
    private String clientAgent;

    @Column(name = "request_uri")
    private String requestUri;

    @Column(name = "http_method", length = 16)
    private String httpMethod;

    @Column(name = "result", nullable = false, length = 32)
    private String result;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "payload_iv")
    @JsonIgnore
    private byte[] payloadIv;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "payload_cipher")
    @JsonIgnore
    private byte[] payloadCipher;

    @Column(name = "payload_hmac", nullable = false, length = 128)
    private String payloadHmac;

    @Column(name = "chain_signature", nullable = false, length = 128)
    private String chainSignature;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_tags")
    private String extraTags;

    // Extended columns for unified audit model
    @Column(name = "event_uuid")
    private java.util.UUID eventUuid;

    @Column(name = "event_class", length = 32)
    private String eventClass;

    @Column(name = "event_type", length = 64)
    private String eventType;

    @Column(name = "source_system", length = 32)
    private String sourceSystem;

    @Column(name = "operator_id", length = 128)
    private String operatorId;

    @Column(name = "operator_name", length = 128)
    private String operatorName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "operator_roles")
    private String operatorRoles;

    @Column(name = "org_code", length = 64)
    private String orgCode;

    @Column(name = "org_name", length = 128)
    private String orgName;

    @Column(name = "summary")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details")
    private String details;

    @Column(name = "record_signature", length = 128)
    private String recordSignature;

    @Column(name = "signature_key_ver", length = 16)
    private String signatureKeyVer;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_date")
    private Instant createdDate;

    @Column(name = "last_modified_by", length = 128)
    private String lastModifiedBy;

    @UpdateTimestamp
    @Column(name = "last_modified_date")
    private Instant lastModifiedDate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    @JsonIgnore
    public InetAddress getClientIpAddress() {
        return clientIp;
    }

    public String getClientIp() {
        return clientIp != null ? clientIp.getHostAddress() : null;
    }

    public void setClientIp(InetAddress clientIp) {
        this.clientIp = clientIp;
    }

    public void setClientIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            this.clientIp = null;
            return;
        }
        try {
            this.clientIp = InetAddress.getByName(clientIp.trim());
        } catch (UnknownHostException ignored) {
            this.clientIp = null;
        }
    }

    public String getClientAgent() {
        return clientAgent;
    }

    public void setClientAgent(String clientAgent) {
        this.clientAgent = clientAgent;
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

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public byte[] getPayloadIv() {
        return payloadIv;
    }

    public void setPayloadIv(byte[] payloadIv) {
        this.payloadIv = payloadIv;
    }

    public byte[] getPayloadCipher() {
        return payloadCipher;
    }

    public void setPayloadCipher(byte[] payloadCipher) {
        this.payloadCipher = payloadCipher;
    }

    public String getPayloadHmac() {
        return payloadHmac;
    }

    public void setPayloadHmac(String payloadHmac) {
        this.payloadHmac = payloadHmac;
    }

    public String getChainSignature() {
        return chainSignature;
    }

    public void setChainSignature(String chainSignature) {
        this.chainSignature = chainSignature;
    }

    public String getExtraTags() {
        return extraTags;
    }

    public void setExtraTags(String extraTags) {
        this.extraTags = extraTags;
    }

    public java.util.UUID getEventUuid() { return eventUuid; }
    public void setEventUuid(java.util.UUID eventUuid) { this.eventUuid = eventUuid; }
    public String getEventClass() { return eventClass; }
    public void setEventClass(String eventClass) { this.eventClass = eventClass; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getOperatorRoles() { return operatorRoles; }
    public void setOperatorRoles(String operatorRoles) { this.operatorRoles = operatorRoles; }
    public String getOrgCode() { return orgCode; }
    public void setOrgCode(String orgCode) { this.orgCode = orgCode; }
    public String getOrgName() { return orgName; }
    public void setOrgName(String orgName) { this.orgName = orgName; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public String getRecordSignature() { return recordSignature; }
    public void setRecordSignature(String recordSignature) { this.recordSignature = recordSignature; }
    public String getSignatureKeyVer() { return signatureKeyVer; }
    public void setSignatureKeyVer(String signatureKeyVer) { this.signatureKeyVer = signatureKeyVer; }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public Instant getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(Instant lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }
}
