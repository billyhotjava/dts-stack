package com.yuzhi.dts.platform.service.governance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.GovernanceProperties;
import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import com.yuzhi.dts.platform.domain.governance.GovRuleVersion;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleBindingRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleVersionRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.governance.dto.QualityRuleDto;
import com.yuzhi.dts.platform.service.governance.request.QualityRuleBindingRequest;
import com.yuzhi.dts.platform.service.governance.request.QualityRuleUpsertRequest;
import jakarta.persistence.EntityNotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class QualityRuleService {

    private static final Logger log = LoggerFactory.getLogger(QualityRuleService.class);

    private final GovRuleRepository ruleRepository;
    private final GovRuleVersionRepository versionRepository;
    private final GovRuleBindingRepository bindingRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final GovernanceProperties properties;

    public QualityRuleService(
        GovRuleRepository ruleRepository,
        GovRuleVersionRepository versionRepository,
        GovRuleBindingRepository bindingRepository,
        CatalogDatasetRepository datasetRepository,
        AuditService auditService,
        ObjectMapper objectMapper,
        GovernanceProperties properties
    ) {
        this.ruleRepository = ruleRepository;
        this.versionRepository = versionRepository;
        this.bindingRepository = bindingRepository;
        this.datasetRepository = datasetRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<QualityRuleDto> listAll() {
        return ruleRepository.findAll().stream().map(GovernanceMapper::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public QualityRuleDto getRule(UUID id) {
        GovRule rule = ruleRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        return GovernanceMapper.toDto(rule);
    }

    @Transactional(readOnly = true)
    public List<QualityRuleDto> findByDataset(UUID datasetId) {
        return ruleRepository
            .findAll()
            .stream()
            .filter(rule -> datasetId == null || datasetId.equals(rule.getDatasetId()))
            .map(GovernanceMapper::toDto)
            .collect(Collectors.toList());
    }

    public QualityRuleDto createRule(QualityRuleUpsertRequest request, String actor) {
        validateDataset(request.getDatasetId());
        String code = resolveRuleCode(request.getCode());
        if (ruleRepository.findByCode(code).isPresent()) {
            throw new IllegalArgumentException("编码重复: " + code);
        }

        GovRule rule = new GovRule();
        rule.setCode(code);
        applyRuleMetadata(rule, request);
        ruleRepository.save(rule);

        GovRuleVersion version = persistVersion(rule, request, 1, actor);
        rule.setLatestVersion(version);
        ruleRepository.save(rule);

        auditService.audit("CREATE", "governance.rule", rule.getId().toString());
        return GovernanceMapper.toDto(ruleRepository.findById(rule.getId()).orElseThrow());
    }

    public QualityRuleDto updateRule(UUID id, QualityRuleUpsertRequest request, String actor) {
        GovRule rule = ruleRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        if (request.getDatasetId() != null) {
            validateDataset(request.getDatasetId());
        }
        if (StringUtils.isNotBlank(request.getCode())) {
            ruleRepository
                .findByCode(request.getCode().trim())
                .filter(other -> !Objects.equals(other.getId(), id))
                .ifPresent(other -> {
                    throw new IllegalArgumentException("编码已被占用");
                });
            rule.setCode(request.getCode().trim());
        }
        applyRuleMetadata(rule, request);
        ruleRepository.save(rule);

        int nextVersion = versionRepository.findFirstByRuleIdOrderByVersionDesc(id).map(v -> v.getVersion() + 1).orElse(1);
        GovRuleVersion version = persistVersion(rule, request, nextVersion, actor);
        rule.setLatestVersion(version);
        ruleRepository.save(rule);

        auditService.audit("UPDATE", "governance.rule", id.toString());
        return GovernanceMapper.toDto(ruleRepository.findById(id).orElseThrow());
    }

    public void deleteRule(UUID id) {
        GovRule rule = ruleRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        ruleRepository.delete(rule);
        auditService.audit("DELETE", "governance.rule", id.toString());
    }

    public QualityRuleDto toggleRule(UUID id, boolean enabled) {
        GovRule rule = ruleRepository.findById(id).orElseThrow(EntityNotFoundException::new);
        rule.setEnabled(enabled);
        ruleRepository.save(rule);
        auditService.audit("UPDATE", "governance.rule.toggle", id.toString());
        return GovernanceMapper.toDto(rule);
    }

    private void applyRuleMetadata(GovRule rule, QualityRuleUpsertRequest request) {
        rule.setName(trimToNull(request.getName()));
        rule.setType(trimToNull(request.getType()));
        rule.setDatasetId(request.getDatasetId());
        rule.setCategory(trimToNull(request.getCategory()));
        rule.setDescription(trimToNull(request.getDescription()));
        rule.setOwner(trimToNull(request.getOwner()));
        rule.setSeverity(trimToNull(request.getSeverity()));
        rule.setDataLevel(trimToNull(request.getDataLevel()));
        rule.setFrequencyCron(trimToNull(request.getFrequencyCron()));
        rule.setTemplate(Boolean.TRUE.equals(request.getTemplate()));
        rule.setExecutor(resolveExecutor(request));
        if (request.getEnabled() != null) {
            rule.setEnabled(request.getEnabled());
        }
    }

    private String resolveExecutor(QualityRuleUpsertRequest request) {
        if (StringUtils.isNotBlank(request.getExecutor())) {
            return request.getExecutor().trim();
        }
        return properties.getQuality().getDefaultExecutor();
    }

    private GovRuleVersion persistVersion(GovRule rule, QualityRuleUpsertRequest request, int versionNumber, String actor) {
        GovRuleVersion version = new GovRuleVersion();
        version.setRule(rule);
        version.setVersion(versionNumber);
        version.setStatus("PUBLISHED");
        version.setDefinition(writeDefinition(request.getDefinition()));
        version.setApprovedBy(actor);
        version.setApprovedAt(java.time.Instant.now());
        versionRepository.save(version);

        List<QualityRuleBindingRequest> bindings = request.getBindings() != null ? request.getBindings() : Collections.emptyList();
        bindings.stream().filter(binding -> binding.getDatasetId() != null).forEach(binding -> {
            GovRuleBinding entity = new GovRuleBinding();
            entity.setRuleVersion(version);
            entity.setDatasetId(binding.getDatasetId());
            entity.setDatasetAlias(trimToNull(binding.getDatasetAlias()));
            entity.setScopeType(trimToNull(binding.getScopeType()));
            entity.setFieldRefs(GovernanceMapper.joinCsv(binding.getFieldRefs()));
            entity.setFilterExpression(trimToNull(binding.getFilterExpression()));
            entity.setScheduleOverride(trimToNull(binding.getScheduleOverride()));
            bindingRepository.save(entity);
        });
        return version;
    }

    private String writeDefinition(Map<String, Object> definition) {
        Map<String, Object> payload = definition != null ? definition : Map.of();
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize rule definition: {}", e.getMessage());
            return "{}";
        }
    }

    private void validateDataset(UUID datasetId) {
        if (datasetId == null) {
            return;
        }
        datasetRepository.findById(datasetId).orElseThrow(() -> new IllegalArgumentException("数据集不存在"));
    }

    private String resolveRuleCode(String code) {
        if (StringUtils.isNotBlank(code)) {
            return code.trim();
        }
        return "QRULE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String trimToNull(String value) {
        return StringUtils.isNotBlank(value) ? value.trim() : null;
    }
}
