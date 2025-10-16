package com.yuzhi.dts.platform.domain.catalog;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "catalog_column_schema")
public class CatalogColumnSchema extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id")
    private CatalogTableSchema table;

    @NotBlank
    @Column(name = "name", length = 128)
    private String name;

    @NotBlank
    @Column(name = "data_type", length = 64)
    private String dataType;

    @Column(name = "nullable")
    private Boolean nullable = Boolean.TRUE;

    @Column(name = "tags", length = 1024)
    private String tags;

    // Sensitive label(s), e.g. PII:phone
    @Column(name = "sensitive_tags", length = 1024)
    private String sensitiveTags;

    @Column(name = "comment", length = 1024)
    private String comment;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public CatalogTableSchema getTable() {
        return table;
    }

    public void setTable(CatalogTableSchema table) {
        this.table = table;
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

    public Boolean getNullable() {
        return nullable;
    }

    public void setNullable(Boolean nullable) {
        this.nullable = nullable;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getSensitiveTags() {
        return sensitiveTags;
    }

    public void setSensitiveTags(String sensitiveTags) {
        this.sensitiveTags = sensitiveTags;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
