package com.yuzhi.dts.platform.service.etl;

import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseCompensationOutcome;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseMutationOutcome;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseMutationResult;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseRecord;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseStatus;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Issues short-lived dbt profiles under one server-owned runtime root.
 *
 * <p>No path or warehouse credential is returned to callers. Airflow derives its read-only host
 * mount from a separately configured host root plus the strict UUID lease id.
 */
@Service
public class DbtRuntimeProfileLeaseService {

    private final ModelMaterializationProperties properties;
    private final DbtTargetConnectionFactory targetFactory;
    private final DbtRuntimeProfileLeaseRepository repository;
    private final Supplier<UUID> leaseIdGenerator;
    private final Clock clock;
    private final DbtRuntimeProfileLeaseFileStore fileStore;
    private final DbtRuntimeProfileLeaseJanitor janitor;

    @Autowired
    public DbtRuntimeProfileLeaseService(
        ModelMaterializationProperties properties,
        DbtTargetConnectionFactory targetFactory,
        DbtRuntimeProfileLeaseRepository repository,
        DbtRuntimeProfileLeaseFileStore fileStore,
        DbtRuntimeProfileLeaseJanitor janitor
    ) {
        this.properties = Objects.requireNonNull(
            properties,
            "properties is required"
        );
        this.targetFactory = Objects.requireNonNull(
            targetFactory,
            "targetFactory is required"
        );
        this.repository = Objects.requireNonNull(
            repository,
            "repository is required"
        );
        this.fileStore = Objects.requireNonNull(
            fileStore,
            "fileStore is required"
        );
        this.janitor = Objects.requireNonNull(
            janitor,
            "janitor is required"
        );
        this.leaseIdGenerator = UUID::randomUUID;
        this.clock = Clock.systemUTC();
    }

    public DbtRuntimeProfileLeaseService(
        ModelMaterializationProperties properties,
        DbtTargetConnectionFactory targetFactory,
        DbtRuntimeProfileLeaseRepository repository
    ) {
        this(
            properties,
            targetFactory,
            repository,
            UUID::randomUUID,
            Clock.systemUTC()
        );
    }

    DbtRuntimeProfileLeaseService(
        ModelMaterializationProperties properties,
        DbtTargetConnectionFactory targetFactory,
        DbtRuntimeProfileLeaseRepository repository,
        Supplier<UUID> leaseIdGenerator,
        Clock clock
    ) {
        this.properties = Objects.requireNonNull(
            properties,
            "properties is required"
        );
        this.targetFactory = Objects.requireNonNull(
            targetFactory,
            "targetFactory is required"
        );
        this.repository = Objects.requireNonNull(
            repository,
            "repository is required"
        );
        this.leaseIdGenerator = Objects.requireNonNull(
            leaseIdGenerator,
            "leaseIdGenerator is required"
        );
        this.clock = Objects.requireNonNull(clock, "clock is required");
        this.fileStore = new DbtRuntimeProfileLeaseFileStore(
            properties
        );
        this.janitor = new DbtRuntimeProfileLeaseJanitor(
            properties,
            repository,
            fileStore,
            clock
        );
    }

    public LeaseView issue(LeaseRequest request) {
        Objects.requireNonNull(request, "request is required");
        String expectedTarget = required(
            properties.getExecutionTargetKey(),
            "execution target"
        );
        if (!expectedTarget.equals(request.executionTargetKey())) {
            throw error(
                "MODEL_EXECUTION_TARGET_UNAVAILABLE",
                "Requested execution target is unavailable"
            );
        }
        requireReady();

        DbtTargetConnectionFactory.RuntimeTarget target;
        try {
            target = targetFactory.resolveRuntimeTarget();
        } catch (DbtRuntimeTargetException failure) {
            throw new DbtRuntimeProfileException(failure.code(), failure.getMessage(), failure);
        } catch (RuntimeException failure) {
            throw new DbtRuntimeProfileException(
                "DBT_EXECUTION_TARGET_SECRET_UNAVAILABLE",
                "Runtime target credential is unavailable",
                failure
            );
        }
        requirePostgres(target);
        JdbcEndpoint endpoint = JdbcEndpoint.parse(target.jdbcUrl());
        String profileKey = identifier(
            properties.getProfileKey(),
            "profile key"
        );
        String targetName = identifier(
            properties.getTargetName(),
            "target name"
        );
        Duration ttl = leaseTtl();
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(ttl);
        UUID leaseId = Objects.requireNonNull(
            leaseIdGenerator.get(),
            "generated lease id is required"
        );
        LeaseView view = new LeaseView(
            leaseId,
            targetName,
            expiresAt,
            required(
                target.credentialVersionRef(),
                "credential version"
            )
        );
        fileStore.createProfile(
            leaseId,
            profileYaml(
                profileKey,
                targetName,
                target,
                endpoint
            )
        );
        try {
            repository.issue(
                new LeaseRecord(
                    leaseId,
                    request.tenantId(),
                    request.pipelineRunId(),
                    request.dagRunId(),
                    request.environment(),
                    request.executionTargetKey(),
                    targetName,
                    view.credentialVersionRef(),
                    LeaseStatus.ISSUED,
                    issuedAt,
                    expiresAt,
                    null,
                    null
                )
            );
            return view;
        } catch (RuntimeException failure) {
            fileStore.deleteLeaseDirectory(leaseId, false);
            throw new DbtRuntimeProfileException(
                "DBT_PROFILE_LEASE_METADATA_WRITE_FAILED",
                "Runtime profile lease metadata could not be persisted",
                failure
            );
        }
    }

