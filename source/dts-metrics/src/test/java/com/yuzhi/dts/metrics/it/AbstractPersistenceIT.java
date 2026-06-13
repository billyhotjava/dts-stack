package com.yuzhi.dts.metrics.it;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for persistence integration tests (T05). Boots a real Spring context (web environment NONE — the
 * metrics context is tiny: no security/eureka) against a Testcontainers PostgreSQL, and lets Liquibase
 * build the F1 schema. Concrete ITs add {@code @Test} methods and stub the platform RestClient via a
 * {@code @TestConfiguration}/{@code @MockBean} (see {@code StubPlatformContractClient} pattern).
 *
 * <p>{@code disabledWithoutDocker = true} mirrors {@link MetricArtifactGenerationIT}: CI without Docker
 * skips rather than fails. The shared datasource block in {@code application.yml} points at {@code dts-pg}
 * by default, so {@code @DynamicPropertySource} MUST override the connection to the ephemeral container —
 * never let a Spring context test reach the compose Postgres.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractPersistenceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
    }
}
