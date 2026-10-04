package com.yuzhi.dts.admin.domain;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "system_config")
public class SystemConfig extends AbstractAuditingEntity<Long> implements Serializable {

    public enum Category {
        FEATURE_TOGGLE,
        SECURITY,
        DATABASE,
        INTEGRATION,
        SYSTEM
    }

    public enum DataType {
        STRING,
        BOOLEAN,
        INTEGER,
        JSON
    }

    public enum ConfigScope {
        RUNTIME,
        RUNTIME_RESTART,
        BOOTSTRAP
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @Column(name = "cfg_key", nullable = false, unique = true)
    private String key;

    @Column(name = "cfg_value")
    private String value;

    @Column(name = "description")
    private String description;

    @Column(name = "category")
    @Enumerated(EnumType.STRING)
    private Category category = Category.SYSTEM;

    @Column(name = "sensitive", nullable = false)
    private boolean sensitive = false;

    @Column(name = "data_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private DataType dataType = DataType.STRING;

    @Column(name = "editable", nullable = false)
    private boolean editable = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "config_scope", nullable = false)
    @Enumerated(EnumType.STRING)
    private ConfigScope configScope = ConfigScope.RUNTIME;

    @Column(name = "restart_required", nullable = false)
    private boolean restartRequired = false;

    @Column(name = "validation_rule")
    private String validationRule;

    @Column(name = "owner")
    private String owner;

    @Override
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public boolean isSensitive() { return sensitive; }
    public void setSensitive(boolean sensitive) { this.sensitive = sensitive; }
    public DataType getDataType() { return dataType; }
    public void setDataType(DataType dataType) { this.dataType = dataType; }
    public boolean isEditable() { return editable; }
    public void setEditable(boolean editable) { this.editable = editable; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public ConfigScope getConfigScope() { return configScope; }
    public void setConfigScope(ConfigScope configScope) { this.configScope = configScope; }
    public boolean isRestartRequired() { return restartRequired; }
    public void setRestartRequired(boolean restartRequired) { this.restartRequired = restartRequired; }
    public String getValidationRule() { return validationRule; }
    public void setValidationRule(String validationRule) { this.validationRule = validationRule; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
}
