package com.yuzhi.dts.platform.domain.governance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "gov_indicator_subscription")
public class GovIndicatorSubscription implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "indicator_id", nullable = false, columnDefinition = "uuid")
    private UUID indicatorId;

    @Column(name = "user_login", length = 64, nullable = false)
    private String userLogin;

    @Column(name = "filter_config", columnDefinition = "jsonb")
    private String filterConfig;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "created_date")
    private Instant createdDate;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getIndicatorId() {
        return indicatorId;
    }

    public void setIndicatorId(UUID indicatorId) {
        this.indicatorId = indicatorId;
    }

    public String getUserLogin() {
        return userLogin;
    }

    public void setUserLogin(String userLogin) {
        this.userLogin = userLogin;
    }

    public String getFilterConfig() {
        return filterConfig;
    }

    public void setFilterConfig(String filterConfig) {
        this.filterConfig = filterConfig;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }
}
