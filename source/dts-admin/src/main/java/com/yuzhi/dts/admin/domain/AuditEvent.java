package com.yuzhi.dts.admin.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Transient;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * 重构后的审计事件实体。
 * 仅保存人工操作留痕所需的核心字段，剔除旧的加密链及 HTTP 明细。
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "source_system", nullable = false, length = 32)
    private String sourceSystem;

    @Column(name = "actor", nullable = false, length = 128)
    private String actor;

    @Column(name = "actor_role", length = 64)
    private String actorRole;

    @Column(name = "actor_name", length = 128)
    private String actorName;

    @Column(name = "module", nullable = false, length = 64)
    private String module;

    @Column(name = "module_label", length = 128)
    private String moduleLabel;

    @Column(name = "operation_group", length = 64)
    private String operationGroup;

    @Column(name = "operation_code", length = 128)
    private String operationCode;

    @Column(name = "operation_name", nullable = false, length = 256)
    private String operationName;

    @Column(name = "operation_type", nullable = false, length = 32)
    private String operationType;

    @Column(name = "operation_type_text", length = 64)
    private String operationTypeText;

    @Column(name = "result", nullable = false, length = 32)
    private String result;

    @Lob
    @Column(name = "summary")
    private String summary;

    @Column(name = "target_table", length = 128)
    private String targetTable;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_ids")
    private String targetIds;

    @Column(name = "target_id_text", length = 512)
    private String targetIdText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_labels")
    private String targetLabels;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "target_snapshot")
    private String targetSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details")
    private String details;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "client_ip")
    private InetAddress clientIp;

    @Column(name = "client_agent", length = 256)
    private String clientAgent;

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

    @Column(name = "department_name", length = 128)
    private String departmentName;

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

    @Transient
    private String legacyAction;

    @Transient
    private String legacyResourceType;

    @Transient
    private String legacyResourceId;

    @Transient
    private String requestUri;

    @Transient
    private String httpMethod;

    @Transient
    private Integer latencyMs;

    @Transient
    private String extraTags;

    @Transient
    private UUID eventUuid;

    @Transient
    private String eventClass;

    @Transient
    private String eventType;

    @Transient
    private byte[] payloadIv;

    @Transient
    private byte[] payloadCipher;

    @Transient
    private String payloadHmac;

    @Transient
    private String chainSignature;

    @Transient
    private String recordSignature;

    @Transient
    private String signatureKeyVer;

    @Transient
    private String correlationId;

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

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
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

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getModuleLabel() {
        return moduleLabel;
    }

    public void setModuleLabel(String moduleLabel) {
        this.moduleLabel = moduleLabel;
    }

    public String getOperationGroup() {
        return operationGroup;
    }

    public void setOperationGroup(String operationGroup) {
        this.operationGroup = operationGroup;
    }

    public String getOperationCode() {
        return operationCode;
    }

    public void setOperationCode(String operationCode) {
        this.operationCode = operationCode;
    }

    public String getOperationName() {
        return operationName;
    }

    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public String getAction() {
        if (legacyAction != null && !legacyAction.isBlank()) {
            return legacyAction;
        }
        return operationCode;
    }

    public void setAction(String action) {
        this.legacyAction = action;
        this.operationCode = action;
        if (action != null && !action.isBlank() && (this.operationName == null || this.operationName.isBlank())) {
            this.operationName = action;
        }
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getOperationTypeText() {
        return operationTypeText;
    }

    public void setOperationTypeText(String operationTypeText) {
        this.operationTypeText = operationTypeText;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getResourceType() {
        if (legacyResourceType != null && !legacyResourceType.isBlank()) {
            return legacyResourceType;
        }
        return targetTable;
    }

    public void setResourceType(String resourceType) {
        this.legacyResourceType = resourceType;
        this.targetTable = resourceType;
    }

    public String getTargetTable() {
        return targetTable;
    }

    public void setTargetTable(String targetTable) {
        this.targetTable = targetTable;
    }

    public String getResourceId() {
        if (legacyResourceId != null && !legacyResourceId.isBlank()) {
            return legacyResourceId;
        }
        return targetIdText;
    }

    public void setResourceId(String resourceId) {
        this.legacyResourceId = resourceId;
        this.targetIdText = resourceId;
    }

    public String getTargetIds() {
        return targetIds;
    }

    public void setTargetIds(String targetIds) {
        this.targetIds = targetIds;
    }

    public String getTargetIdText() {
        return targetIdText;
    }

    public void setTargetIdText(String targetIdText) {
        this.targetIdText = targetIdText;
    }

    public String getTargetLabels() {
        return targetLabels;
    }

    public void setTargetLabels(String targetLabels) {
        this.targetLabels = targetLabels;
    }

    public String getTargetSnapshot() {
        return targetSnapshot;
    }

    public void setTargetSnapshot(String targetSnapshot) {
        this.targetSnapshot = targetSnapshot;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
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

    public InetAddress getClientIp() {
        return clientIp;
    }

    public void setClientIp(InetAddress clientIp) {
        this.clientIp = clientIp;
    }

    public String getClientAgent() {
        return clientAgent;
    }

    public void setClientAgent(String clientAgent) {
        this.clientAgent = clientAgent;
    }

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getOperatorRoles() {
        return operatorRoles;
    }

    public void setOperatorRoles(String operatorRoles) {
        this.operatorRoles = operatorRoles;
    }

    public String getOrgCode() {
        return orgCode;
    }

    public void setOrgCode(String orgCode) {
        this.orgCode = orgCode;
    }

    public String getOrgName() {
        return orgName;
    }

    public void setOrgName(String orgName) {
        this.orgName = orgName;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public String getExtraTags() {
        return extraTags;
    }

    public void setExtraTags(String extraTags) {
        this.extraTags = extraTags;
    }

    public UUID getEventUuid() {
        return eventUuid;
    }

    public void setEventUuid(UUID eventUuid) {
        this.eventUuid = eventUuid;
    }

    public String getEventClass() {
        return eventClass;
    }

    public void setEventClass(String eventClass) {
        this.eventClass = eventClass;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
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

    public String getRecordSignature() {
        return recordSignature;
    }

    public void setRecordSignature(String recordSignature) {
        this.recordSignature = recordSignature;
    }

    public String getSignatureKeyVer() {
        return signatureKeyVer;
    }

    public void setSignatureKeyVer(String signatureKeyVer) {
        this.signatureKeyVer = signatureKeyVer;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
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
