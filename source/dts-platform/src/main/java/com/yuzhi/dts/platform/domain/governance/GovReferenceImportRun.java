package com.yuzhi.dts.platform.domain.governance;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "gov_reference_import_run")
public class GovReferenceImportRun extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "code_type_id", length = 64, nullable = false)
    private String codeTypeId;

    @Column(name = "import_mode", length = 32, nullable = false)
    private String importMode;

    @Column(name = "conflict_policy", length = 32, nullable = false)
    private String conflictPolicy;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "summary", length = 1024)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preview_json", columnDefinition = "jsonb")
    private String previewJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_snapshot_json", columnDefinition = "jsonb")
    private String beforeSnapshotJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_snapshot_json", columnDefinition = "jsonb")
    private String afterSnapshotJson;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCodeTypeId() {
        return codeTypeId;
    }

    public void setCodeTypeId(String codeTypeId) {
        this.codeTypeId = codeTypeId;
    }

    public String getImportMode() {
        return importMode;
    }

    public void setImportMode(String importMode) {
        this.importMode = importMode;
    }

    public String getConflictPolicy() {
        return conflictPolicy;
    }

    public void setConflictPolicy(String conflictPolicy) {
        this.conflictPolicy = conflictPolicy;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getPreviewJson() {
        return previewJson;
    }

    public void setPreviewJson(String previewJson) {
        this.previewJson = previewJson;
    }

    public String getBeforeSnapshotJson() {
        return beforeSnapshotJson;
    }

    public void setBeforeSnapshotJson(String beforeSnapshotJson) {
        this.beforeSnapshotJson = beforeSnapshotJson;
    }

    public String getAfterSnapshotJson() {
        return afterSnapshotJson;
    }

    public void setAfterSnapshotJson(String afterSnapshotJson) {
        this.afterSnapshotJson = afterSnapshotJson;
    }
}
