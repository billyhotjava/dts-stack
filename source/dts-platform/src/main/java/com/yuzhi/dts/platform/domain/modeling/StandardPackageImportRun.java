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

@Entity
@Table(name = "std_pkg_import_run")
public class StandardPackageImportRun extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "package_name", length = 256, nullable = false)
    private String packageName;

    @Column(name = "source", length = 32, nullable = false)
    private String source;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "summary", length = 1024)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preview_json", columnDefinition = "jsonb")
    private String previewJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", columnDefinition = "jsonb")
    private String payloadJson;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
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

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }
}
