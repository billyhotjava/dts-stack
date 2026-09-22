package com.yuzhi.dts.admin.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.admin.domain.AdminKeycloakUser;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
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

/** Real PostgreSQL/Liquibase coverage for existing bindings; no Keycloak or PKI is simulated here. */
@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.liquibase.change-log=classpath:config/liquibase/master.xml"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class F11PersonnelIdentityRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.liquibase.url", POSTGRES::getJdbcUrl);
        registry.add("spring.liquibase.user", POSTGRES::getUsername);
        registry.add("spring.liquibase.password", POSTGRES::getPassword);
    }

    @Autowired
    private AdminKeycloakUserRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("F11-IT-004：真实数据库按原始编码精确区分大小写")
    void resolvesCaseDistinctSourceRecordsIndependently() {
        String suffix = UUID.randomUUID().toString();
        AdminKeycloakUser upper = save("ABC" + suffix, "upper-" + suffix);
        AdminKeycloakUser lower = save("abc" + suffix, "lower-" + suffix);
        entityManager.clear();

        assertThat(repository.findFirstByPersonCode("ABC" + suffix)).get()
            .extracting(AdminKeycloakUser::getId, AdminKeycloakUser::getKeycloakId)
            .containsExactly(upper.getId(), upper.getKeycloakId());
        assertThat(repository.findFirstByPersonCode("abc" + suffix)).get()
            .extracting(AdminKeycloakUser::getId, AdminKeycloakUser::getKeycloakId)
            .containsExactly(lower.getId(), lower.getKeycloakId());
        assertThat(upper.getId()).isNotEqualTo(lower.getId());
    }

    @Test
    @DisplayName("F11-IT-019：改展示登录名后仍可按原稳定账号找到同一目录记录")
    void changingLoginNamePreservesStableBinding() {
        AdminKeycloakUser user = save("SOURCE-" + UUID.randomUUID(), "before-" + UUID.randomUUID());
        Long directoryId = user.getId();
        String accountId = user.getKeycloakId();
        String renamed = "after-" + UUID.randomUUID();
        user.setUsername(renamed);
        repository.saveAndFlush(user);
        entityManager.clear();

        AdminKeycloakUser loaded = repository.findByKeycloakId(accountId).orElseThrow();
        assertThat(loaded.getId()).isEqualTo(directoryId);
        assertThat(loaded.getUsername()).isEqualTo(renamed);
        assertThat(loaded.getPersonCode()).isEqualTo(user.getPersonCode());
    }

    @Test
    @DisplayName("F11-IT-004：同一 kc_id 不能持久化为两条当前绑定")
    void uniqueAccountBindingIsEnforcedByDatabase() {
        AdminKeycloakUser first = save("SOURCE-" + UUID.randomUUID(), "first-" + UUID.randomUUID());
        AdminKeycloakUser duplicate = record("OTHER-" + UUID.randomUUID(), "second-" + UUID.randomUUID());
        duplicate.setKeycloakId(first.getKeycloakId());

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("F11-IT-005：MDM 原始活跃标志与账号启停可独立持久化")
    void rawMdmStatusDoesNotAliasAccountEnabledColumn() {
        AdminKeycloakUser user = record("SOURCE-" + UUID.randomUUID(), "disabled-" + UUID.randomUUID());
        user.setEnabled(false);
        user.setMdmEnabled(1);
        repository.saveAndFlush(user);
        entityManager.clear();

        AdminKeycloakUser loaded = repository.findByKeycloakId(user.getKeycloakId()).orElseThrow();
        assertThat(loaded.isEnabled()).isFalse();
        assertThat(loaded.getMdmEnabled()).isEqualTo(1);
    }

    @Test
    @DisplayName("F11-T07：业务准入/同步状态与来源版本可独立持久化")
    void accessAndSyncStatesPersistIndependently() {
        AdminKeycloakUser user = record("SOURCE-" + UUID.randomUUID(), "lifecycle-" + UUID.randomUUID());
        user.setAccessState("SUSPENDED");
        user.setSyncState("RETRYABLE_FAILURE");
        user.setSyncError("kc-unreachable");
        user.setSourceSystem("MDM");
        user.setSourceVersion("v12");
        repository.saveAndFlush(user);
        entityManager.clear();

        AdminKeycloakUser loaded = repository.findByKeycloakId(user.getKeycloakId()).orElseThrow();
        assertThat(loaded.getAccessState()).isEqualTo("SUSPENDED");
        assertThat(loaded.getSyncState()).isEqualTo("RETRYABLE_FAILURE");
        assertThat(loaded.getSyncError()).isEqualTo("kc-unreachable");
        assertThat(loaded.getSourceSystem()).isEqualTo("MDM");
        assertThat(loaded.getSourceVersion()).isEqualTo("v12");
    }

    private AdminKeycloakUser save(String personCode, String username) {
        return repository.saveAndFlush(record(personCode, username));
    }

    private AdminKeycloakUser record(String personCode, String username) {
        AdminKeycloakUser user = new AdminKeycloakUser();
        user.setKeycloakId(UUID.randomUUID().toString());
        user.setPersonCode(personCode);
        user.setUsername(username);
        user.setPersonSecurityLevel("GENERAL");
        user.setCreatedBy("f11-test");
        user.setCreatedDate(Instant.parse("2026-09-20T00:00:00Z"));
        user.setLastModifiedBy("f11-test");
        user.setLastModifiedDate(Instant.parse("2026-09-20T00:00:00Z"));
        return user;
    }
}
