package com.yuzhi.dts.platform.domain.governance;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;

@Entity
@Table(name = "std_code_directory")
public class StdCodeDirectory extends AbstractAuditingEntity<String> implements Serializable {

    @Id
    @Column(name = "code_type_id", length = 64)
    private String codeTypeId;

    @Column(name = "code_type_code", length = 64, nullable = false, unique = true)
    private String codeTypeCode;

    @Column(name = "code_type_name", length = 128, nullable = false)
    private String codeTypeName;

    @Column(name = "std_level", length = 64)
    private String stdLevel;

    @Column(name = "biz_catalog", length = 128)
    private String bizCatalog;

    @Column(name = "data_type", length = 64)
    private String dataType;

    @Column(name = "status")
    private Integer status;

    @Column(name = "owner_dept", length = 64)
    private String ownerDept;

    @Column(name = "version", length = 32)
    private String version;

    @Override
    public String getId() {
        return codeTypeId;
    }

    public String getCodeTypeId() {
        return codeTypeId;
    }

    public void setCodeTypeId(String codeTypeId) {
        this.codeTypeId = codeTypeId;
    }

    public String getCodeTypeCode() {
        return codeTypeCode;
    }

    public void setCodeTypeCode(String codeTypeCode) {
        this.codeTypeCode = codeTypeCode;
    }

    public String getCodeTypeName() {
        return codeTypeName;
    }

    public void setCodeTypeName(String codeTypeName) {
        this.codeTypeName = codeTypeName;
    }

    public String getStdLevel() {
        return stdLevel;
    }

    public void setStdLevel(String stdLevel) {
        this.stdLevel = stdLevel;
    }

    public String getBizCatalog() {
        return bizCatalog;
    }

    public void setBizCatalog(String bizCatalog) {
        this.bizCatalog = bizCatalog;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getOwnerDept() {
        return ownerDept;
    }

    public void setOwnerDept(String ownerDept) {
        this.ownerDept = ownerDept;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
