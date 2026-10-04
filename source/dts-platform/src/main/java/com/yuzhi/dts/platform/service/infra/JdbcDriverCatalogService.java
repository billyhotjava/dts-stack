package com.yuzhi.dts.platform.service.infra;

import com.yuzhi.dts.platform.service.infra.dto.JdbcDriverInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JdbcDriverCatalogService {

    private static final Logger log = LoggerFactory.getLogger(JdbcDriverCatalogService.class);
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)+)");
    private static final Pattern TAIL_VERSION_PATTERN = Pattern.compile("(\\d{1,4})$");

    @Value("${dts.jdbc.drivers-dir:/opt/dts/drivers}")
    private String driversDir;

    public List<JdbcDriverInfo> listDrivers() {
        Path dir = resolveDriversDir();
        if (dir == null || !Files.isDirectory(dir)) {
            return List.of();
        }
        List<JdbcDriverInfo> output = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream
                .filter(path -> Files.isRegularFile(path))
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                .forEach(path -> {
                    String fileName = path.getFileName().toString();
                    String version = extractVersion(fileName);
                    String label = buildLabel(fileName, version);
                    output.add(new JdbcDriverInfo(fileName, version, label));
                });
        } catch (Exception ex) {
            log.warn("Failed to list JDBC drivers under {}: {}", dir, ex.getMessage());
            log.debug("Driver catalog listing failure stack", ex);
        }
        output.sort(Comparator.comparing(JdbcDriverInfo::fileName));
        return output;
    }

    private Path resolveDriversDir() {
        if (driversDir != null && !driversDir.isBlank()) {
            Path configured = Path.of(driversDir);
            if (Files.isDirectory(configured)) {
                return configured;
            }
        }
        Path repoFallback = resolveRepoDriversDir();
        if (repoFallback != null) {
            return repoFallback;
        }
        return null;
    }

    private Path resolveRepoDriversDir() {
        Path current = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && current != null; i++) {
            Path candidate = current.resolve("services").resolve("dts-platform").resolve("drivers");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }

    private String extractVersion(String fileName) {
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
        return "";
    }

    private String buildLabel(String fileName, String version) {
        if (version == null || version.isBlank()) {
            return fileName;
        }
        return fileName + " (" + version + ")";
    }
}
