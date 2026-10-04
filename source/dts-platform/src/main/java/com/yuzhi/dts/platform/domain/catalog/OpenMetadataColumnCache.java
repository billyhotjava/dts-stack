package com.yuzhi.dts.platform.domain.catalog;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "om_column_cache")
public class OpenMetadataColumnCache extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private OpenMetadataAssetCache asset;

    @Column(name = "om_column_fqn", length = 1024)
    private String omColumnFqn;

    @NotBlank
    @Column(name = "name", length = 256, nullable = false)
    private String name;

    @Column(name = "data_type", length = 128)
    private String dataType;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "ordinal_position")
    private Integer ordinalPosition;

    @Column(name = "tags_json", columnDefinition = "text")
    private String tagsJson;

    @Column(name = "profile_json", columnDefinition = "text")
    private String profileJson;

    @Column(name = "raw_json", columnDefinition = "text")
    private String rawJson;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public OpenMetadataAssetCache getAsset() {
        return asset;
    }

    public void setAsset(OpenMetadataAssetCache asset) {
        this.asset = asset;
    }

    public String getOmColumnFqn() {
        return omColumnFqn;
    }

    public void setOmColumnFqn(String omColumnFqn) {
        this.omColumnFqn = omColumnFqn;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getOrdinalPosition() {
        return ordinalPosition;
    }

    public void setOrdinalPosition(Integer ordinalPosition) {
        this.ordinalPosition = ordinalPosition;
    }

    public String getTagsJson() {
        return tagsJson;
    }

    public void setTagsJson(String tagsJson) {
        this.tagsJson = tagsJson;
    }

    public String getProfileJson() {
        return profileJson;
    }

    public void setProfileJson(String profileJson) {
        this.profileJson = profileJson;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }
}
