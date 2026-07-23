package com.yuzhi.dts.platform.repository.modeling;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.SourceBindingState;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@IntegrationTest
class ModelSpecSourceBindingLockIT {

    @Autowired
    private ModelSpecRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void sharedBindingLockBlocksConcurrentSourceMutationUntilValidationTransactionCompletes() throws Exception {
        String tenant = "model-spec-source-lock-" + UUID.randomUUID();
        String actor = "owner-1";
        String department = "dept-a";
        UUID planId = UUID.randomUUID();
        UUID bindingId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        seed(tenant, actor, department, planId, bindingId, assetId);

        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch writerStarted = new CountDownLatch(1);
        AtomicInteger writerPid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<SourceBindingState> reader = null;
        Future<Integer> writer = null;
        try {
            reader = executor.submit(() ->
                transaction().execute(status -> {
                    SourceBindingState binding = repository.lockSourceBinding(tenant, planId, bindingId).orElseThrow();
                    locked.countDown();
                    await(release);
                    return binding;
                })
            );
            assertThat(locked.await(5, SECONDS)).isTrue();

            writer = executor.submit(() ->
                transaction().execute(status -> {
                    writerPid.set(jdbcTemplate.queryForObject("select pg_backend_pid()", Integer.class));
                    writerStarted.countDown();
                    return jdbcTemplate.update(
                        """
                        update modeling_warehouse_plan_source
                           set source_version = 'v2', confirmation_status = 'DISCOVERED', locator_json = null
                         where tenant_id = ? and plan_id = ? and id = ?
                        """,
                        tenant,
                        planId,
                        bindingId
                    );
                })
            );

            assertThat(writerStarted.await(5, SECONDS)).isTrue();
            waitForLockWait(writerPid.get());
            assertThat(writer.isDone()).isFalse();

            release.countDown();
            assertThat(writer.get(5, SECONDS)).isEqualTo(1);
            assertThat(reader.get(5, SECONDS)).satisfies(binding -> {
                assertThat(binding.sourceVersion()).isEqualTo("v1");
                assertThat(binding.planOwnerId()).isEqualTo(actor);
                assertThat(binding.planOwnerDepartmentId()).isEqualTo(department);
                assertThat(binding.locatorJson()).contains(assetId.toString());
            });
        } finally {
            release.countDown();
            waitQuietly(reader);
            waitQuietly(writer);
            executor.shutdownNow();
            cleanup(tenant, planId);
        }
    }

    private void seed(String tenant, String actor, String department, UUID planId, UUID bindingId, UUID assetId) {
        transaction().executeWithoutResult(status -> {
            jdbcTemplate.update(
                """
                insert into modeling_warehouse_plan (
                    id, tenant_id, owner, code, name, owner_id, owner_department_id, onboarding_mode,
                    lifecycle_status, status, version, created_date, last_modified_date
                ) values (?, ?, ?, ?, ?, ?, ?, 'BUSINESS_FIRST', 'DRAFT', 'DRAFT', 1, current_timestamp, current_timestamp)
                """,
                planId,
                tenant,
                actor,
                "wp_" + planId.toString().replace("-", ""),
                "Source binding lock IT",
                actor,
                department
            );
            jdbcTemplate.update(
                """
                insert into modeling_warehouse_plan_source (
                    id, tenant_id, plan_id, source_type, source_id, source_version, locator_json,
                    confirmation_status, created_date, last_modified_date
                ) values (?, ?, ?, 'CATALOG_TABLE', ?, 'v1', cast(? as jsonb), 'CONFIRMED', current_timestamp, current_timestamp)
                """,
                bindingId,
                tenant,
                planId,
                assetId.toString(),
                "{\"assetId\":\"" + assetId + "\"}"
            );
        });
    }

    private void cleanup(String tenant, UUID planId) {
        transaction().executeWithoutResult(status -> {
            jdbcTemplate.update("delete from modeling_warehouse_plan_source where tenant_id = ? and plan_id = ?", tenant, planId);
            jdbcTemplate.update("delete from modeling_warehouse_plan where tenant_id = ? and id = ?", tenant, planId);
        });
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, SECONDS)) throw new IllegalStateException("Timed out waiting to release source binding lock");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while holding source binding lock", exception);
        }
    }

    private static void waitQuietly(Future<?> future) {
        if (future == null) return;
        try {
            future.get(5, SECONDS);
        } catch (ExecutionException | TimeoutException ignored) {
            // The primary assertion reports failures; cleanup must still proceed.
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void waitForLockWait(int pid) {
        org.awaitility.Awaitility.await()
            .pollInterval(Duration.ofMillis(25))
            .atMost(5, SECONDS)
            .until(() ->
                Boolean.TRUE.equals(
                    jdbcTemplate.queryForObject(
                        "select exists (select 1 from pg_stat_activity where pid = ? and wait_event_type = 'Lock')",
                        Boolean.class,
                        pid
                    )
                )
            );
    }
}
