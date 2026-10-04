package com.yuzhi.dts.platform.repository.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicy;
import com.yuzhi.dts.platform.domain.iam.IamAssetActionPolicyRequest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(
    properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.liquibase.change-log=classpath:config/liquibase/changelog/iam-asset-action-policy-test.xml",
    }
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class IamAssetActionPolicyRepositoryIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("iamAssetActionPolicyIT")
        .withUsername("iam_action_policy_test")
        .withPassword("iam_action_policy_test");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private IamAssetActionPolicyRepository repository;

    @Autowired
    private IamAssetActionPolicyRequestRepository requestRepository;

    @Test
    void returnsOnlyPoliciesEffectiveAtTheDecisionInstant() {
        Instant now = Instant.parse("2026-07-27T10:00:00Z");
        IamAssetActionPolicy current = policy("ROLE", "ROLE_RELEASE_OPERATOR", "DATASET", "dataset-1", "EXPORT", "ALLOW");
        current.setValidFrom(now.minusSeconds(60));
        current.setValidTo(now.plusSeconds(60));
        repository.save(current);

        IamAssetActionPolicy future = policy("USER", "future-user", "DATASET", "dataset-1", "EXPORT", "ALLOW");
        future.setValidFrom(now.plusSeconds(1));
        repository.save(future);

        IamAssetActionPolicy expired = policy("DEPARTMENT", "finance", "DATASET", "dataset-1", "EXPORT", "DENY");
        expired.setValidTo(now.minusSeconds(1));
        repository.save(expired);
        repository.flush();

        assertThat(repository.findEffective("DATASET", "dataset-1", "EXPORT", now))
            .extracting(IamAssetActionPolicy::getSubjectId)
            .containsExactly("ROLE_RELEASE_OPERATOR");
    }

    @Test
    void rejectsDuplicateSubjectResourceActionTuple() {
        repository.saveAndFlush(policy("ROLE", "ROLE_RELEASE_OPERATOR", "DATASET", "dataset-1", "EXPORT", "ALLOW"));

        assertThatThrownBy(() ->
            repository.saveAndFlush(policy("ROLE", "ROLE_RELEASE_OPERATOR", "DATASET", "dataset-1", "EXPORT", "DENY"))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void permitsOnlyOnePendingRequestForTheSameSubjectAndResource() {
        requestRepository.saveAndFlush(request("PENDING", "alice"));

        assertThatThrownBy(() -> requestRepository.saveAndFlush(request("PENDING", "bob")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void permitsHistoricalDecisionsAlongsideANewPendingRequest() {
        requestRepository.saveAndFlush(request("APPROVED", "alice"));
        requestRepository.saveAndFlush(request("REJECTED", "bob"));

        assertThat(requestRepository.saveAndFlush(request("PENDING", "carol")).getId()).isNotNull();
    }

    private static IamAssetActionPolicy policy(
        String subjectType,
        String subjectId,
        String resourceType,
        String resourceId,
        String action,
        String effect
    ) {
        IamAssetActionPolicy policy = new IamAssetActionPolicy();
        policy.setSubjectType(subjectType);
        policy.setSubjectId(subjectId);
        policy.setSubjectName(subjectId);
        policy.setResourceType(resourceType);
        policy.setResourceId(resourceId);
        policy.setResourceName(resourceId);
        policy.setAction(action);
        policy.setEffect(effect);
        policy.setSource("MANUAL");
        policy.setCreatedBy("it-user");
        policy.setCreatedDate(Instant.now());
        return policy;
    }

    private static IamAssetActionPolicyRequest request(String status, String requester) {
        IamAssetActionPolicyRequest request = new IamAssetActionPolicyRequest();
        request.setSubjectType("ROLE");
        request.setSubjectId("ROLE_RELEASE_OPERATOR");
        request.setResourceType("DATASET");
        request.setResourceId("dataset-1");
        request.setChangesJson("{\"EXPORT\":\"ALLOW\"}");
        request.setBeforeSnapshotJson("{\"EXPORT\":\"NONE\"}");
        request.setReason("integration test");
        request.setStatus(status);
        request.setRequestedBy(requester);
        request.setCreatedBy(requester);
        request.setCreatedDate(Instant.now());
        return request;
    }
}
