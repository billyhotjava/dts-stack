package com.yuzhi.dts.platform.service.iam;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.domain.iam.IamDatasetPolicy;
import com.yuzhi.dts.platform.domain.iam.IamSubjectDirectory;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.repository.iam.IamDatasetPolicyRepository;
import com.yuzhi.dts.platform.repository.iam.IamSubjectDirectoryRepository;
import com.yuzhi.dts.platform.repository.iam.IamUserClassificationRepository;
import com.yuzhi.dts.platform.service.iam.dto.*;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class PolicyService {

    private final CatalogDomainRepository domainRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final IamDatasetPolicyRepository datasetPolicyRepository;
    private final IamSubjectDirectoryRepository subjectDirectoryRepository;
    private final IamUserClassificationRepository userClassificationRepository;

    public PolicyService(
        CatalogDomainRepository domainRepository,
        CatalogDatasetRepository datasetRepository,
        IamDatasetPolicyRepository datasetPolicyRepository,
        IamSubjectDirectoryRepository subjectDirectoryRepository,
        IamUserClassificationRepository userClassificationRepository
    ) {
        this.domainRepository = domainRepository;
        this.datasetRepository = datasetRepository;
        this.datasetPolicyRepository = datasetPolicyRepository;
        this.subjectDirectoryRepository = subjectDirectoryRepository;
        this.userClassificationRepository = userClassificationRepository;
    }

    public List<Map<String, Object>> domainsWithDatasets() {
        List<CatalogDomain> domains = domainRepository.findAll();
        List<CatalogDataset> datasets = datasetRepository.findAll();
        Map<UUID, List<CatalogDataset>> grouped = datasets.stream().collect(Collectors.groupingBy(ds -> ds.getDomain() != null ? ds.getDomain().getId() : null));

        List<Map<String, Object>> result = new ArrayList<>();
        for (CatalogDomain domain : domains) {
            List<Map<String, Object>> dsList = grouped
                .getOrDefault(domain.getId(), List.of())
                .stream()
                .map(ds -> Map.<String, Object>of(
                        "id",
                        ds.getId().toString(),
                        "name",
                        ds.getName(),
                        "fields",
                        List.of("id", "name", "classification")
                    ))
                .collect(Collectors.toList());
            result.add(Map.of("id", domain.getId().toString(), "name", domain.getName(), "datasets", dsList));
        }
        // Add orphan datasets (without domain) if any
        List<CatalogDataset> orphanDatasets = grouped.getOrDefault(null, List.of());
        if (!orphanDatasets.isEmpty()) {
            List<Map<String, Object>> dsList = orphanDatasets
                .stream()
                .map(ds -> Map.<String, Object>of(
                        "id",
                        ds.getId().toString(),
                        "name",
                        ds.getName(),
                        "fields",
                        List.of("id", "name", "classification")
                    ))
                .collect(Collectors.toList());
            result.add(Map.of("id", "orphan", "name", "未归属域", "datasets", dsList));
        }
        return result;
    }

    public DatasetPoliciesDto datasetPolicies(UUID datasetId) {
        List<IamDatasetPolicy> policies = datasetPolicyRepository.findByDatasetId(datasetId);
        List<ObjectPolicyDto> objectPolicies = new ArrayList<>();
        List<FieldPolicyDto> fieldPolicies = new ArrayList<>();
        List<RowConditionDto> rowConditions = new ArrayList<>();
        for (IamDatasetPolicy policy : policies) {
            String scope = policy.getScope() != null ? policy.getScope().toUpperCase(Locale.ROOT) : "OBJECT";
            switch (scope) {
                case "FIELD" -> fieldPolicies.add(new FieldPolicyDto(policy.getFieldName(), policy.getSubjectType(), policy.getSubjectName(), policy.getEffect()));
                case "ROW" -> rowConditions.add(new RowConditionDto(policy.getSubjectType(), policy.getSubjectName(), policy.getRowExpression(), policy.getDescription()));
                default -> objectPolicies.add(
                    new ObjectPolicyDto(policy.getSubjectType(), policy.getSubjectId(), policy.getSubjectName(), policy.getEffect(), policy.getValidFrom(), policy.getValidTo(), policy.getSource())
                );
            }
        }
        return new DatasetPoliciesDto(objectPolicies, fieldPolicies, rowConditions);
    }

    public SubjectVisibleDto subjectVisible(String type, String subjectId) {
        List<IamDatasetPolicy> policies = datasetPolicyRepository.findBySubjectTypeIgnoreCaseAndSubjectId(type, subjectId);
        List<SubjectVisibleDto.VisibleObject> objects = new ArrayList<>();
        List<SubjectVisibleDto.VisibleField> fields = new ArrayList<>();
        List<SubjectVisibleDto.VisibleExpression> expressions = new ArrayList<>();
        for (IamDatasetPolicy policy : policies) {
            String scope = policy.getScope() != null ? policy.getScope().toUpperCase(Locale.ROOT) : "OBJECT";
            if ("FIELD".equals(scope)) {
                fields.add(new SubjectVisibleDto.VisibleField(policy.getDatasetName(), policy.getFieldName(), policy.getEffect()));
            } else if ("ROW".equals(scope)) {
                expressions.add(new SubjectVisibleDto.VisibleExpression(policy.getDatasetName(), policy.getRowExpression()));
            } else {
                objects.add(new SubjectVisibleDto.VisibleObject(policy.getDatasetId().toString(), policy.getDatasetName(), policy.getEffect()));
            }
        }
        return new SubjectVisibleDto(objects, fields, expressions);
    }

    public List<SubjectSummaryDto> searchSubjects(String type, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return List.of();
        }
        String kw = keyword.trim();
        List<IamSubjectDirectory> matches = new ArrayList<>(subjectDirectoryRepository.findTop20BySubjectTypeIgnoreCaseAndDisplayNameIgnoreCaseContaining(type, kw));
        if (matches.size() < 5) {
            matches.addAll(subjectDirectoryRepository.findTop20BySubjectTypeIgnoreCaseAndSubjectIdIgnoreCaseContaining(type, kw));
        }
        if (matches.isEmpty() && "user".equalsIgnoreCase(type)) {
            userClassificationRepository
                .findTop20ByUsernameIgnoreCaseContainingOrDisplayNameIgnoreCaseContaining(kw, kw)
                .forEach(user -> matches.add(toSubjectDirectory(type, user.getUsername(), user.getDisplayName())));
        }
        return matches
            .stream()
            .distinct()
            .limit(20)
            .map(item -> new SubjectSummaryDto(item.getSubjectId(), Optional.ofNullable(item.getDisplayName()).orElse(item.getSubjectId())))
            .toList();
    }

    private IamSubjectDirectory toSubjectDirectory(String type, String subjectId, String displayName) {
        IamSubjectDirectory dir = new IamSubjectDirectory();
        dir.setSubjectType(type);
        dir.setSubjectId(subjectId);
        dir.setDisplayName(displayName);
        return dir;
    }

    public ConflictPreviewDto previewConflicts(BatchAuthorizationInputDto input) {
        if (input == null || CollectionUtils.isEmpty(input.objects()) || CollectionUtils.isEmpty(input.subjects())) {
            return new ConflictPreviewDto(List.of());
        }
        List<ConflictItemDto> conflicts = new ArrayList<>();
        for (BatchAuthorizationInputDto.ObjectRef obj : input.objects()) {
            UUID datasetId = parseUuid(obj.datasetId());
            for (BatchAuthorizationInputDto.SubjectRef subject : input.subjects()) {
                List<IamDatasetPolicy> existing = datasetPolicyRepository.findByDatasetIdAndSubjectTypeIgnoreCaseAndSubjectId(datasetId, subject.type(), subject.id());
                String newEffect = input.scope() != null && StringUtils.hasText(input.scope().objectEffect())
                    ? input.scope().objectEffect().toUpperCase(Locale.ROOT)
                    : "ALLOW";
                for (IamDatasetPolicy policy : existing) {
                    String scope = policy.getScope() != null ? policy.getScope().toUpperCase(Locale.ROOT) : "OBJECT";
                    String key = scope.equals("FIELD") ? policy.getFieldName() : scope;
                    conflicts.add(
                        new ConflictItemDto(
                            scope.equals("ROW") ? "row" : (scope.equals("FIELD") ? "field" : "object"),
                            key,
                            subject.name(),
                            policy.getEffect(),
                            scope.equals("FIELD") ? findFieldEffect(input.scope(), policy.getFieldName()) : newEffect
                        )
                    );
                }
            }
        }
        return new ConflictPreviewDto(conflicts);
    }

    @Transactional
    public BatchApplyResultDto apply(BatchAuthorizationInputDto input, String username) {
        if (input == null || CollectionUtils.isEmpty(input.objects()) || CollectionUtils.isEmpty(input.subjects())) {
            return new BatchApplyResultDto(false, Instant.now());
        }
        Instant appliedAt = Instant.now();
        for (BatchAuthorizationInputDto.ObjectRef obj : input.objects()) {
            UUID datasetId = parseUuid(obj.datasetId());
            String datasetName = obj.datasetName();
            for (BatchAuthorizationInputDto.SubjectRef subject : input.subjects()) {
                upsertPolicies(datasetId, datasetName, subject, input.scope(), username, appliedAt);
            }
        }
        return new BatchApplyResultDto(true, appliedAt);
    }

    private void upsertPolicies(
        UUID datasetId,
        String datasetName,
        BatchAuthorizationInputDto.SubjectRef subject,
        BatchAuthorizationInputDto.Scope scope,
        String username,
        Instant appliedAt
    ) {
        List<IamDatasetPolicy> existing = datasetPolicyRepository.findByDatasetIdAndSubjectTypeIgnoreCaseAndSubjectId(datasetId, subject.type(), subject.id());
        Map<String, IamDatasetPolicy> existingByKey = new HashMap<>();
        for (IamDatasetPolicy policy : existing) {
            String key = buildKey(policy.getScope(), policy.getFieldName());
            existingByKey.put(key, policy);
        }

        List<IamDatasetPolicy> toPersist = new ArrayList<>();
        String objectEffect = scope != null && StringUtils.hasText(scope.objectEffect()) ? scope.objectEffect() : "ALLOW";
        // object level
        IamDatasetPolicy objectPolicy = existingByKey.remove(buildKey("OBJECT", null));
        if (objectPolicy == null) {
            objectPolicy = new IamDatasetPolicy();
            objectPolicy.setDatasetId(datasetId);
            objectPolicy.setDatasetName(datasetName);
            objectPolicy.setSubjectType(subject.type());
            objectPolicy.setSubjectId(subject.id());
            objectPolicy.setSubjectName(subject.name());
            objectPolicy.setScope("OBJECT");
        }
        objectPolicy.setEffect(objectEffect.toUpperCase(Locale.ROOT));
        applyValidity(scope, objectPolicy);
        objectPolicy.setSource("MANUAL");
        objectPolicy.setLastModifiedBy(username);
        objectPolicy.setCreatedBy(Optional.ofNullable(objectPolicy.getCreatedBy()).orElse(username));
        objectPolicy.setLastModifiedDate(appliedAt);
        toPersist.add(objectPolicy);

        // field policies
        List<BatchAuthorizationInputDto.FieldRef> fields = scope != null ? scope.fields() : List.of();
        if (fields != null) {
            for (BatchAuthorizationInputDto.FieldRef field : fields) {
                String key = buildKey("FIELD", field.name());
                IamDatasetPolicy policy = existingByKey.remove(key);
                if (policy == null) {
                    policy = new IamDatasetPolicy();
                    policy.setDatasetId(datasetId);
                    policy.setDatasetName(datasetName);
                    policy.setSubjectType(subject.type());
                    policy.setSubjectId(subject.id());
                    policy.setSubjectName(subject.name());
                    policy.setScope("FIELD");
                    policy.setFieldName(field.name());
                }
                policy.setEffect(field.effect() != null ? field.effect().toUpperCase(Locale.ROOT) : "ALLOW");
                policy.setRowExpression(null);
                policy.setSource("MANUAL");
                applyValidity(scope, policy);
                policy.setLastModifiedBy(username);
                policy.setCreatedBy(Optional.ofNullable(policy.getCreatedBy()).orElse(username));
                policy.setLastModifiedDate(appliedAt);
                toPersist.add(policy);
            }
        }

        // row policy
        if (scope != null && StringUtils.hasText(scope.rowExpression())) {
            IamDatasetPolicy policy = existingByKey.remove(buildKey("ROW", null));
            if (policy == null) {
                policy = new IamDatasetPolicy();
                policy.setDatasetId(datasetId);
                policy.setDatasetName(datasetName);
                policy.setSubjectType(subject.type());
                policy.setSubjectId(subject.id());
                policy.setSubjectName(subject.name());
                policy.setScope("ROW");
            }
            policy.setEffect("ALLOW");
            policy.setRowExpression(scope.rowExpression());
            policy.setDescription("Row level filter");
            policy.setSource("MANUAL");
            applyValidity(scope, policy);
            policy.setLastModifiedBy(username);
            policy.setCreatedBy(Optional.ofNullable(policy.getCreatedBy()).orElse(username));
            policy.setLastModifiedDate(appliedAt);
            toPersist.add(policy);
        }

        if (!existingByKey.isEmpty()) {
            datasetPolicyRepository.deleteAll(existingByKey.values());
        }
        datasetPolicyRepository.saveAll(toPersist);
    }

    private void applyValidity(BatchAuthorizationInputDto.Scope scope, IamDatasetPolicy policy) {
        if (scope != null) {
            policy.setValidFrom(scope.validFrom());
            policy.setValidTo(scope.validTo());
        }
    }

    private String buildKey(String scope, String field) {
        String normalizedScope = scope != null ? scope.toUpperCase(Locale.ROOT) : "OBJECT";
        return normalizedScope + ":" + (field != null ? field : "*");
    }

    private String findFieldEffect(BatchAuthorizationInputDto.Scope scope, String fieldName) {
        if (scope == null || CollectionUtils.isEmpty(scope.fields())) {
            return "ALLOW";
        }
        return scope
            .fields()
            .stream()
            .filter(f -> Objects.equals(f.name(), fieldName))
            .findFirst()
            .map(BatchAuthorizationInputDto.FieldRef::effect)
            .map(v -> v != null ? v.toUpperCase(Locale.ROOT) : "ALLOW")
            .orElse("ALLOW");
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new EntityNotFoundException("Invalid dataset id");
        }
    }
}
