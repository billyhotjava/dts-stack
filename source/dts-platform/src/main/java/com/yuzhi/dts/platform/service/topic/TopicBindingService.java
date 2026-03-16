package com.yuzhi.dts.platform.service.topic;

import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.domain.topic.TopicBinding;
import com.yuzhi.dts.platform.domain.topic.TopicTemplate;
import com.yuzhi.dts.platform.domain.topic.TopicTemplateEntity;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import com.yuzhi.dts.platform.repository.topic.TopicBindingRepository;
import com.yuzhi.dts.platform.repository.topic.TopicTemplateEntityRepository;
import com.yuzhi.dts.platform.repository.topic.TopicTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class TopicBindingService {

    private static final String GLOBAL_SCOPE = "GLOBAL";

    private final TopicTemplateRepository templateRepository;
    private final TopicTemplateEntityRepository entityRepository;
    private final TopicBindingRepository bindingRepository;
    private final InfraOdsTableMappingRepository odsTableMappingRepository;

    public TopicBindingService(
        TopicTemplateRepository templateRepository,
        TopicTemplateEntityRepository entityRepository,
        TopicBindingRepository bindingRepository,
        InfraOdsTableMappingRepository odsTableMappingRepository
    ) {
        this.templateRepository = templateRepository;
        this.entityRepository = entityRepository;
        this.bindingRepository = bindingRepository;
        this.odsTableMappingRepository = odsTableMappingRepository;
    }

    public TopicBinding bindOdsTable(BindOdsTableRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("绑定请求不能为空");
        }
        TopicTemplate template = templateRepository
            .findByTemplateCodeIgnoreCase(request.templateCode())
            .orElseThrow(() -> new EntityNotFoundException("专题模板不存在"));
        TopicTemplateEntity entity = entityRepository
            .findFirstByTemplateIdAndEntityCodeIgnoreCase(template.getId(), request.entityCode())
            .orElseThrow(() -> new EntityNotFoundException("专题逻辑实体不存在"));

        TopicBinding binding = bindingRepository
            .findByTemplateIdAndEntityIdAndScopeKey(template.getId(), entity.getId(), GLOBAL_SCOPE)
            .orElseGet(TopicBinding::new);
        ResolvedOdsTarget target = resolveOdsTarget(request, entity);
        binding.setTemplateId(template.getId());
        binding.setEntityId(entity.getId());
        binding.setScopeKey(GLOBAL_SCOPE);
        binding.setBindingMode("ODS_TABLE");
        binding.setDataSourceId(request.dataSourceId());
        binding.setSchemaName(target.schemaName());
        binding.setTableName(target.tableName());
        binding.setOdsMappingId(target.odsMappingId());
        binding.setBatchId(request.batchId());
        binding.setStatus("ACTIVE");
        binding.setBoundBy(defaultText(request.boundBy(), null, "system"));
        binding.setBoundAt(Instant.now());
        binding.setLastVerifiedAt(null);
        binding.setNotes(StringUtils.hasText(request.notes()) ? request.notes().trim() : null);
        return bindingRepository.save(binding);
    }

    private ResolvedOdsTarget resolveOdsTarget(BindOdsTableRequest request, TopicTemplateEntity entity) {
        String schemaName = defaultText(request.schemaName(), entity.getExpectedSchema(), "ods");
        String tableName = requiredText(request.tableName(), "ODS 表名不能为空");
        InfraOdsTableMapping mapping = findMatchingMapping(request.odsMappingId(), schemaName, tableName);
        if (mapping == null) {
            return new ResolvedOdsTarget(schemaName, tableName, request.odsMappingId());
        }
        return new ResolvedOdsTarget(
            defaultText(mapping.getOdsSchema(), schemaName, "public"),
            defaultText(mapping.getOdsTable(), tableName),
            mapping.getId()
        );
    }

    private InfraOdsTableMapping findMatchingMapping(UUID mappingId, String schemaName, String tableName) {
        if (mappingId != null) {
            InfraOdsTableMapping mapping = odsTableMappingRepository.findById(mappingId).orElse(null);
            if (isEnabled(mapping)) {
                return mapping;
            }
        }
        InfraOdsTableMapping exact = odsTableMappingRepository.findFirstByOdsSchemaIgnoreCaseAndOdsTableIgnoreCase(schemaName, tableName).orElse(null);
        if (isEnabled(exact)) {
            return exact;
        }
        List<InfraOdsTableMapping> candidates = odsTableMappingRepository.findByEnabledTrueAndOdsTableIgnoreCaseOrderByCreatedDateDesc(tableName);
        if (candidates == null || candidates.size() != 1) {
            return null;
        }
        return candidates.get(0);
    }

    private boolean isEnabled(InfraOdsTableMapping mapping) {
        return mapping != null && Boolean.TRUE.equals(mapping.getEnabled());
    }

    private String requiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String defaultText(String value, String fallback, String finalFallback) {
        if (StringUtils.hasText(value)) {
            return value.trim();
        }
        if (StringUtils.hasText(fallback)) {
            return fallback.trim();
        }
        return finalFallback;
    }

    public record BindOdsTableRequest(
        String templateCode,
        String entityCode,
        UUID dataSourceId,
        String schemaName,
        String tableName,
        UUID odsMappingId,
        UUID batchId,
        String boundBy,
        String notes
    ) {}

    private record ResolvedOdsTarget(
        String schemaName,
        String tableName,
        UUID odsMappingId
    ) {}
}
