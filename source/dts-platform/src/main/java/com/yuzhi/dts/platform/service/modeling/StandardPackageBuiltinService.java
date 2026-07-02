package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    public StandardPackageBuiltinService(
        StandardPackageImportService importService,
        StandardPackageApplyService applyService,
        StandardPackageImportRunRepository runRepository,
        ObjectMapper objectMapper
    ) {
        this.importService = importService;
        this.applyService = applyService;
        this.runRepository = runRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listBuiltin() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> pkg : readManifest()) {
            String code = String.valueOf(pkg.get("code"));
            Map<String, Object> item = new LinkedHashMap<>(pkg);
            item.put("installed", runRepository.existsByPackageNameAndSourceAndStatus(code, SOURCE_BUILTIN, StandardPackageApplyService.STATUS_APPLIED));
            item.put("entryCounts", countEntries(code));
            result.add(item);
        }
        return result;
    }

    /** 安装：classpath CSV 直接进 preview 管道，无阻塞错误则自动 apply。 */
    public Map<String, Object> install(String code, String actor) {
        Map<String, Object> manifestEntry = readManifest()
            .stream()
            .filter(pkg -> code.equals(pkg.get("code")))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("内置标准包不存在：" + code));

        Map<String, byte[]> entries = loadPackageFiles(code);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("内置标准包资源缺失：" + code);
        }
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
        result.put("packageName", manifestEntry.get("name"));
        return result;
    }

    // ---------- helpers ----------

    private List<Map<String, Object>> readManifest() {
        try (InputStream in = new ClassPathResource(BASE_PATH + "manifest.json").getInputStream()) {
            Map<String, Object> manifest = objectMapper.readValue(in, new TypeReference<Map<String, Object>>() {});
            Object packages = manifest.get("packages");
            if (packages instanceof List<?> list) {
                List<Map<String, Object>> result = new ArrayList<>();
                for (Object entry : list) {
                    if (entry instanceof Map<?, ?> map) {
                        Map<String, Object> normalized = new LinkedHashMap<>();
                        map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
                        result.add(normalized);
                    }
                }
                return result;
            }
            return List.of();
        } catch (Exception ex) {
            throw new IllegalStateException("内置标准包清单读取失败");
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