    public LeaseView consume(UUID leaseId) {
        UUID id = requiredLeaseId(leaseId);
        inspectActive(id);
        requireProfilePresent(id);
        LeaseMutationResult result = repository.consumeState(id);
        if (result.outcome() == LeaseMutationOutcome.NOT_FOUND) {
            throw error(
                "DBT_PROFILE_LEASE_NOT_FOUND",
                "Runtime profile lease does not exist"
            );
        }
        if (result.outcome() == LeaseMutationOutcome.EXPIRED) {
            fileStore.deleteLeaseDirectory(id, false);
            throw error(
                "DBT_PROFILE_LEASE_EXPIRED",
                "Runtime profile lease has expired"
            );
        }
        LeaseRecord current = result.lease();
        if (
            result.outcome() != LeaseMutationOutcome.SUCCESS ||
            current == null
        ) {
            throw error(
                "DBT_PROFILE_LEASE_NOT_FOUND",
                "Runtime profile lease does not exist"
            );
        }
        compensateIfProfileDisappeared(id, current);
        return view(current);
    }

    /** Extends one active consumed lease so the janitor cannot remove a profile while dbt is still running. */
    public LeaseView renew(UUID leaseId) {
        UUID id = requiredLeaseId(leaseId);
        LeaseRecord inspected = inspectActive(id);
        if (inspected.status() != LeaseStatus.CONSUMED) {
            throw error(
                "DBT_PROFILE_LEASE_NOT_CONSUMED",
                "Runtime profile lease has not been consumed"
            );
        }
        requireProfilePresent(id);
        LeaseMutationResult result = repository.renewState(
            id,
            leaseTtl()
        );
        if (result.outcome() == LeaseMutationOutcome.NOT_FOUND) {
            throw error(
                "DBT_PROFILE_LEASE_NOT_FOUND",
                "Runtime profile lease does not exist"
            );
        }
        if (result.outcome() == LeaseMutationOutcome.EXPIRED) {
            fileStore.deleteLeaseDirectory(id, false);
            throw error(
                "DBT_PROFILE_LEASE_EXPIRED",
                "Runtime profile lease has expired"
            );
        }
        if (result.outcome() == LeaseMutationOutcome.NOT_CONSUMED) {
            throw error(
                "DBT_PROFILE_LEASE_NOT_CONSUMED",
                "Runtime profile lease has not been consumed"
            );
        }
        LeaseRecord renewed = result.lease();
        if (renewed == null) {
            throw error(
                "DBT_PROFILE_LEASE_RENEW_FAILED",
                "Runtime profile lease could not be renewed"
            );
        }
        compensateIfProfileDisappeared(id, renewed);
        if (renewed.status() != LeaseStatus.CONSUMED) {
            throw error(
                "DBT_PROFILE_LEASE_RENEW_FAILED",
                "Runtime profile lease could not be renewed"
            );
        }
        return view(renewed);
    }

    /** Returns the same non-sensitive lease identity for idempotent runtime-spec recovery. */
    public LeaseView viewActive(UUID leaseId) {
        UUID id = requiredLeaseId(leaseId);
        return view(inspectActive(id));
    }

    public void release(UUID leaseId) {
        UUID id = requiredLeaseId(leaseId);
        boolean released = repository.release(id, clock.instant());
        if (!released) {
            LeaseRecord current = repository.find(id).orElse(null);
            if (
                current == null ||
                (current.status() != LeaseStatus.RELEASED &&
                    current.status() != LeaseStatus.EXPIRED)
            ) {
                if (!fileStore.leaseDirectoryExists(id)) {
                    return;
                }
                throw error(
                    "DBT_PROFILE_LEASE_RELEASE_FAILED",
                    "Runtime profile lease release could not be confirmed"
                );
            }
        }
        fileStore.deleteLeaseDirectory(id, true);
    }

