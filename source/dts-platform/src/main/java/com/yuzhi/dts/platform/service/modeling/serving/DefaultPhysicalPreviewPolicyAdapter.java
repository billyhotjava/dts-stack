package com.yuzhi.dts.platform.service.modeling.serving;

import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogMaskingRule;
import com.yuzhi.dts.platform.domain.iam.IamDatasetPolicy;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogMaskingRuleRepository;
import com.yuzhi.dts.platform.repository.iam.IamDatasetPolicyRepository;
import com.yuzhi.dts.platform.security.ClassificationUtils;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationBoundary;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationBoundary.ClassificationFact;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.AccessContext;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.ColumnDecision;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.ColumnPolicy;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.PolicyDecision;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.RelationEvidence;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Resolves stable Catalog classification, IAM field policy and masking rules fail-closed. */
@Component
public class DefaultPhysicalPreviewPolicyAdapter implements PhysicalPreviewPolicyPort {

    private static final List<String> MASKS = List.of(
        "NULL", "REDACT", "FIXED", "MASK", "MASK_EMAIL", "MASK_PHONE",
        "PARTIAL", "HASH", "TOKENIZE", "CUSTOM"
    );

    private final CatalogDatasetRepository datasets;
    private final CatalogMaskingRuleRepository maskingRules;
    private final IamDatasetPolicyRepository iamPolicies;
    private final CatalogClassificationBoundary classifications;
    private final ClassificationUtils clearance;
    private final Clock clock;

    @Autowired
    public DefaultPhysicalPreviewPolicyAdapter(
        CatalogDatasetRepository datasets,
        CatalogMaskingRuleRepository maskingRules,
        IamDatasetPolicyRepository iamPolicies,
        CatalogClassificationBoundary classifications,
        ClassificationUtils clearance
    ) {
        this(datasets, maskingRules, iamPolicies, classifications, clearance, Clock.systemUTC());
    }

    DefaultPhysicalPreviewPolicyAdapter(
        CatalogDatasetRepository datasets,
        CatalogMaskingRuleRepository maskingRules,
        IamDatasetPolicyRepository iamPolicies,
        CatalogClassificationBoundary classifications,
        ClassificationUtils clearance,
        Clock clock
    ) {
        this.datasets = datasets;
        this.maskingRules = maskingRules;
        this.iamPolicies = iamPolicies;
        this.classifications = classifications;
        this.clearance = clearance;
        this.clock = clock;
    }

    @Override
    public PolicyDecision resolve(
        ModelSpecView model,
        RelationEvidence evidence,
        AccessContext access,
        String actorId
    ) {
        try {
            String stableKey = CatalogAssetKey.semanticModel(model.id().toString());
            ClassificationFact tableFact = propagated("ASSET", stableKey);
            if (tableFact == null) return unresolved(evidence);
            CatalogDataset dataset = access.physicalAssetId() == null
                ? null
                : datasets.findById(access.physicalAssetId()).orElse(null);
            List<String> tableLevels = new ArrayList<>();
            tableLevels.add(normalizeLevel(tableFact.effectiveLevel()));
            if (dataset != null && trim(dataset.getClassification()) != null) {
                tableLevels.add(normalizeLevel(dataset.getClassification()));
            }
            String tableLevel = SecurityLevelCatalog.maxDataCode(tableLevels);
            String userLevel = clearance.getCurrentUserExplicitMaxLevel().orElse(null);
            if (userLevel == null || !canAccess(tableLevel, userLevel)) throw denied();
            if (dataset == null) {
                // The stable semantic key remains the governance anchor, but legacy IAM and
                // masking stores are dataset-bound. Absence of that policy anchor is unknown,
                // never an implicit raw-data ALLOW.
                return unresolved(tableLevel, evidence);
            }

            List<IamDatasetPolicy> activePolicies = iamPolicies.findByDatasetIdAndSubjectTypeIgnoreCaseAndSubjectId(
                    dataset.getId(),
                    "USER",
                    actorId
                ).stream().filter(this::active).toList();
            if (activePolicies.stream().anyMatch(policy -> !"FIELD".equalsIgnoreCase(trim(policy.getScope())))) {
                return unresolved(evidence);
            }
            Map<String, List<String>> masks = masks(dataset);
            Map<String, ColumnDecision> decisions = new LinkedHashMap<>();
            for (var column : evidence.columns()) {
                String normalizedColumn = normalize(column.name());
                ClassificationFact columnFact = propagated("COLUMN", stableKey + "/column:" + normalizedColumn);
                if (columnFact == null) {
                    decisions.put(column.name(), unknown());
                    continue;
                }
                String columnLevel = normalizeLevel(columnFact.effectiveLevel());
                if (!canAccess(columnLevel, userLevel)) {
                    decisions.put(column.name(), new ColumnDecision(ColumnPolicy.DENY, null));
                    continue;
                }
                ColumnDecision iam = iamDecision(activePolicies, column.name());
                if (iam.policy() == ColumnPolicy.UNKNOWN || iam.policy() == ColumnPolicy.DENY) {
                    decisions.put(column.name(), iam);
                    continue;
                }
                decisions.put(column.name(), maskingDecision(masks.get(normalizedColumn)));
            }
            return new PolicyDecision(tableLevel, decisions);
        } catch (PhysicalPreviewException denied) {
            throw denied;
        } catch (RuntimeException unavailable) {
            return unresolved(evidence);
        }
    }

