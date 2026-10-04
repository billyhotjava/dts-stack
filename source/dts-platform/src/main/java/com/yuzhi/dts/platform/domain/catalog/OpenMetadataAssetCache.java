package com.yuzhi.dts.platform.domain.catalog;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "om_asset_cache")
public class OpenMetadataAssetCache extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "om_entity_id", length = 128)
    private String omEntityId;

    @NotBlank
    @Column(name = "fqn", length = 1024, nullable = false)
    private String fqn;

    @Column(name = "source_type", length = 32)
    private String sourceType = "TABLE";

    @Column(name = "service_name", length = 128)
    private String serviceName;

    @Column(name = "database_name", length = 128)
    private String databaseName;

    @Column(name = "schema_name", length = 128)
    private String schemaName;

    @Column(name = "table_name", length = 256)
    private String tableName;

    @Column(name = "display_name", length = 256)
    private String displayName;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "owner_name", length = 128)
    private String ownerName;

    @Column(name = "domain_name", length = 128)
    private String domainName;

    @Column(name = "tags_json", columnDefinition = "text")
    private String tagsJson;

    @Column(name = "profile_json", columnDefinition = "text")
    private String profileJson;

    @Column(name = "raw_json", columnDefinition = "text")
    private String rawJson;

    @Column(name = "column_count")
    private Integer columnCount;

    @Column(name = "sync_status", length = 32, nullable = false)
    private String syncStatus = "SYNCED";

    @Column(name = "sync_message", length = 512)
    private String syncMessage;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getOmEntityId() {
        return omEntityId;
    }

    public void setOmEntityId(String omEntityId) {
        this.omEntityId = omEntityId;
    }

    public String getFqn() {
        return fqn;
    }

    public void setFqn(String fqn) {
        this.fqn = fqn;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
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

    public Integer getColumnCount() {
        return columnCount;
    }

    public void setColumnCount(Integer columnCount) {
        this.columnCount = columnCount;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public String getSyncMessage() {
        return syncMessage;
    }

    public void setSyncMessage(String syncMessage) {
        this.syncMessage = syncMessage;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }
}
