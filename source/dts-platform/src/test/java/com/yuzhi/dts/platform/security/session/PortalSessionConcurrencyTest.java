package com.yuzhi.dts.platform.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.domain.security.PortalSessionEntity;
import com.yuzhi.dts.platform.repository.security.PortalSessionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PortalSessionConcurrencyTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4");

    @Test
    void concurrentFirstLoginAndTakeoverPreserveSingleActiveSession() throws Exception {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan(PortalSessionEntity.class.getPackageName());
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop", "hibernate.jdbc.batch_size", "25"));
        factory.afterPropertiesSet();
        try {
            var emf = factory.getObject();
            var transaction = new TransactionTemplate(new JpaTransactionManager(emf));
            var em = SharedEntityManagerCreator.createSharedEntityManager(emf);
            var repository = new JpaRepositoryFactory(em).getRepository(PortalSessionRepository.class);
            transaction.executeWithoutResult(status -> em.createNativeQuery(
                "create unique index uq_portal_sessions_active_username_partial on portal_sessions(normalized_username) where revoked_at is null"
            ).executeUpdate());
            var registry = new PortalSessionRegistry(30, true, repository);
            String username = "race-" + UUID.randomUUID();
            // First wave starts without a row to lock; second wave replaces an existing row.
            for (int wave = 0; wave < 2; wave++) {
                var ready = new CountDownLatch(8);
                var start = new CountDownLatch(1);
                try (var executor = Executors.newFixedThreadPool(8)) {
                    List<Future<String>> results = new ArrayList<>();
                    for (int i = 0; i < 8; i++) {
                        String browser = "browser-" + wave + "-" + i;
                        String login = i % 2 == 0 ? username.toUpperCase(java.util.Locale.ROOT) : username;
                        results.add(executor.submit(() -> {
                            ready.countDown();
                            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                            return transaction.execute(status -> registry.createSession(
                                login, List.of("ROLE_USER"), List.of("portal.view"), null, null, null, browser, null
                            ).sessionId());
                        }));
                    }
                    assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                    start.countDown();
                    for (var result : results) assertThat(result.get(30, TimeUnit.SECONDS)).isNotBlank();
                }
                Long active = transaction.execute(status -> em.createQuery(
                    "select count(s) from PortalSessionEntity s where s.normalizedUsername = :name and s.revokedAt is null", Long.class
                ).setParameter("name", username).getSingleResult());
                assertThat(active).isEqualTo(1L);
            }
        } finally {
            factory.destroy();
        }
    }
}
