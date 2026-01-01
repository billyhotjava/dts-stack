package com.yuzhi.dts.platform.domain.modeling;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "modeling_template")
public class ModelingTemplate extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "name", length = 256, nullable = false)
    private String name;

    @Column(name = "layer", length = 16)
    private String layer;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "version", length = 32)
    private String version;

    @Column(name = "version_notes", length = 512)
    private String versionNotes;

    @Column(name = "naming_rule")
    private String namingRule;

    @Column(name = "fields_template")
    private String fieldsTemplate;

    @Column(name = "review_checklist")
    private String reviewChecklist;

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

    public String getLayer() {
        return layer;
    }

    public void setLayer(String layer) {
        this.layer = layer;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersionNotes() {
        return versionNotes;
    }

    public void setVersionNotes(String versionNotes) {
        this.versionNotes = versionNotes;
    }

    public String getNamingRule() {
        return namingRule;
    }

    public void setNamingRule(String namingRule) {
        this.namingRule = namingRule;
    }

    public String getFieldsTemplate() {
        return fieldsTemplate;
    }

    public void setFieldsTemplate(String fieldsTemplate) {
        this.fieldsTemplate = fieldsTemplate;
    }

    public String getReviewChecklist() {
        return reviewChecklist;
    }

    public void setReviewChecklist(String reviewChecklist) {
        this.reviewChecklist = reviewChecklist;
    }
}
