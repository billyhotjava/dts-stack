package com.yuzhi.dts.platform.service.security;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicy;
import com.yuzhi.dts.platform.repository.iam.IamAssetActionPolicyRepository;
import com.yuzhi.dts.platform.security.policy.AssetAction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AssetActionPolicyEvaluator {

    private final IamAssetActionPolicyRepository repository;
    private final AssetActionSubjectResolver subjectResolver;

    public AssetActionPolicyEvaluator(
        IamAssetActionPolicyRepository repository,
        AssetActionSubjectResolver subjectResolver
    ) {
        this.repository = repository;
        this.subjectResolver = subjectResolver;
    }

    public boolean canPerform(CatalogDataset dataset, AssetAction action) {
        if (dataset == null || action == null) {
            return false;
        }
        Set<AssetActionSubjectResolver.SubjectKey> currentSubjects = subjectResolver.currentSubjects();
        if (currentSubjects == null || currentSubjects.isEmpty()) {
            return false;
        }

        Set<ResourceKey> resources = resourceKeys(dataset);
        if (resources.isEmpty()) {
            return false;
        }
        Instant decisionTime = Instant.now();
        List<IamAssetActionPolicy> matching = new ArrayList<>();
        for (ResourceKey resource : resources) {
            repository
                .findEffective(resource.type(), resource.id(), action.code(), decisionTime)
                .stream()
                .filter(policy -> matchesCurrentSubject(policy, currentSubjects))
                .forEach(matching::add);
        }
        if (matching.stream().anyMatch(policy -> "DENY".equalsIgnoreCase(policy.getEffect()))) {
            return false;
        }
        return matching.stream().anyMatch(policy -> "ALLOW".equalsIgnoreCase(policy.getEffect()));
    }

    private static boolean matchesCurrentSubject(
        IamAssetActionPolicy policy,
        Set<AssetActionSubjectResolver.SubjectKey> subjects
    ) {
        if (policy == null || !StringUtils.hasText(policy.getSubjectType()) || !StringUtils.hasText(policy.getSubjectId())) {
            return false;
        }
        return subjects
            .stream()
            .anyMatch(subject ->
                subject.type().equalsIgnoreCase(policy.getSubjectType()) &&
                subject.id().equalsIgnoreCase(policy.getSubjectId())
            );
    }

    private static Set<ResourceKey> resourceKeys(CatalogDataset dataset) {
        Set<ResourceKey> keys = new LinkedHashSet<>();
        if (dataset.getId() != null) {
            keys.add(new ResourceKey("DATASET", dataset.getId().toString()));
        }
        if (StringUtils.hasText(dataset.getHiveTable())) {
            String tableId = StringUtils.hasText(dataset.getHiveDatabase())
                ? dataset.getHiveDatabase().trim() + "." + dataset.getHiveTable().trim()
                : dataset.getHiveTable().trim();
            keys.add(new ResourceKey("TABLE", tableId));
        }
        if (StringUtils.hasText(dataset.getTrinoCatalog())) {
            keys.add(new ResourceKey("CATALOG", dataset.getTrinoCatalog().trim()));
        }
        return keys;
    }

    private record ResourceKey(String type, String id) {
        private ResourceKey {
            type = type.toUpperCase(Locale.ROOT);
        }
    }
}
