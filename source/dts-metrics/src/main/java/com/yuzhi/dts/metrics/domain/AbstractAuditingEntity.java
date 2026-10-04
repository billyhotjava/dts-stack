package com.yuzhi.dts.metrics.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.io.Serializable;
import java.time.Instant;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Base class holding audit columns (created/last-modified by + date) for dts-metrics entities.
 *
 * <p>Mirrors {@code com.yuzhi.dts.platform.domain.AbstractAuditingEntity} but is metrics-local: it
 * must not depend on platform's {@code SecurityUtils}. dts-metrics has no JHipster security wiring, so
 * the {@code @PrePersist}/{@code @PreUpdate} fallbacks default the principal to {@code "system"} —
 * matching the sibling fallback and keeping {@code created_by NOT NULL} satisfiable without auth.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties(value = { "createdBy", "createdDate", "lastModifiedBy", "lastModifiedDate" }, allowGetters = true)
public abstract class AbstractAuditingEntity<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String SYSTEM_PRINCIPAL = "system";

    public abstract T getId();

    @CreatedBy
    @Column(name = "created_by", nullable = false, length = 50, updatable = false)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private Instant createdDate = Instant.now();

    @LastModifiedBy
    @Column(name = "last_modified_by", length = 50)
    private String lastModifiedBy;

    @LastModifiedDate
    @Column(name = "last_modified_date")
    private Instant lastModifiedDate = Instant.now();

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

    @PrePersist
    protected void prePersistAuditDefaults() {
        if (this.createdBy == null || this.createdBy.isBlank()) {
            this.createdBy = SYSTEM_PRINCIPAL;
        }
        if (this.lastModifiedBy == null || this.lastModifiedBy.isBlank()) {
            this.lastModifiedBy = this.createdBy;
        }
        if (this.createdDate == null) {
            this.createdDate = Instant.now();
        }
        if (this.lastModifiedDate == null) {
            this.lastModifiedDate = this.createdDate;
        }
    }

    @PreUpdate
    protected void preUpdateAuditDefaults() {
        if (this.lastModifiedBy == null || this.lastModifiedBy.isBlank()) {
            this.lastModifiedBy = SYSTEM_PRINCIPAL;
        }
        if (this.lastModifiedDate == null) {
            this.lastModifiedDate = Instant.now();
        }
    }
}
