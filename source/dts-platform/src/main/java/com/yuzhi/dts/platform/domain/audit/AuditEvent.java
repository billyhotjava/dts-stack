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
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

    @Column(name = "client_ip")
    private String clientIp;

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

    @Lob
    @Column(name = "payload_iv")
    @JsonIgnore
    private byte[] payloadIv;

    @Lob
    @Column(name = "payload_cipher")
    @JsonIgnore
    private byte[] payloadCipher;

    @Column(name = "payload_hmac", nullable = false, length = 128)
    private String payloadHmac;

    @Column(name = "chain_signature", nullable = false, length = 128)
    private String chainSignature;

    @Column(name = "extra_tags")
    private String extraTags;

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

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
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