    public Readiness readiness() {
        DbtRuntimeProfileLeaseFileStore.Readiness readiness =
            fileStore.readiness();
        return new Readiness(readiness.ready(), readiness.code());
    }

    public void cleanupExpired() {
        janitor.cleanupExpired();
    }

    private void requireReady() {
        Readiness readiness = readiness();
        if (!readiness.ready()) {
            throw error(
                readiness.code(),
                "Runtime profile root is not ready"
            );
        }
    }

    private void requirePostgres(
        DbtTargetConnectionFactory.RuntimeTarget target
    ) {
        if (
            target == null ||
            !StringUtils.hasText(target.type()) ||
            !target
                .type()
                .toLowerCase(Locale.ROOT)
                .contains("postgres") ||
            !StringUtils.hasText(target.username()) ||
            !StringUtils.hasText(target.password())
        ) {
            throw error(
                "MODEL_EXECUTION_TARGET_UNAVAILABLE",
                "Only the configured PostgreSQL runtime target is available"
            );
        }
    }

    private Duration leaseTtl() {
        Duration ttl = properties.getRuntimeProfileLeaseTtl();
        if (
            ttl == null ||
            ttl.isZero() ||
            ttl.isNegative() ||
            ttl.compareTo(Duration.ofHours(1)) > 0
        ) {
            throw error(
                "DBT_PROFILE_LEASE_TTL_INVALID",
                "Runtime profile lease TTL must be between one nanosecond and one hour"
            );
        }
        return ttl;
    }

    private String profileYaml(
        String profileKey,
        String targetName,
        DbtTargetConnectionFactory.RuntimeTarget target,
        JdbcEndpoint endpoint
    ) {
        String database = StringUtils.hasText(target.database())
            ? target.database().trim()
            : endpoint.database();
        String schema = StringUtils.hasText(target.schema())
            ? target.schema().trim()
            : "public";
        return (
            profileKey +
            ":\n" +
            "  target: " +
            targetName +
            "\n" +
            "  outputs:\n" +
            "    " +
            targetName +
            ":\n" +
            "      type: postgres\n" +
            "      host: " +
            yamlString(endpoint.host()) +
            "\n" +
            "      port: " +
            endpoint.port() +
            "\n" +
            "      user: " +
            yamlString(target.username()) +
            "\n" +
            "      password: " +
            yamlString(target.password()) +
            "\n" +
            "      dbname: " +
            yamlString(required(database, "database")) +
            "\n" +
            "      schema: " +
            yamlString(required(schema, "schema")) +
            "\n" +
            "      threads: 4\n"
        );
    }

    private static String yamlString(String value) {
        String text = required(value, "profile value");
        return (
            "\"" +
            text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t") +
            "\""
        );
    }

    private static String identifier(String value, String name) {
        String text = required(value, name);
        if (!text.matches("^[A-Za-z_][A-Za-z0-9_-]{0,127}$")) {
            throw error(
                "DBT_RUNTIME_PROFILE_IDENTIFIER_INVALID",
                name + " is invalid"
            );
        }
        return text;
    }

