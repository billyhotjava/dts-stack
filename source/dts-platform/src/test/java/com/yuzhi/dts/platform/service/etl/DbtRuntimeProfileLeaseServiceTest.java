package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseCompensationOutcome;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseMutationOutcome;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseMutationResult;
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
    private AtomicReference<Instant> databaseNow;
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
        databaseNow = new AtomicReference<>(NOW);
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
            repository.viewActiveState(any(UUID.class))
        )
            .thenAnswer(invocation -> {
                LeaseRecord current = persisted.get();
                if (
                    current == null ||
                    current.status() == LeaseStatus.RELEASED
                ) {
                    return mutation(
                        LeaseMutationOutcome.NOT_FOUND,
                        current
                    );
                }
                if (
                    current.status() == LeaseStatus.EXPIRED ||
                    !NOW.isBefore(current.expiresAt())
                ) {
                    return mutation(
                        LeaseMutationOutcome.EXPIRED,
                        current
                    );
                }
                return mutation(
                    LeaseMutationOutcome.SUCCESS,
                    current
                );
            });
        when(
            repository.compensateMissingProfile(
                any(UUID.class),
                any(LeaseStatus.class),
                any(Instant.class)
            )
        )
            .thenAnswer(invocation -> {
                LeaseRecord current = persisted.get();
                if (current == null) {
                    return LeaseCompensationOutcome.NOT_FOUND;
                }
                if (
                    current.status() == LeaseStatus.EXPIRED ||
                    current.status() == LeaseStatus.RELEASED
                ) {
                    return LeaseCompensationOutcome.ALREADY_TERMINAL;
                }
                persisted.set(
                    withStatus(
                        current,
                        LeaseStatus.EXPIRED,
                        NOW
                    )
                );
                return LeaseCompensationOutcome.COMPENSATED;
            });
        when(repository.findAllByIds(anyList())).thenAnswer(invocation -> {
            LeaseRecord current = persisted.get();
            java.util.List<UUID> ids = invocation.getArgument(0);
            return current != null && ids.contains(current.id())
                ? java.util.List.of(current)
                : java.util.List.of();
        });
        when(
            repository.consumeState(any(UUID.class))
        )
            .thenAnswer(invocation -> {
                LeaseRecord current = persisted.get();
                if (current == null || current.status() == LeaseStatus.RELEASED) {
                    return mutation(
                        LeaseMutationOutcome.NOT_FOUND,
                        current
                    );
                }
                if (current.status() == LeaseStatus.EXPIRED) {
                    return mutation(
                        LeaseMutationOutcome.EXPIRED,
                        current
                    );
                }
                if (current.status() == LeaseStatus.ISSUED) {
                    persisted.set(
                        withStatus(
                            current,
                            LeaseStatus.CONSUMED,
                            NOW
                        )
                    );
                }
                return mutation(
                    LeaseMutationOutcome.SUCCESS,
                    persisted.get()
                );
            });
        when(
            repository.renewState(
                any(UUID.class),
                any(Duration.class)
            )
        )
            .thenAnswer(invocation -> {
                LeaseRecord current = persisted.get();
                if (current == null || current.status() == LeaseStatus.RELEASED) {
                    return mutation(
                        LeaseMutationOutcome.NOT_FOUND,
                        current
                    );
                }
                if (current.status() == LeaseStatus.EXPIRED) {
                    return mutation(
                        LeaseMutationOutcome.EXPIRED,
                        current
                    );
                }
                if (current.status() != LeaseStatus.CONSUMED) {
                    return mutation(
                        LeaseMutationOutcome.NOT_CONSUMED,
                        current
                    );
                }
                persisted.set(
                    withExpiry(
                        current,
                        current
                            .expiresAt()
                            .plus(invocation.getArgument(1))
                    )
                );
                return mutation(
                    LeaseMutationOutcome.SUCCESS,
                    persisted.get()
                );
            });
        doAnswer(invocation -> {
            LeaseRecord current = persisted.get();
            UUID id = invocation.getArgument(0);
            if (
                current == null ||
                !current.id().equals(id) ||
                (current.status() != LeaseStatus.ISSUED &&
                    current.status() != LeaseStatus.CONSUMED)
            ) {
                return false;
            }
            persisted.set(
                withStatus(
                    current,
                    LeaseStatus.RELEASED,
                    invocation.getArgument(1)
                )
            );
            return true;
        })
            .when(repository)
            .release(any(UUID.class), any(Instant.class));
        doAnswer(invocation -> {
            LeaseRecord current = persisted.get();
            UUID id = invocation.getArgument(0);
            Instant expectedExpiresAt = invocation.getArgument(1);
            if (
                current == null ||
                !current.id().equals(id) ||
                (current.status() != LeaseStatus.ISSUED &&
                    current.status() != LeaseStatus.CONSUMED) ||
                !current.expiresAt().equals(expectedExpiresAt) ||
                current.expiresAt().isAfter(databaseNow.get())
            ) {
                return false;
            }
            persisted.set(
                withStatus(
                    current,
                    LeaseStatus.EXPIRED,
                    databaseNow.get()
                )
            );
            return true;
        })
            .when(repository)
            .expire(
                any(UUID.class),
                any(Instant.class)
            );
        when(
            repository.findExpired(anyInt())
        )
            .thenAnswer(invocation -> {
                LeaseRecord current = persisted.get();
                boolean activeAndDue =
                    current != null &&
                    (current.status() == LeaseStatus.ISSUED ||
                        current.status() == LeaseStatus.CONSUMED) &&
                    !databaseNow.get().isBefore(current.expiresAt());
                return activeAndDue
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
        assertThat(service.consume(LEASE_ID)).isEqualTo(lease);

        service.release(LEASE_ID);
        service.release(LEASE_ID);
        assertThat(leaseDir).doesNotExist();
    }

    @Test
    void databaseAdmissionWinsWhenJvmClockRunsAheadDuringConsume() {
        var issued = service.issue(
            new DbtRuntimeProfileLeaseService.LeaseRequest(
                "tenant-a",
                PIPELINE_RUN_ID,
                DAG_RUN_ID,
                "DEV",
                "postgres-primary"
            )
        );
        service = new DbtRuntimeProfileLeaseService(
            properties,
            targetFactory,
            repository,
            UUID::randomUUID,
            Clock.fixed(NOW.plus(Duration.ofHours(1)), ZoneOffset.UTC)
        );

        assertThat(service.consume(LEASE_ID)).isEqualTo(issued);
        assertThat(persisted.get().status())
            .isEqualTo(LeaseStatus.CONSUMED);
    }

    @Test
    void databaseAdmissionWinsWhenJvmClockRunsAheadDuringRenew() {
        service.issue(
            new DbtRuntimeProfileLeaseService.LeaseRequest(
                "tenant-a",
                PIPELINE_RUN_ID,
                DAG_RUN_ID,
                "DEV",
                "postgres-primary"
            )
        );
        service.consume(LEASE_ID);
        service = new DbtRuntimeProfileLeaseService(
            properties,
            targetFactory,
            repository,
            UUID::randomUUID,
            Clock.fixed(NOW.plus(Duration.ofHours(1)), ZoneOffset.UTC)
        );

        assertThat(service.renew(LEASE_ID).expiresAt())
            .isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(persisted.get().status())
            .isEqualTo(LeaseStatus.CONSUMED);
    }

    @Test
    void databaseInspectionRejectsAnExpiredRecoveryBeforeJanitorRuns() {
        service.issue(
            new DbtRuntimeProfileLeaseService.LeaseRequest(
                "tenant-a",
                PIPELINE_RUN_ID,
                DAG_RUN_ID,
                "DEV",
                "postgres-primary"
            )
        );
        persisted.set(
            withExpiry(
                persisted.get(),
                NOW.minus(Duration.ofSeconds(1))
            )
        );

        assertThatThrownBy(() -> service.viewActive(LEASE_ID))
            .isInstanceOf(DbtRuntimeProfileException.class)
            .extracting(error ->
                ((DbtRuntimeProfileException) error).code()
            )
            .isEqualTo("DBT_PROFILE_LEASE_EXPIRED");
    }

    @Test
    void databaseInspectionWinsWhenJvmClockRunsAheadDuringRecovery() {
        var issued = service.issue(
            new DbtRuntimeProfileLeaseService.LeaseRequest(
                "tenant-a",
                PIPELINE_RUN_ID,
                DAG_RUN_ID,
                "DEV",
                "postgres-primary"
            )
        );
        service = new DbtRuntimeProfileLeaseService(
            properties,
            targetFactory,
            repository,
            UUID::randomUUID,
            Clock.fixed(NOW.plus(Duration.ofHours(1)), ZoneOffset.UTC)
        );

        assertThat(service.viewActive(LEASE_ID)).isEqualTo(issued);
    }

    @Test
    void consumeCompensatesWhenProfileDisappearsAfterPrecheck()
        throws Exception {
        service.issue(
            new DbtRuntimeProfileLeaseService.LeaseRequest(
                "tenant-a",
                PIPELINE_RUN_ID,
                DAG_RUN_ID,
                "DEV",
                "postgres-primary"
            )
        );
        Path profile = runtimeRoot
            .resolve(LEASE_ID.toString())
            .resolve("profiles.yml");
        when(repository.consumeState(LEASE_ID)).thenAnswer(invocation -> {
            LeaseRecord consumed = withStatus(
                persisted.get(),
                LeaseStatus.CONSUMED,
                NOW
            );
            persisted.set(consumed);
            Files.delete(profile);
            return mutation(
                LeaseMutationOutcome.SUCCESS,
                consumed
            );
        });

        assertThatThrownBy(() -> service.consume(LEASE_ID))
            .isInstanceOf(DbtRuntimeProfileException.class)
            .extracting(error ->
                ((DbtRuntimeProfileException) error).code()
            )
            .isEqualTo("DBT_PROFILE_LEASE_FILE_MISSING");
        assertThat(persisted.get().status())
            .isEqualTo(LeaseStatus.EXPIRED);
    }

    @Test
    void renewCompensatesWhenProfileDisappearsAfterPrecheck()
        throws Exception {
        service.issue(
            new DbtRuntimeProfileLeaseService.LeaseRequest(
                "tenant-a",
                PIPELINE_RUN_ID,
                DAG_RUN_ID,
                "DEV",
                "postgres-primary"
            )
        );
        service.consume(LEASE_ID);
        Path profile = runtimeRoot
            .resolve(LEASE_ID.toString())
            .resolve("profiles.yml");
        when(
            repository.renewState(
                LEASE_ID,
                Duration.ofMinutes(5)
            )
        )
            .thenAnswer(invocation -> {
                LeaseRecord renewed = withExpiry(
                    persisted.get(),
                    persisted
                        .get()
                        .expiresAt()
                        .plus(invocation.getArgument(1))
                );
                persisted.set(renewed);
                Files.delete(profile);
                return mutation(
                    LeaseMutationOutcome.SUCCESS,
                    renewed
                );
            });

        assertThatThrownBy(() -> service.renew(LEASE_ID))
            .isInstanceOf(DbtRuntimeProfileException.class)
            .extracting(error ->
                ((DbtRuntimeProfileException) error).code()
            )
            .isEqualTo("DBT_PROFILE_LEASE_FILE_MISSING");
        assertThat(persisted.get().status())
            .isEqualTo(LeaseStatus.EXPIRED);
    }

    @Test
    void releaseCasFailureNeverDeletesOrClaimsSuccessForAnUnclaimedProfile() {
        service.issue(
            new DbtRuntimeProfileLeaseService.LeaseRequest(
                "tenant-a",
                PIPELINE_RUN_ID,
                DAG_RUN_ID,
                "DEV",
                "postgres-primary"
            )
        );
        doAnswer(invocation -> false)
            .when(repository)
            .release(LEASE_ID, NOW);

        assertThatThrownBy(() -> service.release(LEASE_ID))
            .isInstanceOf(DbtRuntimeProfileException.class)
            .extracting(error ->
                ((DbtRuntimeProfileException) error).code()
            )
            .isEqualTo("DBT_PROFILE_LEASE_RELEASE_FAILED");

        assertThat(runtimeRoot.resolve(LEASE_ID.toString())).exists();
        assertThat(persisted.get().status())
            .isEqualTo(LeaseStatus.ISSUED);
    }

    @Test
    void releaseDeletesTheProfileOnlyAfterItsMetadataClaimSucceeds() {
        service.issue(
            new DbtRuntimeProfileLeaseService.LeaseRequest(
                "tenant-a",
                PIPELINE_RUN_ID,
                DAG_RUN_ID,
                "DEV",
                "postgres-primary"
            )
        );
        Path leaseDirectory = runtimeRoot.resolve(LEASE_ID.toString());
        doAnswer(invocation -> {
                assertThat(leaseDirectory).exists();
                persisted.set(
                    withStatus(
                        persisted.get(),
                        LeaseStatus.RELEASED,
                        invocation.getArgument(1)
                    )
                );
                return true;
            })
            .when(repository)
            .release(LEASE_ID, NOW);

        service.release(LEASE_ID);

        assertThat(leaseDirectory).doesNotExist();
        assertThat(persisted.get().status())
            .isEqualTo(LeaseStatus.RELEASED);
    }

    @Test
    void repeatedReleaseRetriesAProfileDeletionThatFailedAfterTheClaim()
        throws Exception {
        service.issue(
            new DbtRuntimeProfileLeaseService.LeaseRequest(
                "tenant-a",
                PIPELINE_RUN_ID,
                DAG_RUN_ID,
                "DEV",
                "postgres-primary"
            )
        );
        Path leaseDirectory = runtimeRoot.resolve(LEASE_ID.toString());
        Path blocker = leaseDirectory.resolve("transient-blocker");
        Files.writeString(blocker, "retry-me");

        assertThatThrownBy(() -> service.release(LEASE_ID))
            .isInstanceOf(DbtRuntimeProfileException.class)
            .extracting(error ->
                ((DbtRuntimeProfileException) error).code()
            )
            .isEqualTo("DBT_PROFILE_LEASE_RELEASE_FAILED");
        assertThat(persisted.get().status())
            .isEqualTo(LeaseStatus.RELEASED);
        assertThat(leaseDirectory).exists();

        Files.delete(blocker);
        service.release(LEASE_ID);

        assertThat(leaseDirectory).doesNotExist();
    }

    @Test
    void renewsAConsumedLeaseSoTheJanitorCannotDeleteAnActiveProfile() {
        var issued = service.issue(
            new DbtRuntimeProfileLeaseService.LeaseRequest(
                "tenant-a",
                PIPELINE_RUN_ID,
                DAG_RUN_ID,
                "DEV",
                "postgres-primary"
            )
        );
        service.consume(LEASE_ID);
        Instant databaseRenewedAt = NOW.plus(Duration.ofMinutes(4));
        when(repository.renewState(LEASE_ID, Duration.ofMinutes(5)))
            .thenAnswer(invocation -> {
                persisted.set(
                    withExpiry(
                        persisted.get(),
                        databaseRenewedAt.plus(invocation.getArgument(1))
                    )
                );
                return mutation(
                    LeaseMutationOutcome.SUCCESS,
                    persisted.get()
                );
            });

        service = new DbtRuntimeProfileLeaseService(
            properties,
            targetFactory,
            repository,
            () -> UUID.randomUUID(),
            Clock.fixed(NOW.plus(Duration.ofMinutes(4)), ZoneOffset.UTC)
        );
        var renewed = service.renew(LEASE_ID);

        assertThat(renewed.profileLeaseId()).isEqualTo(issued.profileLeaseId());
        assertThat(renewed.expiresAt())
            .isEqualTo(NOW.plus(Duration.ofMinutes(9)));

        service = new DbtRuntimeProfileLeaseService(
            properties,
            targetFactory,
            repository,
            () -> UUID.randomUUID(),
            Clock.fixed(NOW.plus(Duration.ofMinutes(6)), ZoneOffset.UTC)
        );
        service.cleanupExpired();

        assertThat(runtimeRoot.resolve(LEASE_ID.toString())).exists();
        assertThat(service.viewActive(LEASE_ID).expiresAt())
            .isEqualTo(renewed.expiresAt());
    }

    @Test
    void staleJanitorCandidateNeverDeletesAProfileRenewedBeforeItsClaim() {
        service.issue(
            new DbtRuntimeProfileLeaseService.LeaseRequest(
                "tenant-a",
                PIPELINE_RUN_ID,
                DAG_RUN_ID,
                "DEV",
                "postgres-primary"
            )
        );
        service.consume(LEASE_ID);
        LeaseRecord staleCandidate = persisted.get();
        Instant janitorCutoff = NOW.plus(Duration.ofMinutes(6));
        Instant renewedExpiry = NOW.plus(Duration.ofMinutes(9));
        databaseNow.set(janitorCutoff);
        when(repository.findExpired(100))
            .thenReturn(java.util.List.of(staleCandidate));
        doAnswer(invocation -> {
                persisted.set(
                    withExpiry(persisted.get(), renewedExpiry)
                );
                return false;
            })
            .when(repository)
            .expire(
                LEASE_ID,
                staleCandidate.expiresAt()
            );
        service = new DbtRuntimeProfileLeaseService(
            properties,
            targetFactory,
            repository,
            UUID::randomUUID,
            Clock.fixed(janitorCutoff, ZoneOffset.UTC)
        );

        service.cleanupExpired();

        assertThat(runtimeRoot.resolve(LEASE_ID.toString())).exists();
        assertThat(persisted.get().status())
            .isEqualTo(LeaseStatus.CONSUMED);
        assertThat(persisted.get().expiresAt())
            .isEqualTo(renewedExpiry);
    }

    @Test
    void removesAProfileLeftBehindAfterAnExpirationClaim() {
        service.issue(
            new DbtRuntimeProfileLeaseService.LeaseRequest(
                "tenant-a",
                PIPELINE_RUN_ID,
                DAG_RUN_ID,
                "DEV",
                "postgres-primary"
            )
        );
        persisted.set(
            withStatus(
                persisted.get(),
                LeaseStatus.EXPIRED,
                NOW.plus(Duration.ofMinutes(5))
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
        Instant janitorCutoff = NOW.plus(Duration.ofMinutes(6));
        service = new DbtRuntimeProfileLeaseService(
            properties,
            targetFactory,
            repository,
            UUID::randomUUID,
            Clock.fixed(janitorCutoff, ZoneOffset.UTC)
        );

        service.cleanupExpired();

        assertThat(runtimeRoot.resolve(LEASE_ID.toString()))
            .doesNotExist();
        assertThat(persisted.get().releasedAt())
            .isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void janitorImmediatelyRetriesTrackedTerminalProfileDirectories()
        throws Exception {
        service.issue(
            new DbtRuntimeProfileLeaseService.LeaseRequest(
                "tenant-a",
                PIPELINE_RUN_ID,
                DAG_RUN_ID,
                "DEV",
                "postgres-primary"
            )
        );
        persisted.set(
            withStatus(
                persisted.get(),
                LeaseStatus.RELEASED,
                NOW
            )
        );
        Path leaseDirectory = runtimeRoot.resolve(LEASE_ID.toString());
        Files.setLastModifiedTime(
            leaseDirectory,
            FileTime.from(NOW)
        );

        service.cleanupExpired();

        assertThat(leaseDirectory).doesNotExist();
    }

    @Test
    void janitorKeepsTtlForUntrackedAndInvalidDirectories()
        throws Exception {
        Path agedUntracked = runtimeRoot.resolve(
            "40000000-0000-0000-0000-000000000001"
        );
        Path freshUntracked = runtimeRoot.resolve(
            "40000000-0000-0000-0000-000000000002"
        );
        Path agedInvalid = runtimeRoot.resolve(
            "aged-invalid-lease-directory"
        );
        Path freshInvalid = runtimeRoot.resolve(
            "fresh-invalid-lease-directory"
        );
        Files.createDirectory(agedUntracked);
        Files.createDirectory(freshUntracked);
        Files.createDirectory(agedInvalid);
        Files.createDirectory(freshInvalid);
        Files.setLastModifiedTime(
            agedUntracked,
            FileTime.from(NOW.minus(Duration.ofMinutes(6)))
        );
        Files.setLastModifiedTime(
            agedInvalid,
            FileTime.from(NOW.minus(Duration.ofMinutes(6)))
        );
        Files.setLastModifiedTime(freshUntracked, FileTime.from(NOW));
        Files.setLastModifiedTime(freshInvalid, FileTime.from(NOW));

        service.cleanupExpired();

        assertThat(agedUntracked).doesNotExist();
        assertThat(agedInvalid).doesNotExist();
        assertThat(freshUntracked).exists();
        assertThat(freshInvalid).exists();
    }

    @Test
    void janitorProcessesOneBoundedDirectoryBatchAndContinuesNextPass()
        throws Exception {
        for (int index = 1; index <= 101; index++) {
            Path orphan = runtimeRoot.resolve(
                new UUID(0, index).toString()
            );
            Files.createDirectory(orphan);
            Files.setLastModifiedTime(
                orphan,
                FileTime.from(NOW.minus(Duration.ofMinutes(6)))
            );
        }

        service.cleanupExpired();

        try (var remaining = Files.list(runtimeRoot)) {
            assertThat(remaining.count()).isEqualTo(1);
        }
        verify(repository).findAllByIds(anyList());
        verify(repository, never()).find(any(UUID.class));

        service.cleanupExpired();

        try (var remaining = Files.list(runtimeRoot)) {
            assertThat(remaining.count()).isZero();
        }
        verify(repository, times(2)).findAllByIds(anyList());
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
    void janitorDoesNotExpireDatabaseActiveLeaseWhenJvmRunsAhead() {
        service.issue(
            new DbtRuntimeProfileLeaseService.LeaseRequest(
                "tenant-a",
                PIPELINE_RUN_ID,
                DAG_RUN_ID,
                "DEV",
                "postgres-primary"
            )
        );
        service = new DbtRuntimeProfileLeaseService(
            properties,
            targetFactory,
            repository,
            UUID::randomUUID,
            Clock.fixed(NOW.plus(Duration.ofHours(1)), ZoneOffset.UTC)
        );

        service.cleanupExpired();

        assertThat(runtimeRoot.resolve(LEASE_ID.toString())).exists();
        assertThat(persisted.get().status())
            .isEqualTo(LeaseStatus.ISSUED);
        verify(repository).findExpired(100);
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
        databaseNow.set(NOW.plus(Duration.ofMinutes(6)));
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

    private static LeaseRecord withExpiry(
        LeaseRecord current,
        Instant expiresAt
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
            current.status(),
            current.issuedAt(),
            expiresAt,
            current.consumedAt(),
            current.releasedAt()
        );
    }

    private static LeaseMutationResult mutation(
        LeaseMutationOutcome outcome,
        LeaseRecord lease
    ) {
        return new LeaseMutationResult(outcome, lease);
    }
}
