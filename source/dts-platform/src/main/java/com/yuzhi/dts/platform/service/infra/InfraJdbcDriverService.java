package com.yuzhi.dts.platform.service.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.infra.InfraJdbcDriver;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.infra.InfraJdbcDriverRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.infra.dto.InfraJdbcDriverDto;
import com.yuzhi.dts.platform.service.infra.dto.InfraJdbcDriverUpdateRequest;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InfraJdbcDriverService {

    private static final Logger LOG = LoggerFactory.getLogger(InfraJdbcDriverService.class);
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)+)");
    private static final Pattern TAIL_VERSION_PATTERN = Pattern.compile("(\\d{1,4})$");
    private static final Pattern JDK_PATTERN = Pattern.compile("(\\d+)(?:\\.(\\d+))?");
    private static final String DRIVER_SERVICE_ENTRY = "META-INF/services/java.sql.Driver";
    private static final List<String> JDK_ATTR_KEYS = List.of(
        "Build-Jdk-Spec",
        "Build-Jdk",
        "Build-Jdk-Specification",
        "Build-Jdk-Release",
        "X-Compile-Jdk",
        "Target-Jdk",
        "Created-By"
    );

    @Value("${dts.jdbc.drivers-dir:/opt/dts/drivers}")
    private String driversDir;

    private final InfraJdbcDriverRepository driverRepository;
    private final InfraDataSourceRepository dataSourceRepository;
    private final ObjectMapper objectMapper;

    public InfraJdbcDriverService(
        InfraJdbcDriverRepository driverRepository,
        InfraDataSourceRepository dataSourceRepository,
        ObjectMapper objectMapper
    ) {
        this.driverRepository = driverRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.objectMapper = objectMapper;
    }

    public List<InfraJdbcDriverDto> listDrivers() {
        Path dir = resolveDriversDir(false);
        if (dir != null) {
            syncDriversDir(dir);
        }
        List<InfraJdbcDriver> drivers = driverRepository.findAll();
        drivers.sort(Comparator.comparing(d -> safe(d.getFileName())));
        return drivers.stream().map(this::toDto).collect(Collectors.toList());
    }

    public InfraJdbcDriverDto upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件不能为空");
        }
        String fileName = sanitizeFileName(file.getOriginalFilename());
        if (!StringUtils.hasText(fileName) || !fileName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持上传 .jar 驱动文件");
        }
        Path dir = resolveDriversDir(true);
        Path target = dir.resolve(fileName);
        if (Files.exists(target)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "驱动文件已存在：" + fileName);
        }
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "保存驱动文件失败: " + ex.getMessage());
        }
        JarMetadata metadata = parseJarMetadata(target);
        InfraJdbcDriver entity = driverRepository.findByFileNameIgnoreCase(fileName).orElseGet(InfraJdbcDriver::new);
        entity.setFileName(fileName);
        entity.setFilePath(target.toString());
        entity.setDriverClass(metadata.driverClass());
        entity.setVersion(metadata.version());
        entity.setJdkSpec(metadata.jdkSpec());
        InfraJdbcDriver saved = driverRepository.save(entity);
        return toDto(saved);
    }

    public InfraJdbcDriverDto update(UUID id, InfraJdbcDriverUpdateRequest request) {
        InfraJdbcDriver entity = driverRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "驱动不存在"));
        if (request != null) {
            entity.setDriverClass(normalize(request.driverClass()));
            entity.setVersion(normalize(request.version()));
            entity.setJdkSpec(normalize(request.jdkSpec()));
        }
        InfraJdbcDriver saved = driverRepository.save(entity);
        return toDto(saved);
    }

    public void delete(UUID id) {
        InfraJdbcDriver entity = driverRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "驱动不存在"));
        ensureNotInUse(entity);
        deleteFileIfExists(entity.getFilePath());
        driverRepository.delete(entity);
    }

    private void syncDriversDir(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        List<InfraJdbcDriver> existing = driverRepository.findAll();
        Map<String, InfraJdbcDriver> byName = existing.stream()
            .filter(e -> StringUtils.hasText(e.getFileName()))
            .collect(Collectors.toMap(e -> e.getFileName().toLowerCase(Locale.ROOT), e -> e, (a, b) -> a, LinkedHashMap::new));
        try (var stream = Files.list(dir)) {
            stream
                .filter(path -> Files.isRegularFile(path))
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                .forEach(path -> {
                    String fileName = path.getFileName().toString();
                    String key = fileName.toLowerCase(Locale.ROOT);
                    InfraJdbcDriver entity = byName.get(key);
                    if (entity == null) {
                        JarMetadata metadata = parseJarMetadata(path);
                        InfraJdbcDriver created = new InfraJdbcDriver();
                        created.setFileName(fileName);
                        created.setFilePath(path.toString());
                        created.setDriverClass(metadata.driverClass());
                        created.setVersion(metadata.version());
                        created.setJdkSpec(metadata.jdkSpec());
                        driverRepository.save(created);
                        return;
                    }
                    boolean changed = false;
                    if (!Objects.equals(entity.getFilePath(), path.toString())) {
                        entity.setFilePath(path.toString());
                        changed = true;
                    }
                    if (!StringUtils.hasText(entity.getDriverClass()) || !StringUtils.hasText(entity.getVersion()) || !StringUtils.hasText(entity.getJdkSpec())) {
                        JarMetadata metadata = parseJarMetadata(path);
                        if (!StringUtils.hasText(entity.getDriverClass()) && StringUtils.hasText(metadata.driverClass())) {
                            entity.setDriverClass(metadata.driverClass());
                            changed = true;
                        }
                        if (!StringUtils.hasText(entity.getVersion()) && StringUtils.hasText(metadata.version())) {
                            entity.setVersion(metadata.version());
                            changed = true;
                        }
                        if (!StringUtils.hasText(entity.getJdkSpec()) && StringUtils.hasText(metadata.jdkSpec())) {
                            entity.setJdkSpec(metadata.jdkSpec());
                            changed = true;
                        }
                    }
                    if (changed) {
                        driverRepository.save(entity);
                    }
                });
        } catch (Exception ex) {
            LOG.warn("Failed to sync JDBC drivers from {}: {}", dir, ex.getMessage());
            LOG.debug("Driver sync failure stack", ex);
        }
    }

    private void ensureNotInUse(InfraJdbcDriver driver) {
        if (driver == null) return;
        String driverClass = normalize(driver.getDriverClass());
        String fileName = normalize(driver.getFileName());
        String version = normalize(driver.getVersion());
        List<InfraDataSource> sources = dataSourceRepository.findAll();
        List<String> matched = new ArrayList<>();
        for (InfraDataSource source : sources) {
            Map<String, Object> props = readProps(source.getProps());
            String propsDriverClass = normalize(props.get("driverClass"));
            String propsDriverVersion = normalize(props.get("driverVersion"));
            boolean match =
                (StringUtils.hasText(driverClass) && driverClass.equalsIgnoreCase(propsDriverClass)) ||
                (StringUtils.hasText(fileName) && fileName.equalsIgnoreCase(propsDriverVersion)) ||
                (StringUtils.hasText(version) && version.equalsIgnoreCase(propsDriverVersion));
            if (match) {
                matched.add(source.getName());
            }
        }
        if (!matched.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "驱动已被数据源使用，无法删除: " + String.join(", ", matched)
            );
        }
    }

    private void deleteFileIfExists(String filePath) {
        if (!StringUtils.hasText(filePath)) return;
        try {
            Files.deleteIfExists(Path.of(filePath));
        } catch (Exception ex) {
            LOG.warn("Failed to delete driver file {}: {}", filePath, ex.getMessage());
        }
    }

    private InfraJdbcDriverDto toDto(InfraJdbcDriver entity) {
        boolean missing = false;
        if (entity != null && StringUtils.hasText(entity.getFilePath())) {
            try {
                missing = !Files.exists(Path.of(entity.getFilePath()));
            } catch (Exception ignored) {
                missing = true;
            }
        }
        return new InfraJdbcDriverDto(
            entity.getId(),
            entity.getFileName(),
            entity.getFilePath(),
            entity.getDriverClass(),
            entity.getVersion(),
            entity.getJdkSpec(),
            entity.getCreatedDate(),
            entity.getLastModifiedDate(),
            missing
        );
    }

    private Path resolveDriversDir(boolean ensure) {
        if (!StringUtils.hasText(driversDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "驱动目录未配置");
        }
        Path dir = Path.of(driversDir);
        if (!Files.isDirectory(dir)) {
            Path fallback = resolveRepoDriversDir();
            if (fallback != null) {
                dir = fallback;
            }
        }
        if (ensure) {
            try {
                Files.createDirectories(dir);
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "创建驱动目录失败: " + ex.getMessage());
            }
        }
        return dir;
    }

    private Path resolveRepoDriversDir() {
        try {
            Path current = Path.of("").toAbsolutePath();
            for (int i = 0; i < 6 && current != null; i++) {
                Path candidate = current.resolve("services").resolve("dts-platform").resolve("drivers");
                if (Files.isDirectory(candidate)) {
                    return candidate;
                }
                current = current.getParent();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private JarMetadata parseJarMetadata(Path jarPath) {
        if (jarPath == null) {
            return new JarMetadata(null, null, null);
        }
        String version = null;
        String driverClass = null;
        String jdkSpec = null;
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            Manifest manifest = jarFile.getManifest();
            if (manifest != null) {
                Attributes attrs = manifest.getMainAttributes();
                version = firstAttr(attrs, "Implementation-Version", "Bundle-Version", "Specification-Version", "ImplementationVersion");
                String rawJdk = firstAttr(attrs, JDK_ATTR_KEYS.toArray(new String[0]));
                jdkSpec = normalizeJdkSpec(rawJdk);
                if (!StringUtils.hasText(driverClass)) {
                    driverClass = firstAttr(attrs, "Driver-Class", "JDBC-Driver-Class");
                }
            }
            if (!StringUtils.hasText(driverClass)) {
                driverClass = readDriverClassFromService(jarFile);
            }
            if (!StringUtils.hasText(jdkSpec)) {
                jdkSpec = normalizeJdkSpec(inferJdkSpecFromClass(jarFile));
            }
        } catch (Exception ex) {
            LOG.warn("Failed to parse driver jar metadata for {}: {}", jarPath.getFileName(), ex.getMessage());
            LOG.debug("Driver metadata parse failure stack", ex);
        }
        if (!StringUtils.hasText(version)) {
            version = extractVersion(jarPath.getFileName().toString());
        }
        return new JarMetadata(normalize(driverClass), normalize(version), normalize(jdkSpec));
    }

    private String readDriverClassFromService(JarFile jarFile) {
        try {
            JarEntry entry = jarFile.getJarEntry(DRIVER_SERVICE_ENTRY);
            if (entry == null) {
                return null;
            }
            try (InputStream in = jarFile.getInputStream(entry);
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    return trimmed;
                }
            }
        } catch (Exception ex) {
            LOG.debug("Failed to read driver class from service file: {}", ex.getMessage());
        }
        return null;
    }

    private String inferJdkSpecFromClass(JarFile jarFile) {
        try {
            var entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream in = jarFile.getInputStream(entry)) {
                    byte[] header = in.readNBytes(8);
                    if (header.length < 8) {
                        continue;
                    }
                    int major = ((header[6] & 0xFF) << 8) | (header[7] & 0xFF);
                    return mapMajorToJdk(major);
                }
            }
        } catch (Exception ex) {
            LOG.debug("Failed to infer JDK spec from class files: {}", ex.getMessage());
        }
        return null;
    }

    private String mapMajorToJdk(int major) {
        return switch (major) {
            case 45 -> "1.1";
            case 46 -> "1.2";
            case 47 -> "1.3";
            case 48 -> "1.4";
            case 49 -> "5";
            case 50 -> "6";
            case 51 -> "7";
            case 52 -> "8";
            case 53 -> "9";
            case 54 -> "10";
            case 55 -> "11";
            case 56 -> "12";
            case 57 -> "13";
            case 58 -> "14";
            case 59 -> "15";
            case 60 -> "16";
            case 61 -> "17";
            case 62 -> "18";
            case 63 -> "19";
            case 64 -> "20";
            case 65 -> "21";
            default -> String.valueOf(major);
        };
    }

    private String normalizeJdkSpec(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        Matcher matcher = JDK_PATTERN.matcher(trimmed);
        if (!matcher.find()) {
            return trimmed;
        }
        String major = matcher.group(1);
        String minor = matcher.group(2);
        if ("1".equals(major) && StringUtils.hasText(minor)) {
            return minor;
        }
        return major;
    }

    private String firstAttr(Attributes attrs, String... keys) {
        if (attrs == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (!StringUtils.hasText(key)) {
                continue;
            }
            String value = attrs.getValue(key);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String extractVersion(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return null;
        }
        String base = fileName;
        if (base.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            base = base.substring(0, base.length() - 4);
        }
        Matcher matcher = VERSION_PATTERN.matcher(base);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = TAIL_VERSION_PATTERN.matcher(base);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String sanitizeFileName(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String name = raw.trim();
        int idx = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (idx >= 0 && idx + 1 < name.length()) {
            name = name.substring(idx + 1);
        }
        return name.trim();
    }

    private String normalize(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private Map<String, Object> readProps(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private record JarMetadata(String driverClass, String version, String jdkSpec) {}
}