    private ClassificationFact propagated(String type, String key) {
        ClassificationFact fact = classifications.resolve(type, key).orElse(null);
        return fact != null && fact.propagated() && trim(fact.effectiveLevel()) != null ? fact : null;
    }

    private Map<String, List<String>> masks(CatalogDataset dataset) {
        if (dataset == null) return Map.of();
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (CatalogMaskingRule rule : maskingRules.findByDataset(dataset)) {
            String column = trim(rule.getColumn());
            if (column == null) continue;
            result.computeIfAbsent(normalize(column), ignored -> new ArrayList<>()).add(rule.getFunction());
        }
        return result;
    }

    private ColumnDecision iamDecision(List<IamDatasetPolicy> policies, String column) {
        List<String> effects = policies
            .stream()
            .filter(policy -> column.equalsIgnoreCase(trim(policy.getFieldName())))
            .map(IamDatasetPolicy::getEffect)
            .map(DefaultPhysicalPreviewPolicyAdapter::upper)
            .toList();
        if (effects.stream().anyMatch(effect -> "DENY".equals(effect))) {
            return new ColumnDecision(ColumnPolicy.DENY, null);
        }
        if (effects.stream().anyMatch(effect -> !"ALLOW".equals(effect))) return unknown();
        return new ColumnDecision(ColumnPolicy.ALLOW, null);
    }

    private static ColumnDecision maskingDecision(List<String> functions) {
        if (functions == null || functions.isEmpty()) {
            return new ColumnDecision(ColumnPolicy.ALLOW, null);
        }
        List<ColumnDecision> decisions = functions.stream().map(DefaultPhysicalPreviewPolicyAdapter::decision).toList();
        if (decisions.stream().anyMatch(item -> item.policy() == ColumnPolicy.UNKNOWN)) return unknown();
        if (decisions.stream().anyMatch(item -> item.policy() == ColumnPolicy.DENY)) {
            return new ColumnDecision(ColumnPolicy.DENY, null);
        }
        List<String> strategies = decisions
            .stream()
            .filter(item -> item.policy() == ColumnPolicy.MASK)
            .map(ColumnDecision::maskingStrategy)
            .distinct()
            .toList();
        if (strategies.size() > 1) return unknown();
        return strategies.isEmpty()
            ? new ColumnDecision(ColumnPolicy.ALLOW, null)
            : new ColumnDecision(ColumnPolicy.MASK, strategies.get(0));
    }

    private boolean active(IamDatasetPolicy policy) {
        Instant now = clock.instant();
        return (policy.getValidFrom() == null || !policy.getValidFrom().isAfter(now)) &&
            (policy.getValidTo() == null || !policy.getValidTo().isBefore(now));
    }

    private static boolean canAccess(String resourceLevel, String userLevel) {
        return SecurityLevelCatalog.dataRank(resourceLevel) <= SecurityLevelCatalog.dataRank(userLevel);
    }

    private static ColumnDecision decision(String raw) {
        String policy = upper(raw);
        if ("ALLOW".equals(policy) || "NONE".equals(policy)) {
            return new ColumnDecision(ColumnPolicy.ALLOW, null);
        }
        if ("DENY".equals(policy)) return new ColumnDecision(ColumnPolicy.DENY, null);
        if (MASKS.contains(policy)) return new ColumnDecision(ColumnPolicy.MASK, policy);
        return unknown();
    }

    private static PolicyDecision unresolved(RelationEvidence evidence) {
        return unresolved(null, evidence);
    }

    private static PolicyDecision unresolved(String classification, RelationEvidence evidence) {
        Map<String, ColumnDecision> decisions = new LinkedHashMap<>();
        evidence.columns().forEach(column -> decisions.put(column.name(), unknown()));
        return new PolicyDecision(classification, decisions);
    }

    private static ColumnDecision unknown() {
        return new ColumnDecision(ColumnPolicy.UNKNOWN, null);
    }

    private static PhysicalPreviewException denied() {
        return new PhysicalPreviewException("PHYSICAL_PREVIEW_ACCESS_DENIED", HttpStatus.FORBIDDEN, null);
    }

    private static String normalizeLevel(String value) {
        return SecurityLevelCatalog.requireDataLevel(value).code();
    }

    private static String normalize(String value) {
        return value
            .trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_.:-]+", "_")
            .replaceAll("_+", "_");
    }

    private static String upper(String value) {
        String trimmed = trim(value);
        return trimmed == null ? "" : trimmed.toUpperCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
