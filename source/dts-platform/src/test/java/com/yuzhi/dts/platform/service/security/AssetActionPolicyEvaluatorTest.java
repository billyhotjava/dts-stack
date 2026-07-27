package com.yuzhi.dts.platform.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicy;
import com.yuzhi.dts.platform.repository.iam.IamAssetActionPolicyRepository;
import com.yuzhi.dts.platform.security.policy.AssetAction;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetActionPolicyEvaluatorTest {

    @Mock
    private IamAssetActionPolicyRepository repository;

    @Mock
    private AssetActionSubjectResolver subjectResolver;

    private AssetActionPolicyEvaluator evaluator;
    private CatalogDataset dataset;

    @BeforeEach
    void setUp() {
        evaluator = new AssetActionPolicyEvaluator(repository, subjectResolver);
        dataset = new CatalogDataset();
        dataset.setId(UUID.fromString("10000000-0000-0000-0000-000000000001"));
        dataset.setName("finance_orders");
    }

    @Test
    void deniesWhenNoPolicyMatches() {
        when(subjectResolver.currentSubjects())
            .thenReturn(Set.of(new AssetActionSubjectResolver.SubjectKey("ROLE", "ROLE_RELEASE_OPERATOR")));
        when(
            repository.findEffective(
                eq("DATASET"),
                eq(dataset.getId().toString()),
                eq("EXPORT"),
                any(Instant.class)
            )
        )
            .thenReturn(List.of());

        assertThat(evaluator.canPerform(dataset, AssetAction.EXPORT)).isFalse();
    }

    @Test
    void explicitDenyWinsOverAllowAcrossMatchingSubjects() {
        when(subjectResolver.currentSubjects())
            .thenReturn(
                Set.of(
                    new AssetActionSubjectResolver.SubjectKey("ROLE", "ROLE_RELEASE_OPERATOR"),
                    new AssetActionSubjectResolver.SubjectKey("DEPARTMENT", "finance")
                )
            );
        when(
            repository.findEffective(
                eq("DATASET"),
                eq(dataset.getId().toString()),
                eq("EXPORT"),
                any(Instant.class)
            )
        )
            .thenReturn(
                List.of(
                    policy("ROLE", "ROLE_RELEASE_OPERATOR", "ALLOW"),
                    policy("DEPARTMENT", "finance", "DENY")
                )
            );

        assertThat(evaluator.canPerform(dataset, AssetAction.EXPORT)).isFalse();
    }

    @Test
    void allowsOnlyAnEffectivePolicyForTheCurrentSubject() {
        when(subjectResolver.currentSubjects())
            .thenReturn(Set.of(new AssetActionSubjectResolver.SubjectKey("USER", "alice")));
        when(
            repository.findEffective(
                eq("DATASET"),
                eq(dataset.getId().toString()),
                eq("UPDATE"),
                any(Instant.class)
            )
        )
            .thenReturn(List.of(policy("ROLE", "ROLE_RELEASE_OPERATOR", "ALLOW"), policy("USER", "alice", "ALLOW")));

        assertThat(evaluator.canPerform(dataset, AssetAction.UPDATE)).isTrue();
    }

    @Test
    void allowsCatalogScopedCreateBeforeDatasetHasAnId() {
        CatalogDataset newDataset = new CatalogDataset();
        newDataset.setTrinoCatalog("finance");
        when(subjectResolver.currentSubjects())
            .thenReturn(Set.of(new AssetActionSubjectResolver.SubjectKey("ROLE", "ROLE_DATA_ENGINEER")));
        when(repository.findEffective(eq("CATALOG"), eq("finance"), eq("CREATE"), any(Instant.class)))
            .thenReturn(List.of(policy("ROLE", "ROLE_DATA_ENGINEER", "ALLOW")));

        assertThat(evaluator.canPerform(newDataset, AssetAction.CREATE)).isTrue();
    }

    private static IamAssetActionPolicy policy(String subjectType, String subjectId, String effect) {
        IamAssetActionPolicy policy = new IamAssetActionPolicy();
        policy.setSubjectType(subjectType);
        policy.setSubjectId(subjectId);
        policy.setResourceType("DATASET");
        policy.setResourceId("10000000-0000-0000-0000-000000000001");
        policy.setAction("EXPORT");
        policy.setEffect(effect);
        return policy;
    }
}
