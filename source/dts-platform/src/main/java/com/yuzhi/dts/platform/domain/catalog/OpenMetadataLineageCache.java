package com.yuzhi.dts.platform.domain.catalog;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "om_lineage_cache")
public class OpenMetadataLineageCache extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "from_om_entity_id", length = 128)
    private String fromOmEntityId;

    @Column(name = "to_om_entity_id", length = 128)
    private String toOmEntityId;

    @Column(name = "from_fqn", length = 1024)
    private String fromFqn;

    @Column(name = "to_fqn", length = 1024)
    private String toFqn;

    @Column(name = "edge_type", length = 32)
    private String edgeType = "TABLE";

    @Column(name = "source", length = 64)
    private String source = "openmetadata";

    @Column(name = "raw_json", columnDefinition = "text")
    private String rawJson;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFromOmEntityId() {
        return fromOmEntityId;
    }

    public void setFromOmEntityId(String fromOmEntityId) {
        this.fromOmEntityId = fromOmEntityId;
    }

    public String getToOmEntityId() {
        return toOmEntityId;
    }

    public void setToOmEntityId(String toOmEntityId) {
        this.toOmEntityId = toOmEntityId;
    }

    public String getFromFqn() {
        return fromFqn;
    }

    public void setFromFqn(String fromFqn) {
        this.fromFqn = fromFqn;
    }

    public String getToFqn() {
        return toFqn;
    }

    public void setToFqn(String toFqn) {
        this.toFqn = toFqn;
    }

    public String getEdgeType() {
        return edgeType;
    }

    public void setEdgeType(String edgeType) {
        this.edgeType = edgeType;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }
}
