package com.yuzhi.dts.platform.service.topic;

import com.yuzhi.dts.platform.domain.topic.TopicTemplate;
import com.yuzhi.dts.platform.domain.topic.TopicTemplateEntity;
import com.yuzhi.dts.platform.repository.topic.TopicTemplateEntityRepository;
import com.yuzhi.dts.platform.repository.topic.TopicTemplateRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TopicTemplateQueryService {

    private final TopicTemplateRepository templateRepository;
    private final TopicTemplateEntityRepository entityRepository;

    public TopicTemplateQueryService(TopicTemplateRepository templateRepository, TopicTemplateEntityRepository entityRepository) {
        this.templateRepository = templateRepository;
        this.entityRepository = entityRepository;
    }

    public List<TopicTemplateView> listTemplates() {
        return templateRepository
            .findAll()
            .stream()
            .sorted(Comparator.comparing(TopicTemplate::getTemplateCode, String.CASE_INSENSITIVE_ORDER))
            .map(this::toView)
            .toList();
    }

    private TopicTemplateView toView(TopicTemplate template) {
        List<TopicTemplateEntityView> entities = entityRepository
            .findByTemplateIdOrderByEntityCodeAsc(template.getId())
            .stream()
            .map(this::toView)
            .toList();
        return new TopicTemplateView(
            template.getId(),
            template.getTemplateCode(),
            template.getTemplateName(),
            template.getDescription(),
            template.getBindingScope(),
            template.getStatus(),
            Boolean.TRUE.equals(template.getEnabled()),
            entities
        );
    }

    private TopicTemplateEntityView toView(TopicTemplateEntity entity) {
        return new TopicTemplateEntityView(
            entity.getId(),
            entity.getEntityCode(),
            entity.getEntityName(),
            entity.getEntityType(),
            Boolean.TRUE.equals(entity.getRequired()),
            entity.getSourceName(),
            entity.getTableName(),
            entity.getExpectedSchema(),
            entity.getDescription()
        );
    }

    public record TopicTemplateView(
        java.util.UUID id,
        String templateCode,
        String templateName,
        String description,
        String bindingScope,
        String status,
        boolean enabled,
        List<TopicTemplateEntityView> entities
    ) {}

    public record TopicTemplateEntityView(
        java.util.UUID id,
        String entityCode,
        String entityName,
        String entityType,
        boolean required,
        String sourceName,
        String tableName,
        String expectedSchema,
        String description
    ) {}
}
