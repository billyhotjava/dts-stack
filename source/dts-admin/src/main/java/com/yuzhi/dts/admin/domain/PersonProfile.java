package com.yuzhi.dts.admin.domain;

import com.yuzhi.dts.admin.domain.enumeration.PersonLifecycleStatus;
import com.yuzhi.dts.admin.domain.enumeration.PersonSourceType;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "person_profile")
public class PersonProfile extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @Column(name = "person_code", nullable = false, unique = true, length = 64)
    private String personCode;

    @Column(name = "external_id", length = 128)
    private String externalId;

    @Column(name = "account", length = 128)
    private String account;

    @Column(name = "full_name", length = 128)
    private String fullName;

    @Column(name = "national_id", length = 64)
    private String nationalId;

    @Column(name = "dept_code", length = 64)
    private String deptCode;

    @Column(name = "dept_name", length = 256)
    private String deptName;

    @Column(name = "dept_path", length = 512)
    private String deptPath;

    @Column(name = "title", length = 128)
    private String title;

    @Column(name = "grade", length = 64)
    private String grade;

    @Column(name = "email", length = 128)
    private String email;

    @Column(name = "phone", length = 64)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", length = 32)
    private PersonLifecycleStatus lifecycleStatus = PersonLifecycleStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_source_type", length = 32)
    private PersonSourceType lastSourceType;

    @Column(name = "last_reference", length = 128)
    private String lastReference;

    @Column(name = "last_batch_id")
    private Long lastBatchId;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "active_from")
    private Instant activeFrom;

    @Column(name = "active_to")
    private Instant activeTo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes", columnDefinition = "jsonb")
    private Map<String, Object> attributes = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private Map<String, Object> rawPayload = new LinkedHashMap<>();

    @Override
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPersonCode() {
        return personCode;
    }

    public void setPersonCode(String personCode) {
        this.personCode = personCode;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getNationalId() {
        return nationalId;
    }

    public void setNationalId(String nationalId) {
        this.nationalId = nationalId;
    }

    public String getDeptCode() {
        return deptCode;
    }

    public void setDeptCode(String deptCode) {
        this.deptCode = deptCode;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public String getDeptPath() {
        return deptPath;
    }

    public void setDeptPath(String deptPath) {
        this.deptPath = deptPath;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public PersonLifecycleStatus getLifecycleStatus() {
        return lifecycleStatus;
    }

    public void setLifecycleStatus(PersonLifecycleStatus lifecycleStatus) {
        this.lifecycleStatus = lifecycleStatus;
    }

    public PersonSourceType getLastSourceType() {
        return lastSourceType;
    }

    public void setLastSourceType(PersonSourceType lastSourceType) {
        this.lastSourceType = lastSourceType;
    }

    public String getLastReference() {
        return lastReference;
    }

    public void setLastReference(String lastReference) {
        this.lastReference = lastReference;
    }

    public Long getLastBatchId() {
        return lastBatchId;
    }

    public void setLastBatchId(Long lastBatchId) {
        this.lastBatchId = lastBatchId;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public Instant getActiveFrom() {
        return activeFrom;
    }

    public void setActiveFrom(Instant activeFrom) {
        this.activeFrom = activeFrom;
    }

    public Instant getActiveTo() {
        return activeTo;
    }

    public void setActiveTo(Instant activeTo) {
        this.activeTo = activeTo;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
    }

    public Map<String, Object> getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(Map<String, Object> rawPayload) {
        this.rawPayload = rawPayload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(rawPayload);
    }
}
