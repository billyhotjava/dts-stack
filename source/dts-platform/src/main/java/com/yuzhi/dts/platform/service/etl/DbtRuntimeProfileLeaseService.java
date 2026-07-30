package com.yuzhi.dts.platform.service.etl;

import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseRecord;
import com.yuzhi.dts.platform.repository.modeling.DbtRuntimeProfileLeaseRepository.LeaseStatus;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
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

    private static final Set<java.nio.file.attribute.PosixFilePermission>
        ROOT_PERMISSIONS = PosixFilePermissions.fromString("rwx------");
    private static final Set<java.nio.file.attribute.PosixFilePermission>
        PROFILE_PERMISSIONS = PosixFilePermissions.fromString("rw-------");
    private static final String PROFILE_FILE = "profiles.yml";

    private final ModelMaterializationProperties properties;
    private final DbtTargetConnectionFactory targetFactory;
    private final DbtRuntimeProfileLeaseRepository repository;
    private final Supplier<UUID> leaseIdGenerator;
    private final Clock clock;

    @Autowired
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
        Path leaseDirectory = leaseDirectory(leaseId);
        Path profileFile = leaseDirectory.resolve(PROFILE_FILE);
        try {
            Files.createDirectory(
                leaseDirectory,
                PosixFilePermissions.asFileAttribute(ROOT_PERMISSIONS)
            );
        } catch (java.nio.file.FileAlreadyExistsException collision) {
            throw new DbtRuntimeProfileException(
                "DBT_PROFILE_LEASE_ID_COLLISION",
                "Runtime profile lease id collision",
                collision
            );
        } catch (IOException failure) {
            throw new DbtRuntimeProfileException(
                "DBT_PROFILE_LEASE_WRITE_FAILED",
                "Runtime profile lease directory could not be created",
                failure
            );
        }
        try {
            Files.writeString(
                profileFile,
                profileYaml(
                    profileKey,
                    targetName,
                    target,
                    endpoint
                ),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            );
            Files.setPosixFilePermissions(profileFile, PROFILE_PERMISSIONS);
            LeaseView view = new LeaseView(
                leaseId,
                targetName,
                expiresAt,
                required(
                    target.credentialVersionRef(),
                    "credential version"
                )
            );
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
        } catch (DbtRuntimeProfileException failure) {
            deleteLeaseDirectory(leaseDirectory, false);
            throw failure;
        } catch (IOException failure) {
            deleteLeaseDirectory(leaseDirectory, false);
            throw new DbtRuntimeProfileException(
                "DBT_PROFILE_LEASE_WRITE_FAILED",
                "Runtime profile lease could not be created",
                failure
            );
        } catch (RuntimeException failure) {
            deleteLeaseDirectory(leaseDirectory, false);
            throw new DbtRuntimeProfileException(
                "DBT_PROFILE_LEASE_METADATA_WRITE_FAILED",
                "Runtime profile lease metadata could not be persisted",
                failure
            );
        }
    }

    public LeaseView consume(UUID leaseId) {
        UUID id = requiredLeaseId(leaseId);
        LeaseRecord current = repository.find(id).orElse(null);
        if (current == null || current.status() == LeaseStatus.RELEASED) {
            throw error(
                "DBT_PROFILE_LEASE_NOT_FOUND",
                "Runtime profile lease does not exist"
            );
        }
        if (current.status() == LeaseStatus.EXPIRED) {
            throw error(
                "DBT_PROFILE_LEASE_EXPIRED",
                "Runtime profile lease has expired"
            );
        }
        Instant now = clock.instant();
        if (!now.isBefore(current.expiresAt())) {
            expireAndDelete(current, now, now);
            throw error(
                "DBT_PROFILE_LEASE_EXPIRED",
                "Runtime profile lease has expired"
            );
        }
        if (
            !Files.isRegularFile(
                leaseDirectory(id).resolve(PROFILE_FILE),
                LinkOption.NOFOLLOW_LINKS
            )
        ) {
            expireAndDelete(
                current,
                current.expiresAt(),
                now
            );
            throw error(
                "DBT_PROFILE_LEASE_FILE_MISSING",
                "Runtime profile lease file is unavailable"
            );
        }
        if (current.status() == LeaseStatus.CONSUMED) {
            return view(current);
        }
        if (!repository.consume(id, now)) {
            return consume(id);
        }
        return view(current);
    }

    /** Extends one active consumed lease so the janitor cannot remove a profile while dbt is still running. */
    public LeaseView renew(UUID leaseId) {
        UUID id = requiredLeaseId(leaseId);
        LeaseRecord current = repository.find(id).orElse(null);
        if (
            current == null ||
            current.status() == LeaseStatus.RELEASED
        ) {
            throw error(
                "DBT_PROFILE_LEASE_NOT_FOUND",
                "Runtime profile lease does not exist"
            );
        }
        Instant now = clock.instant();
        if (
            current.status() == LeaseStatus.EXPIRED ||
            !now.isBefore(current.expiresAt())
        ) {
            expireAndDelete(current, now, now);
            throw error(
                "DBT_PROFILE_LEASE_EXPIRED",
                "Runtime profile lease has expired"
            );
        }
        if (current.status() != LeaseStatus.CONSUMED) {
            throw error(
                "DBT_PROFILE_LEASE_NOT_CONSUMED",
                "Runtime profile lease has not been consumed"
            );
        }
        if (
            !Files.isRegularFile(
                leaseDirectory(id).resolve(PROFILE_FILE),
                LinkOption.NOFOLLOW_LINKS
            )
        ) {
            expireAndDelete(
                current,
                current.expiresAt(),
                now
            );
            throw error(
                "DBT_PROFILE_LEASE_FILE_MISSING",
                "Runtime profile lease file is unavailable"
            );
        }
        Instant expiresAt = now.plus(leaseTtl());
        if (!repository.renew(id, now, expiresAt)) {
            LeaseRecord raced = repository.find(id).orElse(null);
            if (
                raced != null &&
                raced.status() == LeaseStatus.CONSUMED &&
                now.isBefore(raced.expiresAt())
            ) {
                return view(raced);
            }
            throw error(
                "DBT_PROFILE_LEASE_RENEW_FAILED",
                "Runtime profile lease could not be renewed"
            );
        }
        return new LeaseView(
            current.id(),
            current.targetName(),
            expiresAt,
            current.credentialVersionRef()
        );
    }

    /** Returns the same non-sensitive lease identity for idempotent runtime-spec recovery. */
    public LeaseView viewActive(UUID leaseId) {
        UUID id = requiredLeaseId(leaseId);
        LeaseRecord current = repository.find(id).orElse(null);
        if (
            current == null ||
            current.status() == LeaseStatus.RELEASED
        ) {
            throw error(
                "DBT_PROFILE_LEASE_NOT_FOUND",
                "Runtime profile lease does not exist"
            );
        }
        Instant now = clock.instant();
        if (
            current.status() == LeaseStatus.EXPIRED ||
            !now.isBefore(current.expiresAt())
        ) {
            expireAndDelete(current, now, now);
            throw error(
                "DBT_PROFILE_LEASE_EXPIRED",
                "Runtime profile lease has expired"
            );
        }
        return view(current);
    }

    public void release(UUID leaseId) {
        UUID id = requiredLeaseId(leaseId);
        Path directory = leaseDirectory(id);
        boolean released = repository.release(id, clock.instant());
        if (!released) {
            LeaseRecord current = repository.find(id).orElse(null);
            if (
                current == null ||
                (current.status() != LeaseStatus.RELEASED &&
                    current.status() != LeaseStatus.EXPIRED)
            ) {
                if (
                    Files.notExists(
                        directory,
                        LinkOption.NOFOLLOW_LINKS
                    )
                ) {
                    return;
                }
                throw error(
                    "DBT_PROFILE_LEASE_RELEASE_FAILED",
                    "Runtime profile lease release could not be confirmed"
                );
            }
        }
        deleteLeaseDirectory(directory, true);
    }

    public Readiness readiness() {
        try {
            Path root = runtimeRoot();
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
                if (properties.isRuntimeProfileRequireTmpfs()) {
                    return Readiness.down(
                        "DBT_RUNTIME_PROFILE_ROOT_MISSING"
                    );
                }
                Files.createDirectories(
                    root,
                    PosixFilePermissions.asFileAttribute(
                        ROOT_PERMISSIONS
                    )
                );
            }
            if (containsSymbolicLink(root)) {
                return Readiness.down(
                    "DBT_RUNTIME_PROFILE_ROOT_SYMLINK"
                );
            }
            if (
                !Files.isDirectory(
                    root,
                    LinkOption.NOFOLLOW_LINKS
                ) ||
                !Files.isWritable(root)
            ) {
                return Readiness.down(
                    "DBT_RUNTIME_PROFILE_ROOT_UNAVAILABLE"
                );
            }
            Set<java.nio.file.attribute.PosixFilePermission> permissions =
                Files.getPosixFilePermissions(
                    root,
                    LinkOption.NOFOLLOW_LINKS
                );
            if (!ROOT_PERMISSIONS.equals(permissions)) {
                if (properties.isRuntimeProfileRequireTmpfs()) {
                    return Readiness.down(
                        "DBT_RUNTIME_PROFILE_ROOT_PERMISSIONS_INVALID"
                    );
                }
                Files.setPosixFilePermissions(root, ROOT_PERMISSIONS);
            }
            if (properties.isRuntimeProfileRequireTmpfs()) {
                if (properties.getRuntimeProfileExpectedUid() < 0) {
                    return Readiness.down(
                        "DBT_RUNTIME_PROFILE_EXPECTED_UID_INVALID"
                    );
                }
                Object ownerUid = Files.getAttribute(
                    root,
                    "unix:uid",
                    LinkOption.NOFOLLOW_LINKS
                );
                if (
                    !(ownerUid instanceof Number owner) ||
                    owner.longValue() !=
                    properties.getRuntimeProfileExpectedUid()
                ) {
                    return Readiness.down(
                        "DBT_RUNTIME_PROFILE_ROOT_OWNER_INVALID"
                    );
                }
                FileStore store = Files.getFileStore(root);
                String type = store
                    .type()
                    .toLowerCase(Locale.ROOT);
                if (!"tmpfs".equals(type) && !"ramfs".equals(type)) {
                    return Readiness.down(
                        "DBT_RUNTIME_PROFILE_ROOT_NOT_TMPFS"
                    );
                }
            }
            return Readiness.up();
        } catch (IOException | RuntimeException failure) {
            return Readiness.down(
                "DBT_RUNTIME_PROFILE_ROOT_UNAVAILABLE"
            );
        }
    }

    @Scheduled(
        fixedDelayString = "${dts.modeling.materialization.runtime-profile-janitor-delay-ms:60000}"
    )
    public void cleanupExpired() {
        Instant now = clock.instant();
        try {
            for (LeaseRecord lease : repository.findExpired(now, 100)) {
                expireAndDelete(lease, now, now);
            }
        } catch (RuntimeException ignored) {
            // Fail closed and retry on the next janitor pass.
        }
        cleanupOrphans(now);
    }

    private void cleanupOrphans(Instant now) {
        Path root;
        try {
            root = runtimeRoot();
            if (
                !Files.isDirectory(
                    root,
                    LinkOption.NOFOLLOW_LINKS
                )
            ) {
                return;
            }
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
                for (Path entry : entries) {
                    if (
                        !Files.isDirectory(
                            entry,
                            LinkOption.NOFOLLOW_LINKS
                        )
                    ) {
                        continue;
                    }
                    UUID id = parseLeaseId(entry.getFileName().toString());
                    LeaseRecord tracked = id == null
                        ? null
                        : repository.find(id).orElse(null);
                    if (tracked != null) {
                        if (
                            tracked.status() == LeaseStatus.RELEASED ||
                            tracked.status() == LeaseStatus.EXPIRED
                        ) {
                            deleteLeaseDirectory(entry, false);
                        }
                        continue;
                    }
                    Instant modified = Files.getLastModifiedTime(
                        entry,
                        LinkOption.NOFOLLOW_LINKS
                    )
                        .toInstant();
                    if (!now.isBefore(modified.plus(leaseTtl()))) {
                        deleteLeaseDirectory(entry, false);
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Readiness remains fail-closed; the next janitor pass retries cleanup.
        }
    }

    private boolean expireAndDelete(
        LeaseRecord lease,
        Instant cutoff,
        Instant expiredAt
    ) {
        if (
            !repository.expire(
                lease.id(),
                lease.expiresAt(),
                cutoff,
                expiredAt
            )
        ) {
            return false;
        }
        return deleteLeaseDirectory(
            leaseDirectory(lease.id()),
            false
        );
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

    private Path runtimeRoot() {
        String configured = required(
            properties.getRuntimeProfileRoot(),
            "runtime profile root"
        );
        Path root = Path.of(configured).toAbsolutePath().normalize();
        if (root.getParent() == null) {
            throw error(
                "DBT_RUNTIME_PROFILE_ROOT_UNAVAILABLE",
                "Runtime profile root is invalid"
            );
        }
        return root;
    }

    private Path leaseDirectory(UUID leaseId) {
        Path root = runtimeRoot();
        Path lease = root.resolve(leaseId.toString()).normalize();
        if (!root.equals(lease.getParent())) {
            throw error(
                "DBT_PROFILE_LEASE_PATH_INVALID",
                "Runtime profile lease path is invalid"
            );
        }
        return lease;
    }

    private boolean containsSymbolicLink(Path path) {
        Path current = path.getRoot();
        for (Path segment : path) {
            current = current == null
                ? segment
                : current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                return true;
            }
        }
        return false;
    }

    private boolean deleteLeaseDirectory(
        Path leaseDirectory,
        boolean strict
    ) {
        try {
            Path root = runtimeRoot();
            Path normalized = leaseDirectory
                .toAbsolutePath()
                .normalize();
            if (!root.equals(normalized.getParent())) {
                throw error(
                    "DBT_PROFILE_LEASE_PATH_INVALID",
                    "Runtime profile lease path is invalid"
                );
            }
            Files.deleteIfExists(normalized.resolve(PROFILE_FILE));
            Files.deleteIfExists(normalized);
            return true;
        } catch (IOException | RuntimeException failure) {
            if (strict) {
                throw new DbtRuntimeProfileException(
                    "DBT_PROFILE_LEASE_RELEASE_FAILED",
                    "Runtime profile lease could not be released",
                    failure
                );
            }
            return false;
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

    private static UUID parseLeaseId(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return null;
        }
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
