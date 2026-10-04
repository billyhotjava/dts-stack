package com.yuzhi.dts.platform.domain.modeling;

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

/** 标准包 apply 阶段逐条写入前的 before-image，rollback 逆序消费。 */
@Entity
@Table(name = "std_pkg_import_run_item")
public class StandardPackageImportRunItem extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "run_id", columnDefinition = "uuid", nullable = false)
    private UUID runId;

    @Column(name = "seq", nullable = false)
    private Integer seq;

    @Column(name = "entity_type", length = 32, nullable = false)
    private String entityType;

    @Column(name = "entity_id", length = 128, nullable = false)
    private String entityId;

    @Column(name = "action", length = 16, nullable = false)
    private String action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_json", columnDefinition = "jsonb")
    private String beforeJson;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getRunId() {
        return runId;
    }

    public void setRunId(UUID runId) {
        this.runId = runId;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getBeforeJson() {
        return beforeJson;
    }

    public void setBeforeJson(String beforeJson) {
        this.beforeJson = beforeJson;
    }
}
