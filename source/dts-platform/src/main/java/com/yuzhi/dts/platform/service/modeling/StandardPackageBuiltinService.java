package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRun;
import com.yuzhi.dts.platform.repository.modeling.StandardPackageImportRunRepository;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 内置国标包：清单来自 classpath standard-packages/manifest.json，安装复用
 * 标准包导入管道（preview → apply），与客户上传包完全同构。
 */
@Service
@Transactional
public class StandardPackageBuiltinService {

    public static final String SOURCE_BUILTIN = "BUILTIN";

    private static final String BASE_PATH = "standard-packages/";
    private static final List<String> PACKAGE_FILES = List.of(
        StandardPackageImportService.FILE_TERMS,
        StandardPackageImportService.FILE_ELEMENTS,
        StandardPackageImportService.FILE_CODE_DIRECTORIES,
        StandardPackageImportService.FILE_CODE_ITEMS,
        StandardPackageImportService.FILE_CODE_MAPPINGS
    );

    private final StandardPackageImportService importService;
    private final StandardPackageApplyService applyService;
    private final StandardPackageImportRunRepository runRepository;
    private final ObjectMapper objectMapper;
    private final StandardPackageManifestContract manifestContract;

    public StandardPackageBuiltinService(
        StandardPackageImportService importService,
        StandardPackageApplyService applyService,
        StandardPackageImportRunRepository runRepository,
        ObjectMapper objectMapper,
        StandardPackageManifestContract manifestContract
    ) {
        this.importService = importService;
        this.applyService = applyService;
        this.runRepository = runRepository;
        this.objectMapper = objectMapper;
        this.manifestContract = manifestContract;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listBuiltin() {
        StandardPackageManifestContract.Catalog catalog = readCatalog();
        Map<String, Map<String, Object>> metadata = readCatalogMetadata();
        Map<String, StandardPackageManifestContract.InstalledPackage> installedPackages = installedPackages();
        List<StandardPackageManifestContract.Manifest> packages = StandardPackageManifestContract.SCHEMA_V2.equals(catalog.schemaVersion())
            ? manifestContract.installationOrder(catalog)
            : catalog.packages();
        List<Map<String, Object>> result = new ArrayList<>();
        for (StandardPackageManifestContract.Manifest pkg : packages) {
            String code = pkg.packageCode();
            manifestContract.verifyFiles(pkg, loadPackageFiles(code));
            Map<String, Object> item = new LinkedHashMap<>(manifestContract.summary(pkg));
            item.put("code", code);
            item.put("name", pkg.packageName());
            item.putAll(metadata.getOrDefault(code, Map.of()));
            StandardPackageManifestContract.InstalledPackage installed = installedPackages.get(code);
            item.put("installed", installed != null);
            item.put("installedVersion", installed == null ? null : installed.packageVersion());
            item.put("dependencyReady", isInstallable(pkg, installedPackages));
            item.put("entryCounts", countEntries(code));
            result.add(item);
        }
        return result;
    }

    /** 安装：classpath CSV 直接进 preview 管道，无阻塞错误则自动 apply。 */
    public Map<String, Object> install(String code, String actor) {
        StandardPackageManifestContract.Manifest manifestEntry = readCatalog()
            .packages()
            .stream()
            .filter(pkg -> code.equals(pkg.packageCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("内置标准包不存在：" + code));

        manifestContract.requireInstallable(manifestEntry, installedPackages());
        Map<String, byte[]> entries = loadPackageFiles(code);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("内置标准包资源缺失：" + code);
        }
        entries.put(StandardPackageImportService.FILE_MANIFEST, manifestContract.writePackage(manifestEntry));
        Map<String, Object> preview = importService.preview(entries, code, SOURCE_BUILTIN, actor);
        if (Boolean.TRUE.equals(preview.get("blocking"))) {
            // 内置包出错属资源缺陷：返回报告便于定位，不入库
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("applied", false);
            result.put("preview", preview);
            return result;
        }
        Map<String, Object> applied = applyService.apply(UUID.fromString(String.valueOf(preview.get("runId"))), actor);
        Map<String, Object> result = new LinkedHashMap<>(applied);
        result.put("applied", true);
        result.put("packageCode", code);
        result.put("packageName", manifestEntry.packageName());
        return result;
    }

    // ---------- helpers ----------

    private StandardPackageManifestContract.Catalog readCatalog() {
        byte[] catalog = readCatalogBytes();
        try {
            return manifestContract.parseCatalog(catalog);
        } catch (StandardPackageContractException ex) {
            if (!"MANIFEST_SCHEMA_UNSUPPORTED".equals(ex.code())) {
                throw ex;
            }
            return manifestContract.parseCatalogCompatible(catalog, loadLegacyPackageEntries(catalog));
        }
    }

    private Map<String, Map<String, byte[]>> loadLegacyPackageEntries(byte[] catalog) {
        try {
            Map<String, Object> root = objectMapper.readValue(catalog, new TypeReference<Map<String, Object>>() {});
            Object packages = root.get("packages");
            if (!(packages instanceof List<?> list)) {
                return Map.of();
            }
            Map<String, Map<String, byte[]>> entries = new LinkedHashMap<>();
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> pkg && pkg.get("code") != null) {
                    String code = String.valueOf(pkg.get("code"));
                    entries.put(code, loadPackageFiles(code));
                }
            }
            return entries;
        } catch (Exception ex) {
            throw new IllegalStateException("内置标准包 v1 清单适配失败");
        }
    }

    private Map<String, Map<String, Object>> readCatalogMetadata() {
        try {
            Map<String, Object> manifest = objectMapper.readValue(readCatalogBytes(), new TypeReference<Map<String, Object>>() {});
            Object packages = manifest.get("packages");
            if (packages instanceof List<?> list) {
                Map<String, Map<String, Object>> result = new LinkedHashMap<>();
                for (Object entry : list) {
                    if (entry instanceof Map<?, ?> map) {
                        Object rawCode = map.containsKey("packageCode") ? map.get("packageCode") : map.get("code");
                        String code = String.valueOf(rawCode);
                        Map<String, Object> display = new LinkedHashMap<>();
                        copyIfPresent(map, display, "standardNo");
                        copyIfPresent(map, display, "description");
                        result.put(code, display);
                    }
                }
                return result;
            }
            return Map.of();
        } catch (Exception ex) {
            throw new IllegalStateException("内置标准包清单读取失败");
        }
    }

    private byte[] readCatalogBytes() {
        try (InputStream in = new ClassPathResource(BASE_PATH + "manifest.json").getInputStream()) {
            return in.readAllBytes();
        } catch (Exception ex) {
            throw new IllegalStateException("内置标准包清单读取失败");
        }
    }

    private Map<String, StandardPackageManifestContract.InstalledPackage> installedPackages() {
        Map<String, StandardPackageManifestContract.InstalledPackage> installed = new LinkedHashMap<>();
        for (StandardPackageImportRun run : runRepository.findByStatusOrderByCreatedDateDesc(StandardPackageApplyService.STATUS_APPLIED)) {
            String packageCode = run.getPackageName();
            String packageVersion = "1.0.0";
            String contentChecksum = null;
            if (StringUtils.hasText(run.getPreviewJson())) {
                try {
                    JsonNode preview = objectMapper.readTree(run.getPreviewJson());
                    packageCode = textOrDefault(preview, "packageCode", packageCode);
                    packageVersion = textOrDefault(preview, "packageVersion", packageVersion);
                    contentChecksum = textOrDefault(preview, "contentChecksum", null);
                } catch (Exception ex) {
                    throw new IllegalStateException("已安装标准包状态解析失败：" + run.getId(), ex);
                }
            }
            if (StringUtils.hasText(packageCode)) {
                installed.putIfAbsent(
                    packageCode,
                    new StandardPackageManifestContract.InstalledPackage(packageCode, packageVersion, contentChecksum)
                );
            }
        }
        return Map.copyOf(installed);
    }

    private boolean isInstallable(
        StandardPackageManifestContract.Manifest manifest,
        Map<String, StandardPackageManifestContract.InstalledPackage> installed
    ) {
        try {
            manifestContract.requireInstallable(manifest, installed);
            return true;
        } catch (StandardPackageContractException ex) {
            return false;
        }
    }

    private String textOrDefault(JsonNode node, String field, String defaultValue) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() && StringUtils.hasText(value.asText()) ? value.asText() : defaultValue;
    }

    private void copyIfPresent(Map<?, ?> source, Map<String, Object> target, String field) {
        if (source.containsKey(field)) {
            target.put(field, source.get(field));
        }
    }

    private Map<String, byte[]> loadPackageFiles(String code) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (String fileName : PACKAGE_FILES) {
            ClassPathResource resource = new ClassPathResource(BASE_PATH + code + "/" + fileName);
            if (!resource.exists()) {
                continue;
            }
            try (InputStream in = resource.getInputStream()) {
                entries.put(fileName, in.readAllBytes());
            } catch (Exception ex) {
                throw new IllegalStateException("内置标准包文件读取失败：" + fileName);
            }
        }
        return entries;
    }

    private Map<String, Integer> countEntries(String code) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : loadPackageFiles(code).entrySet()) {
            String content = new String(entry.getValue(), StandardCharsets.UTF_8);
            int rows = 0;
            String[] lines = content.split("\r?\n");
            for (int i = 1; i < lines.length; i++) {
                if (StringUtils.hasText(lines[i])) {
                    rows++;
                }
            }
            counts.put(entry.getKey(), rows);
        }
        return counts;
    }
}