    private static String required(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw error(
                "DBT_RUNTIME_PROFILE_CONFIGURATION_INVALID",
                name + " is required"
            );
        }
        return value.trim();
    }

    private static String bounded(
        String value,
        String name,
        int maxLength
    ) {
        String text = required(value, name);
        if (text.length() > maxLength) {
            throw error(
                "DBT_RUNTIME_PROFILE_CONFIGURATION_INVALID",
                name + " exceeds the supported length"
            );
        }
        return text;
    }

    private static UUID requiredLeaseId(UUID leaseId) {
        if (leaseId == null) {
            throw error(
                "DBT_PROFILE_LEASE_ID_INVALID",
                "Runtime profile lease id is required"
            );
        }
        return leaseId;
    }

    private LeaseRecord inspectActive(UUID leaseId) {
        LeaseMutationResult result = repository.viewActiveState(
            leaseId
        );
        if (result.outcome() == LeaseMutationOutcome.NOT_FOUND) {
            throw error(
                "DBT_PROFILE_LEASE_NOT_FOUND",
                "Runtime profile lease does not exist"
            );
        }
        if (result.outcome() == LeaseMutationOutcome.EXPIRED) {
            fileStore.deleteLeaseDirectory(leaseId, false);
            throw error(
                "DBT_PROFILE_LEASE_EXPIRED",
                "Runtime profile lease has expired"
            );
        }
        LeaseRecord lease = result.lease();
        if (
            result.outcome() != LeaseMutationOutcome.SUCCESS ||
            lease == null
        ) {
            throw error(
                "DBT_PROFILE_LEASE_NOT_FOUND",
                "Runtime profile lease does not exist"
            );
        }
        return lease;
    }

    private void requireProfilePresent(UUID leaseId) {
        if (!fileStore.profileExists(leaseId)) {
            throw fileMissing();
        }
    }

    private void compensateIfProfileDisappeared(
        UUID leaseId,
        LeaseRecord lease
    ) {
        if (fileStore.profileExists(leaseId)) {
            return;
        }
        LeaseCompensationOutcome outcome =
            repository.compensateMissingProfile(
                leaseId,
                lease.status(),
                lease.expiresAt()
            );
        if (outcome == LeaseCompensationOutcome.CONFLICT) {
            throw error(
                "DBT_PROFILE_LEASE_COMPENSATION_FAILED",
                "Runtime profile lease file loss could not be compensated"
            );
        }
        throw fileMissing();
    }

    private static DbtRuntimeProfileException fileMissing() {
        return error(
            "DBT_PROFILE_LEASE_FILE_MISSING",
            "Runtime profile lease file is unavailable"
        );
    }

    private static DbtRuntimeProfileException error(
        String code,
        String message
    ) {
        return new DbtRuntimeProfileException(code, message);
    }

    private static LeaseView view(LeaseRecord lease) {
        return new LeaseView(
            lease.id(),
            lease.targetName(),
            lease.expiresAt(),
            lease.credentialVersionRef()
        );
    }

    private record JdbcEndpoint(
        String host,
        int port,
        String database
    ) {
        private static JdbcEndpoint parse(String jdbcUrl) {
            if (
                !StringUtils.hasText(jdbcUrl) ||
                !jdbcUrl
                    .toLowerCase(Locale.ROOT)
                    .startsWith("jdbc:postgresql://")
            ) {
                throw error(
                    "MODEL_EXECUTION_TARGET_UNAVAILABLE",
                    "Configured runtime target is not PostgreSQL"
                );
            }
            try {
                URI uri = URI.create(
                    jdbcUrl.trim().substring("jdbc:".length())
                );
                String path = uri.getPath();
                String database =
                    path == null || path.length() < 2
                        ? null
                        : path.substring(1);
                if (!StringUtils.hasText(uri.getHost())) {
                    throw new IllegalArgumentException("host missing");
                }
                return new JdbcEndpoint(
                    uri.getHost(),
                    uri.getPort() > 0 ? uri.getPort() : 5432,
                    database
                );
            } catch (RuntimeException failure) {
                throw new DbtRuntimeProfileException(
                    "MODEL_EXECUTION_TARGET_UNAVAILABLE",
                    "Configured PostgreSQL JDBC endpoint is invalid",
                    failure
                );
            }
        }
    }

    public record LeaseRequest(
        String tenantId,
        UUID pipelineRunId,
        String dagRunId,
        String environment,
        String executionTargetKey
    ) {
        public LeaseRequest {
            tenantId = bounded(tenantId, "tenantId", 128);
            Objects.requireNonNull(
                pipelineRunId,
                "pipelineRunId is required"
            );
            dagRunId = bounded(dagRunId, "dagRunId", 256);
            environment = bounded(
                environment,
                "environment",
                64
            )
                .toUpperCase(Locale.ROOT);
            if (!Set.of("DEV", "TEST", "PROD").contains(environment)) {
                throw error(
                    "MODEL_ENVIRONMENT_UNSUPPORTED",
                    "Runtime profile environment is unsupported"
                );
            }
            executionTargetKey = bounded(
                executionTargetKey,
                "executionTargetKey",
                256
            );
        }
    }

    public record LeaseView(
        UUID profileLeaseId,
        String targetName,
        Instant expiresAt,
        String credentialVersionRef
    ) {
        public LeaseView {
            Objects.requireNonNull(
                profileLeaseId,
                "profileLeaseId is required"
            );
            targetName = required(targetName, "targetName");
            Objects.requireNonNull(
                expiresAt,
                "expiresAt is required"
            );
            credentialVersionRef = required(
                credentialVersionRef,
                "credentialVersionRef"
            );
        }
    }

    public record Readiness(boolean ready, String code) {
        private static Readiness up() {
            return new Readiness(true, "READY");
        }

        private static Readiness down(String code) {
            return new Readiness(false, code);
        }
    }
}
