package com.yuzhi.dts.admin.domain.audit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_entry")
public class AuditEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "source_system", nullable = false, length = 32)
    private String sourceSystem;

    @Column(name = "module_key", nullable = false, length = 64)
    private String moduleKey;

    @Column(name = "module_name", length = 128)
    private String moduleName;

    @Column(name = "button_code", length = 128)
    private String buttonCode;

    @Column(name = "operation_code", length = 128)
    private String operationCode;

    @Column(name = "operation_name", length = 256)
    private String operationName;

    @Column(name = "operation_kind", nullable = false, length = 32)
    private String operationKind;

    @Column(name = "result", nullable = false, length = 32)
    private String result;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "actor_id", nullable = false, length = 128)
    private String actorId;

    @Column(name = "actor_name", length = 128)
    private String actorName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "actor_roles")
    private List<String> actorRoles = new ArrayList<>();

    @Column(name = "change_request_ref", length = 64)
    private String changeRequestRef;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "client_ip")
    private InetAddress clientIp;

    @Column(name = "client_agent", length = 256)
    private String clientAgent;

    @Column(name = "request_uri", length = 512)
    private String requestUri;

    @Column(name = "http_method", length = 16)
    private String httpMethod;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata")
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_attributes")
    private Map<String, Object> extraAttributes = new LinkedHashMap<>();

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    @JsonIgnore
    private List<AuditEntryTarget> targets = new ArrayList<>();

    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    @JsonIgnore
    private List<AuditEntryDetail> details = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
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

    public String getModuleKey() {
        return moduleKey;
    }

    public void setModuleKey(String moduleKey) {
        this.moduleKey = moduleKey;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getButtonCode() {
        return buttonCode;
    }

    public void setButtonCode(String buttonCode) {
        this.buttonCode = buttonCode;
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

    public String getOperationKind() {
        return operationKind;
    }

    public void setOperationKind(String operationKind) {
        this.operationKind = operationKind;
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

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public List<String> getActorRoles() {
        return actorRoles;
    }

    public void setActorRoles(List<String> actorRoles) {
        this.actorRoles = actorRoles == null ? new ArrayList<>() : new ArrayList<>(actorRoles);
    }

    public String getChangeRequestRef() {
        return changeRequestRef;
    }

    public void setChangeRequestRef(String changeRequestRef) {
        this.changeRequestRef = changeRequestRef;
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

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }

    public Map<String, Object> getExtraAttributes() {
        return extraAttributes;
    }

    public void setExtraAttributes(Map<String, Object> extraAttributes) {
        this.extraAttributes = extraAttributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extraAttributes);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<AuditEntryTarget> getTargets() {
        return targets;
    }

    public List<AuditEntryDetail> getDetails() {
        return details;
    }

    public void addTarget(AuditEntryTarget target) {
        if (target == null) {
            return;
        }
        target.setEntry(this);
        targets.add(target);
    }

    public void addDetail(AuditEntryDetail detail) {
        if (detail == null) {
            return;
        }
        detail.setEntry(this);
        details.add(detail);
    }
}
