package com.yuzhi.dts.platform.domain.service;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "svc_api_metric_hourly")
public class SvcApiMetricHourly extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "api_id", columnDefinition = "uuid", nullable = false)
    private UUID apiId;

    @Column(name = "bucket_start", nullable = false)
    private Instant bucketStart;

    @Column(name = "call_count")
    private Long callCount;

    @Column(name = "qps_peak")
    private Integer qpsPeak;

    @Column(name = "masked_hits")
    private Integer maskedHits;

    @Column(name = "deny_count")
    private Integer denyCount;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getApiId() {
        return apiId;
    }

    public void setApiId(UUID apiId) {
        this.apiId = apiId;
    }

    public Instant getBucketStart() {
        return bucketStart;
    }

    public void setBucketStart(Instant bucketStart) {
        this.bucketStart = bucketStart;
    }

    public Long getCallCount() {
        return callCount;
    }

    public void setCallCount(Long callCount) {
        this.callCount = callCount;
    }

    public Integer getQpsPeak() {
        return qpsPeak;
    }

    public void setQpsPeak(Integer qpsPeak) {
        this.qpsPeak = qpsPeak;
    }

    public Integer getMaskedHits() {
        return maskedHits;
    }

    public void setMaskedHits(Integer maskedHits) {
        this.maskedHits = maskedHits;
    }

    public Integer getDenyCount() {
        return denyCount;
    }

    public void setDenyCount(Integer denyCount) {
        this.denyCount = denyCount;
    }
}
