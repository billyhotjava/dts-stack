package com.yuzhi.dts.ingestion.domain.infra;

import com.yuzhi.dts.ingestion.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "infra_airbyte_connection")
public class InfraAirbyteConnection extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "infra_source_id", columnDefinition = "uuid")
    private UUID infraSourceId;

    @Column(name = "source_definition_id", length = 64)
    private String sourceDefinitionId;

    @Column(name = "source_id", length = 64)
    private String sourceId;

    @Column(name = "destination_id", length = 64)
    private String destinationId;

    @Column(name = "connection_id", length = 64)
    private String connectionId;

    @Column(name = "sync_mode", length = 32)
    private String syncMode;

    @Column(name = "schedule_type", length = 32)
    private String scheduleType;

    @Column(name = "schedule_cron", length = 128)
    private String scheduleCron;

    @Column(name = "namespace", length = 128)
    private String namespace;

    @Column(name = "prefix", length = 128)
    private String prefix;

    @Column(name = "owner", length = 64)
    private String owner;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "last_job_id", length = 64)
    private String lastJobId;

    @Column(name = "last_job_status", length = 32)
    private String lastJobStatus;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "source_config_json", columnDefinition = "text")
    private String sourceConfigJson;

    @Column(name = "destination_config_json", columnDefinition = "text")
    private String destinationConfigJson;

    @Column(name = "schema_strategy", length = 32)
    private String schemaStrategy;

    @Column(name = "reconcile_rule", columnDefinition = "text")
    private String reconcileRule;

    @Column(name = "selected_streams_json", columnDefinition = "text")
    private String selectedStreamsJson;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getInfraSourceId() {
        return infraSourceId;
    }

    public void setInfraSourceId(UUID infraSourceId) {
        this.infraSourceId = infraSourceId;
    }

    public String getSourceDefinitionId() {
        return sourceDefinitionId;
    }

    public void setSourceDefinitionId(String sourceDefinitionId) {
        this.sourceDefinitionId = sourceDefinitionId;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public void setDestinationId(String destinationId) {
        this.destinationId = destinationId;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }

    public String getSyncMode() {
        return syncMode;
    }

    public void setSyncMode(String syncMode) {
        this.syncMode = syncMode;
    }

    public String getScheduleType() {
        return scheduleType;
    }

    public void setScheduleType(String scheduleType) {
        this.scheduleType = scheduleType;
    }

    public String getScheduleCron() {
        return scheduleCron;
    }

    public void setScheduleCron(String scheduleCron) {
        this.scheduleCron = scheduleCron;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLastJobId() {
        return lastJobId;
    }

    public void setLastJobId(String lastJobId) {
        this.lastJobId = lastJobId;
    }

    public String getLastJobStatus() {
        return lastJobStatus;
    }

    public void setLastJobStatus(String lastJobStatus) {
        this.lastJobStatus = lastJobStatus;
    }

    public Instant getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(Instant lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSourceConfigJson() {
        return sourceConfigJson;
    }

    public void setSourceConfigJson(String sourceConfigJson) {
        this.sourceConfigJson = sourceConfigJson;
    }

    public String getDestinationConfigJson() {
        return destinationConfigJson;
    }

    public void setDestinationConfigJson(String destinationConfigJson) {
        this.destinationConfigJson = destinationConfigJson;
    }

    public String getSchemaStrategy() {
        return schemaStrategy;
    }

    public void setSchemaStrategy(String schemaStrategy) {
        this.schemaStrategy = schemaStrategy;
    }

    public String getReconcileRule() {
        return reconcileRule;
    }

    public void setReconcileRule(String reconcileRule) {
        this.reconcileRule = reconcileRule;
    }

    public String getSelectedStreamsJson() {
        return selectedStreamsJson;
    }

    public void setSelectedStreamsJson(String selectedStreamsJson) {
        this.selectedStreamsJson = selectedStreamsJson;
    }
}
