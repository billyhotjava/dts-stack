package com.yuzhi.dts.platform.service.topic;

import com.yuzhi.dts.platform.domain.topic.TopicBinding;
import com.yuzhi.dts.platform.domain.topic.TopicTemplate;
import com.yuzhi.dts.platform.domain.topic.TopicTemplateEntity;
import com.yuzhi.dts.platform.repository.topic.TopicBindingRepository;
import com.yuzhi.dts.platform.repository.topic.TopicTemplateEntityRepository;
import com.yuzhi.dts.platform.repository.topic.TopicTemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import java.time.Instant;
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

    public TopicBindingService(
        TopicTemplateRepository templateRepository,
        TopicTemplateEntityRepository entityRepository,
        TopicBindingRepository bindingRepository
    ) {
        this.templateRepository = templateRepository;
        this.entityRepository = entityRepository;
        this.bindingRepository = bindingRepository;
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
        binding.setTemplateId(template.getId());
        binding.setEntityId(entity.getId());
        binding.setScopeKey(GLOBAL_SCOPE);
        binding.setBindingMode("ODS_TABLE");
        binding.setDataSourceId(request.dataSourceId());
        binding.setSchemaName(defaultText(request.schemaName(), entity.getExpectedSchema(), "ods"));
        binding.setTableName(requiredText(request.tableName(), "ODS 表名不能为空"));
        binding.setOdsMappingId(request.odsMappingId());
        binding.setBatchId(request.batchId());
        binding.setStatus("ACTIVE");
        binding.setBoundBy(defaultText(request.boundBy(), null, "system"));
        binding.setBoundAt(Instant.now());
        binding.setLastVerifiedAt(null);
        binding.setNotes(StringUtils.hasText(request.notes()) ? request.notes().trim() : null);
        return bindingRepository.save(binding);
    }

    private String requiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
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
}
