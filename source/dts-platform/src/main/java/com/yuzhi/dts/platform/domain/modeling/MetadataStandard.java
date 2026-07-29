package com.yuzhi.dts.platform.domain.modeling;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(
    name = "metadata_standard",
    uniqueConstraints = { @UniqueConstraint(name = "uk_metadata_standard_key", columnNames = { "field_name_en", "domain" }) }
)
public class MetadataStandard extends AbstractAuditingEntity<UUID> implements Serializable {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "field_name_cn", length = 128, nullable = false)
    private String fieldNameCn;

    @Column(name = "field_name_en", length = 128, nullable = false)
    private String fieldNameEn;

    @Column(name = "data_type", length = 64, nullable = false)
    private String dataType;

    @Column(name = "data_length")
    private Integer dataLength;

    @Column(name = "data_precision")
    private Integer dataPrecision;

    @Column(name = "data_scale")
    private Integer dataScale;

    @Column(name = "nullable", nullable = false)
    private Boolean nullable = Boolean.TRUE;

    @Column(name = "domain", length = 64, nullable = false)
    private String domain;

    @Column(name = "description", length = 2048, nullable = false)
    private String description;

    @Column(name = "source_system", length = 64, nullable = true)
    private String sourceSystem;

    @Column(name = "code_set", length = 128)
    private String codeSet;

    @Column(name = "default_value", length = 256)
    private String defaultValue;

    @Column(name = "is_pk")
    private Boolean isPk;

    @Enumerated(EnumType.STRING)
    @Column(name = "security_level", length = 32, nullable = false)
    private DataSecurityLevel securityLevel = DataSecurityLevel.INTERNAL;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Override
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFieldNameCn() {
        return fieldNameCn;
    }

    public void setFieldNameCn(String fieldNameCn) {
        this.fieldNameCn = fieldNameCn;
    }

    public String getFieldNameEn() {
        return fieldNameEn;
    }

    public void setFieldNameEn(String fieldNameEn) {
        this.fieldNameEn = fieldNameEn;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public Integer getDataLength() {
        return dataLength;
    }

    public void setDataLength(Integer dataLength) {
        this.dataLength = dataLength;
    }

    public Integer getDataPrecision() {
        return dataPrecision;
    }

    public void setDataPrecision(Integer dataPrecision) {
        this.dataPrecision = dataPrecision;
    }

    public Integer getDataScale() {
        return dataScale;
    }

    public void setDataScale(Integer dataScale) {
        this.dataScale = dataScale;
    }

    public Boolean getNullable() {
        return nullable;
    }

    public void setNullable(Boolean nullable) {
        this.nullable = nullable;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getCodeSet() {
        return codeSet;
    }

    public void setCodeSet(String codeSet) {
        this.codeSet = codeSet;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public Boolean getIsPk() {
        return isPk;
    }

    public void setIsPk(Boolean isPk) {
        this.isPk = isPk;
    }

    public DataSecurityLevel getSecurityLevel() {
        return securityLevel;
    }

    public void setSecurityLevel(DataSecurityLevel securityLevel) {
        this.securityLevel = securityLevel;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
