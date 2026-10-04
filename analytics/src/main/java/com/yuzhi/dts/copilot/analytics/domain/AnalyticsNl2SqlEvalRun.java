package com.yuzhi.dts.copilot.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "analytics_nl2sql_eval_run")
public class AnalyticsNl2SqlEvalRun implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "label", length = 128)
    private String label;

    @Column(name = "model_version", length = 64)
    private String modelVersion;

    @Column(name = "prompt_version", length = 64)
    private String promptVersion;

    @Column(name = "dictionary_version", length = 64)
    private String dictionaryVersion;

    @Column(name = "case_count", nullable = false)
    private Integer caseCount = 0;

    @Column(name = "pass_count", nullable = false)
    private Integer passCount = 0;

    @Column(name = "fail_count", nullable = false)
    private Integer failCount = 0;

    @Column(name = "pass_rate", nullable = false)
    private Double passRate = 0d;

    @Column(name = "average_score", nullable = false)
    private Double averageScore = 0d;

    @Column(name = "blocked_rate", nullable = false)
    private Double blockedRate = 0d;

    @Column(name = "gate_passed")
    private Boolean gatePassed;

    @Column(name = "gate_summary_json", columnDefinition = "text")
    private String gateSummaryJson;

    @Column(name = "summary_json", columnDefinition = "text")
    private String summaryJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getDictionaryVersion() {
        return dictionaryVersion;
    }

    public void setDictionaryVersion(String dictionaryVersion) {
        this.dictionaryVersion = dictionaryVersion;
    }

    public Integer getCaseCount() {
        return caseCount;
    }

    public void setCaseCount(Integer caseCount) {
        this.caseCount = caseCount;
    }

    public Integer getPassCount() {
        return passCount;
    }

    public void setPassCount(Integer passCount) {
        this.passCount = passCount;
    }

    public Integer getFailCount() {
        return failCount;
    }

    public void setFailCount(Integer failCount) {
        this.failCount = failCount;
    }

    public Double getPassRate() {
        return passRate;
    }

    public void setPassRate(Double passRate) {
        this.passRate = passRate;
    }

    public Double getAverageScore() {
        return averageScore;
    }

    public void setAverageScore(Double averageScore) {
        this.averageScore = averageScore;
    }

    public Double getBlockedRate() {
        return blockedRate;
    }

    public void setBlockedRate(Double blockedRate) {
        this.blockedRate = blockedRate;
    }

    public Boolean getGatePassed() {
        return gatePassed;
    }

    public void setGatePassed(Boolean gatePassed) {
        this.gatePassed = gatePassed;
    }

    public String getGateSummaryJson() {
        return gateSummaryJson;
    }

    public void setGateSummaryJson(String gateSummaryJson) {
        this.gateSummaryJson = gateSummaryJson;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public void setSummaryJson(String summaryJson) {
        this.summaryJson = summaryJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
