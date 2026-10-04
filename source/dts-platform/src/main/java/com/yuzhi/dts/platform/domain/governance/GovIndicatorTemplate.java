package com.yuzhi.dts.platform.domain.governance;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "gov_indicator_template")
public class GovIndicatorTemplate extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "code", length = 64, unique = true, nullable = false)
    private String code;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "domain", length = 32, nullable = false)
    private String domain;

    @Column(name = "indicator_blueprints", columnDefinition = "jsonb", nullable = false)
    private String indicatorBlueprints;

    @Column(name = "required_source_fields", columnDefinition = "jsonb")
    private String requiredSourceFields;

    @Column(name = "seed_tables", columnDefinition = "jsonb")
    private String seedTables;

    @Column(name = "recommended_snapshot")
    private Boolean recommendedSnapshot;

    @Column(name = "builtin", nullable = false)
    private Boolean builtin = Boolean.FALSE;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getIndicatorBlueprints() {
        return indicatorBlueprints;
    }

    public void setIndicatorBlueprints(String indicatorBlueprints) {
        this.indicatorBlueprints = indicatorBlueprints;
    }

    public String getRequiredSourceFields() {
        return requiredSourceFields;
    }

    public void setRequiredSourceFields(String requiredSourceFields) {
        this.requiredSourceFields = requiredSourceFields;
    }

    public String getSeedTables() {
        return seedTables;
    }

    public void setSeedTables(String seedTables) {
        this.seedTables = seedTables;
    }

    public Boolean getRecommendedSnapshot() {
        return recommendedSnapshot;
    }

    public void setRecommendedSnapshot(Boolean recommendedSnapshot) {
        this.recommendedSnapshot = recommendedSnapshot;
    }

    public Boolean getBuiltin() {
        return builtin;
    }

    public void setBuiltin(Boolean builtin) {
        this.builtin = builtin;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }
}
