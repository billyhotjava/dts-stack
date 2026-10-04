package com.yuzhi.dts.copilot.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "analysis_draft")
public class AnalyticsAnalysisDraft implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "entity_id", nullable = false, length = 32, unique = true)
    private String entityId;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "message_id", length = 64)
    private String messageId;

    @Column(name = "question", nullable = false, columnDefinition = "text")
    private String question;

    @Column(name = "database_id", nullable = false)
    private Long databaseId;

    @Column(name = "sql_text", nullable = false, columnDefinition = "text")
    private String sqlText;

    @Column(name = "explanation_text", columnDefinition = "text")
    private String explanationText;

    @Column(name = "suggested_display", length = 64)
    private String suggestedDisplay;

    @Column(name = "response_kind", length = 64)
    private String responseKind;

    @Column(name = "data_surface", length = 64)
    private String dataSurface;

    @Column(name = "quality_level", length = 32)
    private String qualityLevel;

    @Column(name = "quality_notes", columnDefinition = "text")
    private String qualityNotes;

    @Column(name = "report_code", length = 128)
    private String reportCode;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "linked_card_id")
    private Long linkedCardId;

    @Column(name = "linked_dashboard_id")
    private Long linkedDashboardId;

    @Column(name = "linked_screen_id")
    private Long linkedScreenId;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public Long getDatabaseId() {
        return databaseId;
    }

    public void setDatabaseId(Long databaseId) {
        this.databaseId = databaseId;
    }

    public String getSqlText() {
        return sqlText;
    }

    public void setSqlText(String sqlText) {
        this.sqlText = sqlText;
    }

    public String getExplanationText() {
        return explanationText;
    }

    public void setExplanationText(String explanationText) {
        this.explanationText = explanationText;
    }

    public String getSuggestedDisplay() {
        return suggestedDisplay;
    }

    public void setSuggestedDisplay(String suggestedDisplay) {
        this.suggestedDisplay = suggestedDisplay;
    }

    public String getResponseKind() {
        return responseKind;
    }

    public void setResponseKind(String responseKind) {
        this.responseKind = responseKind;
    }

    public String getDataSurface() {
        return dataSurface;
    }

    public void setDataSurface(String dataSurface) {
        this.dataSurface = dataSurface;
    }

    public String getQualityLevel() {
        return qualityLevel;
    }

    public void setQualityLevel(String qualityLevel) {
        this.qualityLevel = qualityLevel;
    }

    public String getQualityNotes() {
        return qualityNotes;
    }

    public void setQualityNotes(String qualityNotes) {
        this.qualityNotes = qualityNotes;
    }

    public String getReportCode() {
        return reportCode;
    }

    public void setReportCode(String reportCode) {
        this.reportCode = reportCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getLinkedCardId() {
        return linkedCardId;
    }

    public void setLinkedCardId(Long linkedCardId) {
        this.linkedCardId = linkedCardId;
    }

    public Long getLinkedDashboardId() {
        return linkedDashboardId;
    }

    public void setLinkedDashboardId(Long linkedDashboardId) {
        this.linkedDashboardId = linkedDashboardId;
    }

    public Long getLinkedScreenId() {
        return linkedScreenId;
    }

    public void setLinkedScreenId(Long linkedScreenId) {
        this.linkedScreenId = linkedScreenId;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
