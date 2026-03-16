package com.yuzhi.dts.platform.service.topic;

import com.yuzhi.dts.platform.domain.topic.TopicTemplate;
import com.yuzhi.dts.platform.domain.topic.TopicTemplateEntity;
import com.yuzhi.dts.platform.repository.topic.TopicTemplateEntityRepository;
import com.yuzhi.dts.platform.repository.topic.TopicTemplateRepository;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class TopicTemplateBootstrapService {

    private final TopicTemplateRepository templateRepository;
    private final TopicTemplateEntityRepository entityRepository;

    public TopicTemplateBootstrapService(TopicTemplateRepository templateRepository, TopicTemplateEntityRepository entityRepository) {
        this.templateRepository = templateRepository;
        this.entityRepository = entityRepository;
    }

    @PostConstruct
    public void initializeDefaults() {
        bootstrapDefaults();
    }

    public void bootstrapDefaults() {
        TopicTemplate projectManagement = upsertTemplate(
            "project-management",
            "项目管理专题",
            "项目管理、进度、风险与口径支撑专题",
            "GLOBAL"
        );
        upsertEntity(
            projectManagement,
            "project_subject_domain",
            "项目主体域",
            "ODS_TABLE",
            "pm_ods",
            "project_subject_domain",
            "ods",
            true,
            "项目管理专题的主输入逻辑实体"
        );

        TopicTemplate plmOverview = upsertTemplate("plm-overview", "PLM 专题", "PLM 概览专题模板", "GLOBAL");
        upsertEntity(plmOverview, "plm_bom_domain", "PLM BOM 域", "ODS_TABLE", "plm_ods", "plm_bom_domain", "ods", true, "PLM BOM 逻辑实体");
        upsertEntity(
            plmOverview,
            "plm_change_domain",
            "PLM 变更域",
            "ODS_TABLE",
            "plm_ods",
            "plm_change_domain",
            "ods",
            true,
            "PLM 变更逻辑实体"
        );
        upsertEntity(plmOverview, "plm_part_domain", "PLM 零件域", "ODS_TABLE", "plm_ods", "plm_part_domain", "ods", false, "PLM 零件逻辑实体");
    }

    private TopicTemplate upsertTemplate(String code, String name, String description, String bindingScope) {
        TopicTemplate template = templateRepository.findByTemplateCodeIgnoreCase(code).orElseGet(TopicTemplate::new);
        template.setTemplateCode(code);
        template.setTemplateName(name);
        template.setDescription(description);
        template.setStatus("ACTIVE");
        template.setBindingScope(defaultText(bindingScope, "GLOBAL"));
        template.setEnabled(Boolean.TRUE);
        return templateRepository.save(template);
    }

    private void upsertEntity(
        TopicTemplate template,
        String entityCode,
        String entityName,
        String entityType,
        String sourceName,
        String tableName,
        String expectedSchema,
        boolean required,
        String description
    ) {
        TopicTemplateEntity entity = entityRepository
            .findFirstByTemplateIdAndEntityCodeIgnoreCase(template.getId(), entityCode)
            .orElseGet(TopicTemplateEntity::new);
        entity.setTemplateId(template.getId());
        entity.setEntityCode(entityCode);
        entity.setEntityName(entityName);
        entity.setEntityType(entityType);
        entity.setSourceName(sourceName);
        entity.setTableName(tableName);
        entity.setExpectedSchema(expectedSchema);
        entity.setRequired(required);
        entity.setDescription(description);
        entityRepository.save(entity);
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
