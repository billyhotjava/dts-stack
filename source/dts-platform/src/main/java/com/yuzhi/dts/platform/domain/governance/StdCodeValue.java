package com.yuzhi.dts.platform.domain.governance;

import com.yuzhi.dts.platform.domain.AbstractAuditingEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;

@Entity
@Table(name = "std_code_value")
public class StdCodeValue extends AbstractAuditingEntity<Long> implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "code_type_id", length = 64, nullable = false)
    private String codeTypeId;

    @Column(name = "code_value", length = 128, nullable = false)
    private String codeValue;

    @Column(name = "code_name", length = 256, nullable = false)
    private String codeName;

    @Column(name = "description", length = 1024)
    private String description;

    @Column(name = "sort_num")
    private Integer sortNum;

    @Column(name = "parent_code", length = 128)
    private String parentCode;

    @Column(name = "is_default")
    private Boolean isDefault;

    @Override
    public Long getId() {
        return itemId;
    }

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getCodeTypeId() {
        return codeTypeId;
    }

    public void setCodeTypeId(String codeTypeId) {
        this.codeTypeId = codeTypeId;
    }

    public String getCodeValue() {
        return codeValue;
    }

    public void setCodeValue(String codeValue) {
        this.codeValue = codeValue;
    }

    public String getCodeName() {
        return codeName;
    }

    public void setCodeName(String codeName) {
        this.codeName = codeName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getSortNum() {
        return sortNum;
    }

    public void setSortNum(Integer sortNum) {
        this.sortNum = sortNum;
    }

    public String getParentCode() {
        return parentCode;
    }

    public void setParentCode(String parentCode) {
        this.parentCode = parentCode;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }
}
