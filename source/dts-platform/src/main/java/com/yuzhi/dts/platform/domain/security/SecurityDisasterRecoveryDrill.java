package com.yuzhi.dts.platform.domain.security;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "security_dr_drill")
public class SecurityDisasterRecoveryDrill extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "drill_date", nullable = false)
    private LocalDate drillDate;

    @Column(name = "scenario", length = 256, nullable = false)
    private String scenario;

    @Column(name = "target_key", length = 64)
    private String targetKey;

    @Column(name = "result", length = 32, nullable = false)
    private String result;

    @Column(name = "rpo_minutes")
    private Integer rpoMinutes;

    @Column(name = "rto_minutes")
    private Integer rtoMinutes;

    @Column(name = "summary", length = 1024)
    private String summary;

    @Column(name = "evidence_uri", length = 512)
    private String evidenceUri;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details_json", columnDefinition = "jsonb")
    private String detailsJson;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDate getDrillDate() {
        return drillDate;
    }

    public void setDrillDate(LocalDate drillDate) {
        this.drillDate = drillDate;
    }

    public String getScenario() {
        return scenario;
    }

    public void setScenario(String scenario) {
        this.scenario = scenario;
    }

    public String getTargetKey() {
        return targetKey;
    }

    public void setTargetKey(String targetKey) {
        this.targetKey = targetKey;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public Integer getRpoMinutes() {
        return rpoMinutes;
    }

    public void setRpoMinutes(Integer rpoMinutes) {
        this.rpoMinutes = rpoMinutes;
    }

    public Integer getRtoMinutes() {
        return rtoMinutes;
    }

    public void setRtoMinutes(Integer rtoMinutes) {
        this.rtoMinutes = rtoMinutes;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getEvidenceUri() {
        return evidenceUri;
    }

    public void setEvidenceUri(String evidenceUri) {
        this.evidenceUri = evidenceUri;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public void setDetailsJson(String detailsJson) {
        this.detailsJson = detailsJson;
    }
}

