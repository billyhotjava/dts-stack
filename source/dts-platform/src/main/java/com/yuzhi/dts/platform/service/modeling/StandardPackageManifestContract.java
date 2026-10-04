package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class StandardPackageManifestContract {

    public static final String SCHEMA_V2 = "2.0";

    private static final Pattern PACKAGE_CODE = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern SEMANTIC_VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> LICENSE_CONCLUSIONS = Set.of("APPROVED", "REVIEW_REQUIRED", "REJECTED");

    private final ObjectMapper objectMapper;

    public StandardPackageManifestContract(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Manifest parsePackage(byte[] json) {
        if (json == null || json.length == 0) {
            throw error("MANIFEST_INVALID_JSON", "manifest.json 为空");
        }
        try {
            return validateAndNormalize(objectMapper.readValue(json, Manifest.class));
        } catch (StandardPackageContractException ex) {
            throw ex;
        } catch (Exception ex) {
            throw error("MANIFEST_INVALID_JSON", "manifest.json 不是有效 JSON");
        }
    }

    public Catalog parseCatalog(byte[] json) {
        if (json == null || json.length == 0) {
            throw error("MANIFEST_INVALID_JSON", "标准包目录为空");
        }
        try {
            Catalog raw = objectMapper.readValue(json, Catalog.class);
            if (raw == null || !SCHEMA_V2.equals(raw.schemaVersion())) {
                throw error("MANIFEST_SCHEMA_UNSUPPORTED", "标准包目录仅支持 schemaVersion=2.0");
            }
            if (raw.packages() == null || raw.packages().isEmpty()) {
                throw error("MANIFEST_REQUIRED_FIELD", "packages 不能为空");
            }
            List<Manifest> packages = raw.packages().stream().map(this::validateAndNormalize).toList();
            Catalog catalog = new Catalog(SCHEMA_V2, packages);
            installationOrder(catalog);
            return catalog;
        } catch (StandardPackageContractException ex) {
            throw ex;
        } catch (Exception ex) {
            throw error("MANIFEST_INVALID_JSON", "标准包目录不是有效 JSON");
        }
    }

    public Catalog parseCatalogCompatible(byte[] json, Map<String, Map<String, byte[]>> packageEntries) {
        if (json == null || json.length == 0) {
            throw error("MANIFEST_INVALID_JSON", "标准包目录为空");
        }
        try {
            Map<String, Object> root = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            if (SCHEMA_V2.equals(root.get("schemaVersion"))) {
                return parseCatalog(json);
            }
            if (root.containsKey("schemaVersion")) {
                throw error("MANIFEST_SCHEMA_UNSUPPORTED", "标准包目录仅支持 schemaVersion=2.0 或无版本的 v1 清单");
            }
            Object rawPackages = root.get("packages");
            if (!(rawPackages instanceof List<?> packages) || packages.isEmpty()) {
                throw error("MANIFEST_REQUIRED_FIELD", "packages 不能为空");
            }

            Map<String, Map<String, byte[]>> entries = packageEntries == null ? Map.of() : packageEntries;
            Set<String> codes = new HashSet<>();
            List<Manifest> adapted = new ArrayList<>();
            for (Object rawPackage : packages) {
                if (!(rawPackage instanceof Map<?, ?> pkg)) {
                    throw error("MANIFEST_LEGACY_PACKAGE_INVALID", "v1 packages 只能包含对象");
                }
                String code = legacyText(pkg, "code");
                if (!StringUtils.hasText(code)) {
                    throw error("MANIFEST_REQUIRED_FIELD", "v1 package.code 不能为空");
                }
                String normalizedCode = normalizeLegacyCode(code);
                if (!codes.add(normalizedCode)) {
                    throw error("MANIFEST_DUPLICATE_PACKAGE", "packageCode 重复：" + normalizedCode);
                }
                String name = legacyText(pkg, "name");
                String category = legacyText(pkg, "category");
                adapted.add(
                    adaptLegacyPackage(
                        code,
                        StringUtils.hasText(name) ? name : code,
                        StringUtils.hasText(category) ? category : "LEGACY",
                        entries.getOrDefault(normalizedCode, Map.of())
                    )
                );
            }
            return new Catalog("1.0", List.copyOf(adapted));
        } catch (StandardPackageContractException ex) {
            throw ex;
        } catch (Exception ex) {
            throw error("MANIFEST_INVALID_JSON", "标准包目录不是有效 JSON");
        }
    }

    public byte[] writePackage(Manifest manifest) {
        try {
            return objectMapper.writeValueAsBytes(validateAndNormalize(manifest));
        } catch (StandardPackageContractException ex) {
            throw ex;
        } catch (Exception ex) {
            throw error("MANIFEST_SERIALIZATION_FAILED", "manifest.json 序列化失败");
        }
    }

    public Manifest adaptLegacyPackage(
        String packageCode,
        String packageName,
        String category,
        Map<String, byte[]> entries
    ) {
        String normalizedCode = normalizeLegacyCode(packageCode);
        Map<String, String> files = new TreeMap<>();
        if (entries != null) {
            entries.forEach((fileName, content) -> {
                if (!"manifest.json".equals(fileName) && content != null) {
                    files.put(fileName, sha256(content));
                }
            });
        }
        return new Manifest(
            "1.0",
            normalizedCode,
            StringUtils.hasText(packageName) ? packageName : normalizedCode,
            "1.0.0",
            StringUtils.hasText(category) ? category : "LEGACY",
            "LEGACY",
            null,
            null,
            List.of(),
            List.of(),
            false,
            "LEGACY_V1",
            "REVIEW_REQUIRED",
            Map.copyOf(files),
            contentChecksum(files)
        );
    }

    public Map<String, Object> summary(Manifest manifest) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("schemaVersion", manifest.schemaVersion());
        summary.put("packageCode", manifest.packageCode());
        summary.put("packageName", manifest.packageName());
        summary.put("packageVersion", manifest.packageVersion());
        summary.put("category", manifest.category());
        summary.put("industry", manifest.industry());
        summary.put("releasedAt", manifest.releasedAt());
        summary.put("effectiveFrom", manifest.effectiveFrom());
        summary.put("dependencies", manifest.dependencies());
        summary.put("replaces", manifest.replaces());
        summary.put("deprecated", manifest.deprecated());
        summary.put("sourceRegisterRef", manifest.sourceRegisterRef());
        summary.put("licenseConclusion", manifest.licenseConclusion());
        summary.put("files", manifest.files());
        summary.put("contentChecksum", manifest.contentChecksum());
        summary.put("compatibilityMode", SCHEMA_V2.equals(manifest.schemaVersion()) ? "V2" : "LEGACY_V1");
        return summary;
    }

    public void verifyFiles(Manifest manifest, Map<String, byte[]> entries) {
        if (entries == null) {
            throw error("MANIFEST_FILE_MISSING", "标准包内容为空");
        }
        for (Map.Entry<String, String> declaration : manifest.files().entrySet()) {
            byte[] content = entries.get(declaration.getKey());
            if (content == null) {
                throw error("MANIFEST_FILE_MISSING", "声明文件不存在：" + declaration.getKey());
            }
            if (!declaration.getValue().equals(sha256(content))) {
                throw error("MANIFEST_CHECKSUM_MISMATCH", "文件 SHA-256 不匹配：" + declaration.getKey());
            }
        }
        for (String fileName : entries.keySet()) {
            if (!"manifest.json".equals(fileName) && !manifest.files().containsKey(fileName)) {
                throw error("MANIFEST_FILE_UNDECLARED", "文件未在 manifest 中声明：" + fileName);
            }
        }
    }

    public List<Manifest> installationOrder(Catalog catalog) {
        if (catalog == null || !SCHEMA_V2.equals(catalog.schemaVersion())) {
            throw error("MANIFEST_SCHEMA_UNSUPPORTED", "标准包目录仅支持 schemaVersion=2.0");
        }
        List<Manifest> packages = catalog.packages() == null ? List.of() : catalog.packages();
        Map<String, Manifest> byCode = new LinkedHashMap<>();
        for (Manifest manifest : packages) {
            if (manifest == null) {
                throw error("MANIFEST_REQUIRED_FIELD", "packages 不能包含空项");
            }
            if (byCode.putIfAbsent(manifest.packageCode(), manifest) != null) {
                throw error("MANIFEST_DUPLICATE_PACKAGE", "packageCode 重复：" + manifest.packageCode());
            }
        }

        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, Set<String>> consumers = new LinkedHashMap<>();
        for (Manifest manifest : packages) {
            indegree.put(manifest.packageCode(), 0);
            consumers.put(manifest.packageCode(), new LinkedHashSet<>());
        }
        for (Manifest manifest : packages) {
            for (Dependency dependency : manifest.dependencies()) {
                Manifest provider = byCode.get(dependency.packageCode());
                if (provider == null) {
                    throw error(
                        "MANIFEST_DEPENDENCY_MISSING",
                        manifest.packageCode() + " 缺少依赖：" + dependency.packageCode()
                    );
                }
                if (compareVersions(provider.packageVersion(), dependency.minimumVersion()) < 0) {
                    throw error(
                        "MANIFEST_DEPENDENCY_VERSION",
                        dependency.packageCode() + " 版本低于 " + dependency.minimumVersion()
                    );
                }
                indegree.merge(manifest.packageCode(), 1, Integer::sum);
                consumers.get(dependency.packageCode()).add(manifest.packageCode());
            }
        }

        PriorityQueue<String> ready = new PriorityQueue<>();
        indegree.forEach((code, degree) -> {
            if (degree == 0) ready.add(code);
        });
        List<Manifest> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            String code = ready.remove();
            ordered.add(byCode.get(code));
            for (String consumer : consumers.get(code)) {
                int remaining = indegree.merge(consumer, -1, Integer::sum);
                if (remaining == 0) {
                    ready.add(consumer);
                }
            }
        }
        if (ordered.size() != packages.size()) {
            throw error("MANIFEST_DEPENDENCY_CYCLE", "标准包依赖存在循环");
        }
        return List.copyOf(ordered);
    }

    public void requireInstallable(Manifest candidate, Map<String, InstalledPackage> installedPackages) {
        Map<String, InstalledPackage> installed = installedPackages == null ? Map.of() : installedPackages;
        for (Dependency dependency : candidate.dependencies()) {
            InstalledPackage actual = installed.get(dependency.packageCode());
            if (actual == null) {
                throw error("MANIFEST_DEPENDENCY_MISSING", candidate.packageCode() + " 缺少已安装依赖：" + dependency.packageCode());
            }
            if (compareVersions(actual.packageVersion(), dependency.minimumVersion()) < 0) {
                throw error(
                    "MANIFEST_DEPENDENCY_VERSION",
                    dependency.packageCode() + " 已安装版本低于 " + dependency.minimumVersion()
                );
            }
        }

        InstalledPackage current = installed.get(candidate.packageCode());
        if (current == null) {
            return;
        }
        int versionComparison = compareVersions(candidate.packageVersion(), current.packageVersion());
        if (versionComparison < 0) {
            throw error(
                "MANIFEST_VERSION_ROLLBACK",
                candidate.packageVersion() + " 低于已安装版本 " + current.packageVersion()
            );
        }
        if (
            versionComparison == 0 &&
            StringUtils.hasText(current.contentChecksum()) &&
            !candidate.contentChecksum().equals(current.contentChecksum())
        ) {
            throw error("MANIFEST_VERSION_CONTENT_MISMATCH", "同一 packageVersion 的内容摘要不一致");
        }
    }

    public int compareVersions(String left, String right) {
        requireSemanticVersion("version", left);
        requireSemanticVersion("version", right);
        int[] leftParts = versionParts(left);
        int[] rightParts = versionParts(right);
        for (int i = 0; i < leftParts.length; i++) {
            int compared = Integer.compare(leftParts[i], rightParts[i]);
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    private Manifest validateAndNormalize(Manifest raw) {
        if (raw == null) {
            throw error("MANIFEST_INVALID_JSON", "manifest.json 不能为空");
        }
        if (!SCHEMA_V2.equals(raw.schemaVersion())) {
            throw error("MANIFEST_SCHEMA_UNSUPPORTED", "仅支持 schemaVersion=2.0");
        }
        requireText("packageCode", raw.packageCode());
        requireText("packageName", raw.packageName());
        requireText("packageVersion", raw.packageVersion());
        requireText("category", raw.category());
        requireText("industry", raw.industry());
        requireText("releasedAt", raw.releasedAt());
        requireText("effectiveFrom", raw.effectiveFrom());
        requireText("sourceRegisterRef", raw.sourceRegisterRef());
        requireText("licenseConclusion", raw.licenseConclusion());
        if (raw.files() == null || raw.files().isEmpty()) {
            throw error("MANIFEST_REQUIRED_FIELD", "files 不能为空");
        }

        if (!PACKAGE_CODE.matcher(raw.packageCode()).matches()) {
            throw error("MANIFEST_PACKAGE_CODE_INVALID", "packageCode 仅允许小写字母、数字和单连字符分段");
        }
        requireSemanticVersion("packageVersion", raw.packageVersion());
        requireInstant(raw.releasedAt());
        requireDate(raw.effectiveFrom());
        if (!LICENSE_CONCLUSIONS.contains(raw.licenseConclusion())) {
            throw error("MANIFEST_LICENSE_CONCLUSION_INVALID", "licenseConclusion 仅允许 APPROVED/REVIEW_REQUIRED/REJECTED");
        }

        List<Dependency> dependencies = raw.dependencies() == null ? List.of() : new ArrayList<>(raw.dependencies());
        List<String> replaces = raw.replaces() == null ? List.of() : new ArrayList<>(raw.replaces());
        Map<String, String> files = new LinkedHashMap<>(raw.files());

        Set<String> dependencyCodes = new HashSet<>();
        for (Dependency dependency : dependencies) {
            if (dependency == null || !StringUtils.hasText(dependency.packageCode())) {
                throw error("MANIFEST_DEPENDENCY_INVALID", "dependency.packageCode 不能为空");
            }
            if (!PACKAGE_CODE.matcher(dependency.packageCode()).matches()) {
                throw error("MANIFEST_DEPENDENCY_INVALID", "dependency.packageCode 格式非法：" + dependency.packageCode());
            }
            requireSemanticVersion("dependency.minimumVersion", dependency.minimumVersion());
            if (raw.packageCode().equals(dependency.packageCode())) {
                throw error("MANIFEST_DEPENDENCY_SELF", "包不能依赖自身：" + raw.packageCode());
            }
            if (!dependencyCodes.add(dependency.packageCode())) {
                throw error("MANIFEST_DEPENDENCY_DUPLICATE", "依赖重复：" + dependency.packageCode());
            }
        }

        Set<String> replacedCodes = new HashSet<>();
        for (String replaced : replaces) {
            if (!StringUtils.hasText(replaced) || !PACKAGE_CODE.matcher(replaced).matches()) {
                throw error("MANIFEST_REPLACES_INVALID", "replaces 包编码格式非法");
            }
            if (raw.packageCode().equals(replaced)) {
                throw error("MANIFEST_REPLACES_SELF", "包不能替代自身：" + raw.packageCode());
            }
            if (!replacedCodes.add(replaced)) {
                throw error("MANIFEST_REPLACES_DUPLICATE", "replaces 重复：" + replaced);
            }
        }

        for (Map.Entry<String, String> file : files.entrySet()) {
            if (!StringUtils.hasText(file.getKey()) || file.getKey().contains("/") || file.getKey().contains("\\") || file.getKey().contains("..")) {
                throw error("MANIFEST_FILE_NAME_INVALID", "files 只能声明包根目录文件名");
            }
            if (!StringUtils.hasText(file.getValue()) || !SHA_256.matcher(file.getValue()).matches()) {
                throw error("MANIFEST_CHECKSUM_INVALID", "文件 SHA-256 格式非法：" + file.getKey());
            }
        }

        String computedChecksum = contentChecksum(files);
        if (StringUtils.hasText(raw.contentChecksum()) && !computedChecksum.equals(raw.contentChecksum())) {
            throw error("MANIFEST_CONTENT_CHECKSUM_MISMATCH", "contentChecksum 与 files 清单不一致");
        }

        return new Manifest(
            raw.schemaVersion(),
            raw.packageCode(),
            raw.packageName(),
            raw.packageVersion(),
            raw.category(),
            raw.industry(),
            raw.releasedAt(),
            raw.effectiveFrom(),
            List.copyOf(dependencies),
            List.copyOf(replaces),
            raw.deprecated(),
            raw.sourceRegisterRef(),
            raw.licenseConclusion(),
            Map.copyOf(files),
            computedChecksum
        );
    }

    private void requireText(String field, String value) {
        if (!StringUtils.hasText(value)) {
            throw error("MANIFEST_REQUIRED_FIELD", field + " 不能为空");
        }
    }

    private void requireSemanticVersion(String field, String value) {
        if (!StringUtils.hasText(value) || !SEMANTIC_VERSION.matcher(value).matches()) {
            throw error("MANIFEST_VERSION_INVALID", field + " 必须使用 major.minor.patch");
        }
    }

    private void requireInstant(String value) {
        try {
            Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw error("MANIFEST_RELEASED_AT_INVALID", "releasedAt 必须是 UTC ISO-8601 时间");
        }
    }

    private void requireDate(String value) {
        try {
            LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw error("MANIFEST_EFFECTIVE_FROM_INVALID", "effectiveFrom 必须是 ISO-8601 日期");
        }
    }

    private int[] versionParts(String value) {
        String[] parts = value.split("\\.");
        try {
            return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]) };
        } catch (NumberFormatException ex) {
            throw error("MANIFEST_VERSION_INVALID", "版本号超出整数范围：" + value);
        }
    }

    private String normalizeLegacyCode(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "legacy-package";
        int extension = normalized.lastIndexOf('.');
        if (extension > 0) {
            normalized = normalized.substring(0, extension);
        }
        normalized = normalized.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return StringUtils.hasText(normalized) ? normalized : "legacy-package";
    }

    private String legacyText(Map<?, ?> source, String field) {
        Object value = source.get(field);
        return value == null ? null : String.valueOf(value);
    }

    private String contentChecksum(Map<String, String> files) {
        StringBuilder canonical = new StringBuilder();
        new TreeMap<>(files).forEach((fileName, checksum) -> canonical.append(fileName).append(':').append(checksum).append('\n'));
        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 算法不可用", ex);
        }
    }

    private StandardPackageContractException error(String code, String message) {
        return new StandardPackageContractException(code, message);
    }

    public record Dependency(String packageCode, String minimumVersion) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Manifest(
        String schemaVersion,
        String packageCode,
        String packageName,
        String packageVersion,
        String category,
        String industry,
        String releasedAt,
        String effectiveFrom,
        List<Dependency> dependencies,
        List<String> replaces,
        boolean deprecated,
        String sourceRegisterRef,
        String licenseConclusion,
        Map<String, String> files,
        String contentChecksum
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Catalog(String schemaVersion, List<Manifest> packages) {}

    public record InstalledPackage(String packageCode, String packageVersion, String contentChecksum) {}
}
