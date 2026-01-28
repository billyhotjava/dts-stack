package com.yuzhi.dts.platform.service.governance.dto;

import java.time.Instant;

public class ReferenceCodeDirectoryDto {
    private String codeTypeId;
    private String codeTypeCode;
    private String codeTypeName;
    private String stdLevel;
    private String bizCatalog;
    private String dataType;
    private Integer status;
    private String ownerDept;
    private String version;
    private Instant createdDate;
    private Instant lastModifiedDate;
    private Long itemCount;

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

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public Instant getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(Instant lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }

    public Long getItemCount() {
        return itemCount;
    }

    public void setItemCount(Long itemCount) {
        this.itemCount = itemCount;
    }
}
