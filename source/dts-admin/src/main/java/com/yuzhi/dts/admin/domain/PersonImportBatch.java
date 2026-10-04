package com.yuzhi.dts.admin.domain;

import com.yuzhi.dts.admin.domain.enumeration.PersonImportStatus;
import com.yuzhi.dts.admin.domain.enumeration.PersonSourceType;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "person_import_batch")
public class PersonImportBatch extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private PersonSourceType sourceType = PersonSourceType.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PersonImportStatus status = PersonImportStatus.PENDING;

    @Column(name = "reference", length = 128)
    private String reference;

    @Column(name = "total_records")
    private Integer totalRecords = 0;

    @Column(name = "success_records")
    private Integer successRecords = 0;

    @Column(name = "failure_records")
    private Integer failureRecords = 0;

    @Column(name = "skipped_records")
    private Integer skippedRecords = 0;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun = false;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @Override
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PersonSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(PersonSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public PersonImportStatus getStatus() {
        return status;
    }

    public void setStatus(PersonImportStatus status) {
        this.status = status;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public Integer getTotalRecords() {
        return totalRecords;
    }

    public void setTotalRecords(Integer totalRecords) {
        this.totalRecords = totalRecords;
    }

    public Integer getSuccessRecords() {
        return successRecords;
    }

    public void setSuccessRecords(Integer successRecords) {
        this.successRecords = successRecords;
    }

    public Integer getFailureRecords() {
        return failureRecords;
    }

    public void setFailureRecords(Integer failureRecords) {
        this.failureRecords = failureRecords;
    }

    public Integer getSkippedRecords() {
        return skippedRecords;
    }

    public void setSkippedRecords(Integer skippedRecords) {
        this.skippedRecords = skippedRecords;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
