package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseRecord;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DbtRuntimeProfileLeaseServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-27T13:00:00Z");
    private static final UUID LEASE_ID =
        UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PIPELINE_RUN_ID =
        UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final String DAG_RUN_ID = "manual__candidate-1-v2-a1";
    private static final String PASSWORD = "not-returned:P@ss'word";

    @TempDir
    Path runtimeRoot;

    private ModelMaterializationProperties properties;
    private DbtTargetConnectionFactory targetFactory;
    private DbtRuntimeProfileLeaseRepository repository;
    private AtomicReference<LeaseRecord> persisted;
    private DbtRuntimeProfileLeaseService service;

    @BeforeEach
    void setUp() throws Exception {
        Files.setPosixFilePermissions(
            runtimeRoot,
            PosixFilePermissions.fromString("rwx------")
        );
        properties = new ModelMaterializationProperties();
        properties.setRuntimeProfileRoot(runtimeRoot.toString());
        properties.setRuntimeProfileRequireTmpfs(false);
        properties.setRuntimeProfileLeaseTtl(Duration.ofMinutes(5));
        targetFactory = mock(DbtTargetConnectionFactory.class);
        repository = mock(DbtRuntimeProfileLeaseRepository.class);
        persisted = new AtomicReference<>();
        doAnswer(invocation -> {
            persisted.set(invocation.getArgument(0));
            return null;
        })
            .when(repository)
            .issue(any(LeaseRecord.class));
        when(repository.find(any(UUID.class))).thenAnswer(invocation -> {
            LeaseRecord current = persisted.get();
            UUID id = invocation.getArgument(0);
            return current != null && current.id().equals(id)
                ? java.util.Optional.of(current)
                : java.util.Optional.empty();
        });
        when(
            repository.consume(any(UUID.class), any(Instant.class))
        )
            .thenAnswer(invocation -> {
                LeaseRecord current = persisted.get();
                if (
                    current == null ||
                    current.status() != LeaseStatus.ISSUED
                ) {
                    return false;
                }
                persisted.set(
                    withStatus(
                        current,
                        LeaseStatus.CONSUMED,
                        invocation.getArgument(1)
                    )
                );
                return true;
            });
        doAnswer(invocation -> {
            LeaseRecord current = persisted.get();
            if (current != null) {
                persisted.set(
                    withStatus(
                        current,
                        LeaseStatus.RELEASED,
                        invocation.getArgument(1)
                    )
                );
            }
            return null;
        })
            .when(repository)
            .release(any(UUID.class), any(Instant.class));
        doAnswer(invocation -> {
            LeaseRecord current = persisted.get();
            if (current != null) {
                persisted.set(
                    withStatus(
                        current,
                        LeaseStatus.EXPIRED,
                        invocation.getArgument(1)
                    )
                );
            }
            return null;
        })
            .when(repository)
            .expire(any(UUID.class), any(Instant.class));
        when(
            repository.findExpired(any(Instant.class), anyInt())
        )
            .thenAnswer(invocation -> {
                LeaseRecord current = persisted.get();
                Instant at = invocation.getArgument(0);
                return current != null &&
                    current.status() != LeaseStatus.RELEASED &&
                    current.status() != LeaseStatus.EXPIRED &&
                    !at.isBefore(current.expiresAt())
                    ? java.util.List.of(current)
                    : java.util.List.of();
            });
        when(repository.exists(any(UUID.class))).thenAnswer(invocation -> {
            LeaseRecord current = persisted.get();
            return current != null &&
                current.id().equals(invocation.getArgument(0));
        });
        service = new DbtRuntimeProfileLeaseService(
            properties,
            targetFactory,
            repository,
            () -> LEASE_ID,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(targetFactory.resolveRuntimeTarget()).thenReturn(runtimeTarget());
    }

    @Test
    void writesOnePrivateProfileButReturnsOnlyNonSensitiveLeaseMetadata()
        throws Exception {
        var lease = service.issue(
            new DbtRuntimeProfileLeaseService.LeaseRequest(
                "tenant-a",
                PIPELINE_RUN_ID,
                DAG_RUN_ID,
                "DEV",
                "postgres-primary"
            )
        );

        Path leaseDir = runtimeRoot.resolve(LEASE_ID.toString());
        Path profile = leaseDir.resolve("profiles.yml");
        assertThat(Files.readString(profile))
            .contains(PASSWORD)
            .contains("host: \"dts-pg\"")
            .contains("target: dev");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(leaseDir)))
            .isEqualTo("rwx------");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(profile)))
            .isEqualTo("rw-------");

        assertThat(lease.profileLeaseId()).isEqualTo(LEASE_ID);
        assertThat(lease.targetName()).isEqualTo("dev");
        assertThat(lease.credentialVersionRef()).isEqualTo("credential-v3");
        assertThat(lease.toString())
            .doesNotContain(PASSWORD)
            .doesNotContain(runtimeRoot.toString())
            .doesNotContain("dts-pg")
            .doesNotContain("warehouse_user");

        assertThat(service.consume(LEASE_ID)).isEqualTo(lease);
        assertThatThrownBy(() -> service.consume(LEASE_ID))
            .isInstanceOf(DbtRuntimeProfileException.class)
            .extracting(error -> ((DbtRuntimeProfileException) error).code())
            .isEqualTo("DBT_PROFILE_LEASE_ALREADY_CONSUMED");

        service.release(LEASE_ID);
        service.release(LEASE_ID);
        assertThat(leaseDir).doesNotExist();
    }

    @Test
    void rejectsUnknownTargetBeforeResolvingAnyWarehouseSecret() {
        assertThatThrownBy(() ->
            service.issue(
                new DbtRuntimeProfileLeaseService.LeaseRequest(
                    "tenant-a",
                    PIPELINE_RUN_ID,
                    DAG_RUN_ID,
                    "DEV",
                    "attacker-target"
                )
            )
        )
            .isInstanceOf(DbtRuntimeProfileException.class)
            .extracting(error -> ((DbtRuntimeProfileException) error).code())
            .isEqualTo("MODEL_EXECUTION_TARGET_UNAVAILABLE");

        verify(targetFactory, never()).resolveRuntimeTarget();
    }

    @Test
    void collisionNeverDeletesAnExistingLeaseDirectory() throws Exception {
        Path existing = runtimeRoot.resolve(LEASE_ID.toString());
        Files.createDirectory(existing);
        Files.setPosixFilePermissions(
            existing,
            PosixFilePermissions.fromString("rwx------")
        );
        Path sentinel = existing.resolve("profiles.yml");
        Files.writeString(sentinel, "existing-lease");

        assertThatThrownBy(() ->
            service.issue(
                new DbtRuntimeProfileLeaseService.LeaseRequest(
                    "tenant-a",
                    PIPELINE_RUN_ID,
                    DAG_RUN_ID,
                    "DEV",
                    "postgres-primary"
                )
            )
        )
            .isInstanceOf(DbtRuntimeProfileException.class)
            .extracting(error -> ((DbtRuntimeProfileException) error).code())
            .isEqualTo("DBT_PROFILE_LEASE_ID_COLLISION");
        assertThat(Files.readString(sentinel)).isEqualTo("existing-lease");
    }

    @Test
    void expiresAndDeletesAnUnconsumedLease() {
        service.issue(
            new DbtRuntimeProfileLeaseService.LeaseRequest(
                "tenant-a",
                PIPELINE_RUN_ID,
                DAG_RUN_ID,
                "DEV",
                "postgres-primary"
            )
        );
        try {
            Files.setLastModifiedTime(
                runtimeRoot.resolve(LEASE_ID.toString()),
                FileTime.from(NOW.minus(Duration.ofMinutes(1)))
            );
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
        service = new DbtRuntimeProfileLeaseService(
            properties,
            targetFactory,
            repository,
            () -> UUID.randomUUID(),
            Clock.fixed(NOW.plus(Duration.ofMinutes(6)), ZoneOffset.UTC)
        );

        service.cleanupExpired();

        assertThat(runtimeRoot.resolve(LEASE_ID.toString())).doesNotExist();
        assertThatThrownBy(() -> service.consume(LEASE_ID))
            .isInstanceOf(DbtRuntimeProfileException.class)
            .extracting(error -> ((DbtRuntimeProfileException) error).code())
            .isEqualTo("DBT_PROFILE_LEASE_EXPIRED");
    }

    @Test
    void failsClosedWhenProductionRootIsNotTmpfs() {
        properties.setRuntimeProfileRequireTmpfs(true);
        properties.setRuntimeProfileExpectedUid(
            ((Number) getAttribute(runtimeRoot, "unix:uid")).longValue()
        );
        service = new DbtRuntimeProfileLeaseService(
            properties,
            targetFactory,
            repository,
            () -> LEASE_ID,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThat(service.readiness().ready()).isFalse();
        assertThatThrownBy(() ->
            service.issue(
                new DbtRuntimeProfileLeaseService.LeaseRequest(
                    "tenant-a",
                    PIPELINE_RUN_ID,
                    DAG_RUN_ID,
                    "DEV",
                    "postgres-primary"
                )
            )
        )
            .isInstanceOf(DbtRuntimeProfileException.class)
            .extracting(error -> ((DbtRuntimeProfileException) error).code())
            .isEqualTo("DBT_RUNTIME_PROFILE_ROOT_NOT_TMPFS");
    }

    @Test
    void failsClosedWhenProductionRootOwnerDoesNotMatchConfiguredUid() {
        properties.setRuntimeProfileRequireTmpfs(true);
        long actualUid = ((Number) getAttribute(
            runtimeRoot,
            "unix:uid"
        ))
            .longValue();
        properties.setRuntimeProfileExpectedUid(actualUid + 1);

        assertThat(service.readiness().ready()).isFalse();
        assertThat(service.readiness().code())
            .isEqualTo("DBT_RUNTIME_PROFILE_ROOT_OWNER_INVALID");
    }

    private static Object getAttribute(Path path, String attribute) {
        try {
            return Files.getAttribute(path, attribute);
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static DbtTargetConnectionFactory.RuntimeTarget runtimeTarget() {
        return new DbtTargetConnectionFactory.RuntimeTarget(
            UUID.fromString("30000000-0000-0000-0000-000000000001"),
            "warehouse",
            "finance",
            "postgres",
            "jdbc:postgresql://dts-pg:5432/warehouse",
            "warehouse_user",
            PASSWORD,
            "credential-v3"
        );
    }

    private static LeaseRecord withStatus(
        LeaseRecord current,
        LeaseStatus status,
        Instant at
    ) {
        return new LeaseRecord(
            current.id(),
            current.tenantId(),
            current.pipelineRunId(),
            current.dagRunId(),
            current.environment(),
            current.executionTargetKey(),
            current.targetName(),
            current.credentialVersionRef(),
            status,
            current.issuedAt(),
            current.expiresAt(),
            status == LeaseStatus.CONSUMED
                ? at
                : current.consumedAt(),
            status == LeaseStatus.RELEASED ||
                status == LeaseStatus.EXPIRED
                ? at
                : current.releasedAt()
        );
    }
}
