package com.yuzhi.dts.platform.domain.modeling;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "modeling_glossary_term_review")
public class ModelingGlossaryTermReview extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id", nullable = false)
    private ModelingGlossaryTerm term;

    @Column(name = "version", length = 32)
    private String version;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "reviewer", length = 64)
    private String reviewer;

    @Column(name = "review_notes", columnDefinition = "text")
    private String reviewNotes;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ModelingGlossaryTerm getTerm() {
        return term;
    }

    public void setTerm(ModelingGlossaryTerm term) {
        this.term = term;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReviewer() {
        return reviewer;
    }

    public void setReviewer(String reviewer) {
        this.reviewer = reviewer;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}

