package com.yuzhi.dts.copilot.analytics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "analytics_screen_template")
public class AnalyticsScreenTemplate implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "category", nullable = false, length = 64)
    private String category = "custom";

    @Column(name = "thumbnail", length = 64)
    private String thumbnail;

    @Column(name = "tags_json", columnDefinition = "text")
    private String tagsJson;

    @Column(name = "width", nullable = false)
    private Integer width = 1920;

    @Column(name = "height", nullable = false)
    private Integer height = 1080;

    @Column(name = "background_color", length = 32)
    private String backgroundColor;

    @Column(name = "background_image", columnDefinition = "text")
    private String backgroundImage;

    @Column(name = "theme", length = 32)
    private String theme;

    @Column(name = "components_json", columnDefinition = "text")
    private String componentsJson;

    @Column(name = "variables_json", columnDefinition = "text")
    private String variablesJson;

    @Column(name = "source_screen_id")
    private Long sourceScreenId;

    @Column(name = "source_template_id")
    private Long sourceTemplateId;

    @Column(name = "template_version", nullable = false)
    private Integer templateVersion = 1;

    @Column(name = "visibility_scope", nullable = false, length = 32)
    private String visibilityScope = "team";

    @Column(name = "owner_dept", length = 128)
    private String ownerDept;

    @Column(name = "listed", nullable = false)
    private boolean listed = true;

    @Column(name = "theme_pack_json", columnDefinition = "text")
    private String themePackJson;

    @Column(name = "archived", nullable = false)
    private boolean archived = false;

    @Column(name = "creator_id")
    private Long creatorId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public String getTagsJson() {
        return tagsJson;
    }

    public void setTagsJson(String tagsJson) {
        this.tagsJson = tagsJson;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(String backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public String getBackgroundImage() {
        return backgroundImage;
    }

    public void setBackgroundImage(String backgroundImage) {
        this.backgroundImage = backgroundImage;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getComponentsJson() {
        return componentsJson;
    }

    public void setComponentsJson(String componentsJson) {
        this.componentsJson = componentsJson;
    }

    public String getVariablesJson() {
        return variablesJson;
    }

    public void setVariablesJson(String variablesJson) {
        this.variablesJson = variablesJson;
    }

    public Long getSourceScreenId() {
        return sourceScreenId;
    }

    public void setSourceScreenId(Long sourceScreenId) {
        this.sourceScreenId = sourceScreenId;
    }

    public Long getSourceTemplateId() {
        return sourceTemplateId;
    }

    public void setSourceTemplateId(Long sourceTemplateId) {
        this.sourceTemplateId = sourceTemplateId;
    }

    public Integer getTemplateVersion() {
        return templateVersion;
    }

    public void setTemplateVersion(Integer templateVersion) {
        this.templateVersion = templateVersion;
    }

    public String getVisibilityScope() {
        return visibilityScope;
    }

    public void setVisibilityScope(String visibilityScope) {
        this.visibilityScope = visibilityScope;
    }

    public String getOwnerDept() {
        return ownerDept;
    }

    public void setOwnerDept(String ownerDept) {
        this.ownerDept = ownerDept;
    }

    public boolean isListed() {
        return listed;
    }

    public void setListed(boolean listed) {
        this.listed = listed;
    }

    public String getThemePackJson() {
        return themePackJson;
    }

    public void setThemePackJson(String themePackJson) {
        this.themePackJson = themePackJson;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId) {
        this.creatorId = creatorId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
