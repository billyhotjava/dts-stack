package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.domain.modeling.DataSecurityLevel;
import com.yuzhi.dts.platform.domain.modeling.DataStandard;
import com.yuzhi.dts.platform.domain.modeling.DataStandardStatus;
import com.yuzhi.dts.platform.repository.modeling.DataStandardRepository;
import com.yuzhi.dts.platform.service.modeling.dto.DataStandardImportResultDto;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class DataStandardImportService {

    private static final String TEMPLATE =
        "code,name,domain,scope,owner,tags,security_level,status,version,version_notes,description\n" +
        "STD_FIN_001,项目预算金额,财务,全院,财务管理员,\"金额,预算\",内部,ACTIVE,v1,初版,示例：预算金额（元）\n";

    private final DataStandardRepository repository;
    private final DataStandardService standardService;

    public DataStandardImportService(DataStandardRepository repository, DataStandardService standardService) {
        this.repository = repository;
        this.standardService = standardService;
    }

    public byte[] buildTemplateCsv() {
        return TEMPLATE.getBytes(StandardCharsets.UTF_8);
    }

    public DataStandardImportResultDto importCsv(MultipartFile file) {
        DataStandardImportResultDto result = new DataStandardImportResultDto();
        if (file == null || file.isEmpty()) {
            result.addError("未上传文件或文件为空");
            return result;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (!StringUtils.hasText(headerLine)) {
                result.addError("CSV 表头为空");
                return result;
            }
            List<String> headers = parseCsvLine(stripBom(headerLine));
            Map<String, Integer> index = buildHeaderIndex(headers);
            if (!index.containsKey("code") || !index.containsKey("name")) {
                result.addError("CSV 必须包含表头：code,name");
                return result;
            }

            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                List<String> values = parseCsvLine(line);
                String code = get(values, index, "code");
                String name = get(values, index, "name");
                if (!StringUtils.hasText(code) || !StringUtils.hasText(name)) {
                    result.addError("第 " + rowNumber + " 行：code 或 name 为空，已跳过");
                    result.setSkipped(result.getSkipped() + 1);
                    continue;
                }

                DataStandardUpsertRequest request = new DataStandardUpsertRequest();
                request.setCode(code.trim());
                request.setName(name.trim());
                request.setDomain(trimToNull(get(values, index, "domain")));
                request.setScope(trimToNull(get(values, index, "scope")));
                request.setOwner(trimToNull(get(values, index, "owner")));
                request.setTags(parseTags(get(values, index, "tags")));
                request.setStatus(parseStatus(get(values, index, "status")));
                request.setVersion(trimToNull(get(values, index, "version")));
                request.setVersionNotes(trimToNull(get(values, index, "version_notes")));
                request.setDescription(trimToNull(get(values, index, "description")));
                request.setSecurityLevel(parseDataSecurityLevel(get(values, index, "security_level")));

	                try {
	                    Optional<DataStandard> existing = repository.findByCodeIgnoreCase(request.getCode());
	                    existing.ifPresentOrElse(
	                        standard -> {
	                            standardService.update(standard.getId(), request, null);
	                            result.setUpdated(result.getUpdated() + 1);
	                        },
	                        () -> {
	                            standardService.create(request, null);
	                            result.setCreated(result.getCreated() + 1);
	                        }
	                    );
	                } catch (RuntimeException e) {
	                    result.addError("第 " + rowNumber + " 行：" + request.getCode() + " 导入失败：" + safeMessage(e));
	                    result.setSkipped(result.getSkipped() + 1);
	                } finally {
	                    result.setTotalRows(result.getTotalRows() + 1);
                }
            }
        } catch (Exception e) {
            result.addError("读取 CSV 失败：" + safeMessage(e));
        }
        return result;
    }

    private Map<String, Integer> buildHeaderIndex(List<String> headers) {
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i);
            if (!StringUtils.hasText(h)) continue;
            String key = h.trim().toLowerCase(Locale.ROOT);
            index.put(key, i);
        }
        return index;
    }

    private String get(List<String> values, Map<String, Integer> index, String key) {
        Integer i = index.get(key);
        if (i == null) return null;
        if (i < 0 || i >= values.size()) return null;
        String v = values.get(i);
        return StringUtils.hasText(v) ? v.trim() : null;
    }

    private List<String> parseTags(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        String[] parts = raw.split("[,;，；\\s]+");
        List<String> tags = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part == null ? "" : part.trim();
            if (trimmed.isEmpty()) continue;
            tags.add(trimmed);
        }
        return tags;
    }

    private DataStandardStatus parseStatus(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String token = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return DataStandardStatus.valueOf(token);
        } catch (Exception ignored) {
            return null;
        }
    }

    private DataSecurityLevel parseDataSecurityLevel(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        SecurityLevelCatalog.DataSecurityLevel parsed = SecurityLevelCatalog.DataSecurityLevel.parse(raw.trim());
        if (parsed == null) {
            return null;
        }
        return switch (parsed) {
            case PUBLIC -> DataSecurityLevel.PUBLIC;
            case INTERNAL -> DataSecurityLevel.INTERNAL;
            case SECRET -> DataSecurityLevel.SECRET;
            case CONFIDENTIAL -> DataSecurityLevel.CONFIDENTIAL;
        };
    }

    private List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        if (line == null) {
            return out;
        }
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }
            if (c == ',' && !inQuotes) {
                out.add(cell.toString().trim());
                cell.setLength(0);
                continue;
            }
            cell.append(c);
        }
        out.add(cell.toString().trim());
        return out;
    }

    private String stripBom(String line) {
        if (line == null || line.isEmpty()) {
            return line;
        }
        if (line.charAt(0) == '\uFEFF') {
            return line.substring(1);
        }
        return line;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safeMessage(Throwable e) {
        if (e == null) return "未知错误";
        String message = e.getMessage();
        return StringUtils.hasText(message) ? message : e.getClass().getSimpleName();
    }
}
