package com.yuzhi.dts.platform.domain.catalog;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "catalog_classification_mapping")
public class CatalogClassificationMapping extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "source", length = 64)
    private String source;

    @Column(name = "source_level", length = 32)
    private String sourceLevel;

    @Column(name = "platform_level", length = 32)
    private String platformLevel;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSourceLevel() {
        return sourceLevel;
    }

    public void setSourceLevel(String sourceLevel) {
        this.sourceLevel = sourceLevel;
    }

    public String getPlatformLevel() {
        return platformLevel;
    }

    public void setPlatformLevel(String platformLevel) {
        this.platformLevel = platformLevel;
    }
}

