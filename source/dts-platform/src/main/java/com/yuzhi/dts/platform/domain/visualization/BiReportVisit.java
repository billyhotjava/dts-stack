package com.yuzhi.dts.platform.domain.visualization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Per-visit log for BI report links. Populated by
 * {@code BiReportLinkService#touchVisit(...)} so that leader-overview
 * aggregations (Sprint-15 F1) can compute visits-in-period KPIs, top-N
 * reports by visit count, and the domain matrix.
 *
 * <p>Intentionally a lightweight entity without full auditing metadata —
 * we only need {@code visitedAt} and the denormalised tags (userLogin /
 * deptCode / bizDomain) captured at write time.
 */
@Entity
@Table(name = "bi_report_visit")
public class BiReportVisit implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "report_id", columnDefinition = "uuid", nullable = false)
    private UUID reportId;

    @Column(name = "user_login", length = 128)
    private String userLogin;

    @Column(name = "dept_code", length = 64)
    private String deptCode;

    @Column(name = "biz_domain", length = 64)
    private String bizDomain;

    @Column(name = "visited_at", nullable = false)
    private Instant visitedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getReportId() {
        return reportId;
    }

    public void setReportId(UUID reportId) {
        this.reportId = reportId;
    }

    public String getUserLogin() {
        return userLogin;
    }

    public void setUserLogin(String userLogin) {
        this.userLogin = userLogin;
    }

    public String getDeptCode() {
        return deptCode;
    }

    public void setDeptCode(String deptCode) {
        this.deptCode = deptCode;
    }

    public String getBizDomain() {
        return bizDomain;
    }

    public void setBizDomain(String bizDomain) {
        this.bizDomain = bizDomain;
    }

    public Instant getVisitedAt() {
        return visitedAt;
    }

    public void setVisitedAt(Instant visitedAt) {
        this.visitedAt = visitedAt;
    }
}
