package com.yuzhi.dts.platform.service.etl;

import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Owns all filesystem operations for short-lived dbt runtime profiles. */
@Component
public class DbtRuntimeProfileLeaseFileStore {

    private static final Set<java.nio.file.attribute.PosixFilePermission>
        ROOT_PERMISSIONS = PosixFilePermissions.fromString("rwx------");
    private static final Set<java.nio.file.attribute.PosixFilePermission>
        PROFILE_PERMISSIONS = PosixFilePermissions.fromString("rw-------");
    private static final String PROFILE_FILE = "profiles.yml";

    private final ModelMaterializationProperties properties;

    public DbtRuntimeProfileLeaseFileStore(
        ModelMaterializationProperties properties
    ) {
        this.properties = Objects.requireNonNull(
            properties,
            "properties is required"
        );
    }

    public void createProfile(UUID leaseId, String profileYaml) {
        Path directory = leaseDirectory(leaseId);
        try {
            Files.createDirectory(
                directory,
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
            Path profile = directory.resolve(PROFILE_FILE);
            Files.writeString(
                profile,
                profileYaml,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            );
            Files.setPosixFilePermissions(profile, PROFILE_PERMISSIONS);
        } catch (IOException | RuntimeException failure) {
            deleteLeaseDirectory(directory, false);
            throw new DbtRuntimeProfileException(
                "DBT_PROFILE_LEASE_WRITE_FAILED",
                "Runtime profile lease could not be created",
                failure
            );
        }
    }

    public boolean profileExists(UUID leaseId) {
        return Files.isRegularFile(
            leaseDirectory(leaseId).resolve(PROFILE_FILE),
            LinkOption.NOFOLLOW_LINKS
        );
    }

    public boolean leaseDirectoryExists(UUID leaseId) {
        return Files.isDirectory(
            leaseDirectory(leaseId),
            LinkOption.NOFOLLOW_LINKS
        );
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
                Readiness production = productionReadiness(root);
                if (!production.ready()) {
                    return production;
                }
            }
            return Readiness.up();
        } catch (IOException | RuntimeException failure) {
            return Readiness.down(
                "DBT_RUNTIME_PROFILE_ROOT_UNAVAILABLE"
            );
        }
    }

    Path runtimeRoot() {
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

    Path leaseDirectory(UUID leaseId) {
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

    boolean deleteLeaseDirectory(UUID leaseId, boolean strict) {
        return deleteLeaseDirectory(leaseDirectory(leaseId), strict);
    }

    boolean deleteLeaseDirectory(
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

    private Readiness productionReadiness(Path root) throws IOException {
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
        String type = store.type().toLowerCase(Locale.ROOT);
        if (!"tmpfs".equals(type) && !"ramfs".equals(type)) {
            return Readiness.down(
                "DBT_RUNTIME_PROFILE_ROOT_NOT_TMPFS"
            );
        }
        return Readiness.up();
    }

    private static boolean containsSymbolicLink(Path path) {
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

    private static String required(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw error(
                "DBT_RUNTIME_PROFILE_CONFIGURATION_INVALID",
                name + " is required"
            );
        }
        return value.trim();
    }

    private static DbtRuntimeProfileException error(
        String code,
        String message
    ) {
        return new DbtRuntimeProfileException(code, message);
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
