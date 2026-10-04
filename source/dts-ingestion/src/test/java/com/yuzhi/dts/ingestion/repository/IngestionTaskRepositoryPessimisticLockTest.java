package com.yuzhi.dts.ingestion.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.ingestion.domain.IngestionTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IngestionTaskRepositoryPessimisticLockTest {

    @Autowired
    private IngestionTaskRepository taskRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void findByIdForUpdate_serializesConcurrentWriters() throws Exception {
        TransactionTemplate transaction = requiresNewTransaction();
        Long taskId = transaction.execute(status -> {
            IngestionTask task = new IngestionTask();
            task.setName("locking-task");
            task.setSourceType("postgresqlreader");
            task.setDestinationType("postgresqlwriter");
            task.setSyncMode("full_refresh");
            task.setStatus("draft");
            return taskRepository.saveAndFlush(task).getId();
        });
        assertThat(taskId).isNotNull();

        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirstLock = new CountDownLatch(1);
        CountDownLatch secondAttemptStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> first = executor.submit(() ->
            transaction.executeWithoutResult(status -> {
                taskRepository.findByIdForUpdate(taskId).orElseThrow();
                firstLockAcquired.countDown();
                await(releaseFirstLock);
            })
        );

        try {
            assertThat(firstLockAcquired.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> second = executor.submit(() ->
                transaction.executeWithoutResult(status -> {
                    secondAttemptStarted.countDown();
                    taskRepository.findByIdForUpdate(taskId).orElseThrow();
                })
            );
            assertThat(secondAttemptStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(250, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);

            releaseFirstLock.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            releaseFirstLock.countDown();
            executor.shutdownNow();
        }
    }

    private TransactionTemplate requiresNewTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction;
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release pessimistic lock");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while holding pessimistic lock", ex);
        }
    }
}
