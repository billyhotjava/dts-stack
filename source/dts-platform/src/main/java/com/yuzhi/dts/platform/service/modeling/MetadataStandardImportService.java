package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.domain.modeling.DataSecurityLevel;
import com.yuzhi.dts.platform.domain.modeling.MetadataStandard;
import com.yuzhi.dts.platform.repository.modeling.MetadataStandardRepository;
import com.yuzhi.dts.platform.service.modeling.dto.MetadataStandardImportResultDto;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class MetadataStandardImportService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
        "VARCHAR",
        "CHAR",
        "TEXT",
        "INT",
        "INTEGER",
        "BIGINT",
        "DECIMAL",
        "NUMERIC",
        "DOUBLE",
        "FLOAT",
        "DATE",
        "TIMESTAMP",
        "BOOLEAN"
    );

    private static final Pattern TYPE_WITH_SIZE = Pattern.compile("([A-Z0-9_]+)\\s*\\((\\d+)(?:\\s*,\\s*(\\d+))?\\)");

    private final MetadataStandardRepository repository;
    private final MetadataStandardService service;

    public MetadataStandardImportService(MetadataStandardRepository repository, MetadataStandardService service) {
        this.repository = repository;
        this.service = service;
    }

    public MetadataStandardImportResultDto importCsv(MultipartFile file) {
        MetadataStandardImportResultDto result = new MetadataStandardImportResultDto();
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
            List<String> headers = CsvUtils.parseCsvLine(CsvUtils.stripBom(headerLine));
            Map<String, Integer> index = buildHeaderIndex(headers);
            if (!index.containsKey("field_name_cn") || !index.containsKey("field_name_en")) {
                result.addError("CSV 必须包含表头：field_name_cn,field_name_en");
                return result;
            }

            String line;
            int rowNumber = 1;
            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                List<String> values = CsvUtils.parseCsvLine(line);
                String fieldNameCn = get(values, index, "field_name_cn");
                String fieldNameEn = get(values, index, "field_name_en");
                String dataTypeRaw = get(values, index, "data_type");
                String nullableRaw = get(values, index, "nullable");
                String domain = get(values, index, "domain");
                String description = get(values, index, "description");
                String sourceSystem = get(values, index, "source_system");

                if (!StringUtils.hasText(fieldNameCn) || !StringUtils.hasText(fieldNameEn)) {
                    result.addError("第 " + rowNumber + " 行：field_name_cn 或 field_name_en 为空，已跳过");
                    result.setSkipped(result.getSkipped() + 1);
                    continue;
                }
                if (!StringUtils.hasText(dataTypeRaw)) {
                    result.addError("第 " + rowNumber + " 行：" + fieldNameEn + " data_type 为空，已跳过");
                    result.setSkipped(result.getSkipped() + 1);
                    continue;
                }
                if (!StringUtils.hasText(nullableRaw)) {
                    result.addError("第 " + rowNumber + " 行：" + fieldNameEn + " nullable 为空，已跳过");
                    result.setSkipped(result.getSkipped() + 1);
                    continue;
                }
                if (!StringUtils.hasText(domain)) {
                    result.addError("第 " + rowNumber + " 行：" + fieldNameEn + " domain 为空，已跳过");
                    result.setSkipped(result.getSkipped() + 1);
                    continue;
                }
                if (!StringUtils.hasText(description)) {
                    result.addError("第 " + rowNumber + " 行：" + fieldNameEn + " description 为空，已跳过");
                    result.setSkipped(result.getSkipped() + 1);
                    continue;
                }
                if (!StringUtils.hasText(sourceSystem)) {
                    result.addError("第 " + rowNumber + " 行：" + fieldNameEn + " source_system 为空，已跳过");
                    result.setSkipped(result.getSkipped() + 1);
                    continue;
                }

                Boolean nullable = parseBooleanYN(nullableRaw);
                if (nullable == null) {
                    result.addError("第 " + rowNumber + " 行：" + fieldNameEn + " nullable 仅支持 Y/N");
                    result.setSkipped(result.getSkipped() + 1);
                    continue;
                }

                TypeParts typeParts = parseTypeParts(dataTypeRaw);
                String normalizedType = typeParts.baseType();
                if (!ALLOWED_TYPES.contains(normalizedType)) {
                    result.addError("第 " + rowNumber + " 行：" + fieldNameEn + " data_type 不在建议集合内");
                    result.setSkipped(result.getSkipped() + 1);
                    continue;
                }

                Integer dataLength = parseInteger(get(values, index, "data_length"));
                Integer dataPrecision = parseInteger(get(values, index, "data_precision"));
                Integer dataScale = parseInteger(get(values, index, "data_scale"));

                if (dataLength == null && typeParts.length() != null) {
                    dataLength = typeParts.length();
                }
                if (dataPrecision == null && typeParts.precision() != null) {
                    dataPrecision = typeParts.precision();
                }
                if (dataScale == null && typeParts.scale() != null) {
                    dataScale = typeParts.scale();
                }

                if ("VARCHAR".equals(normalizedType) && dataLength == null) {
                    result.addError("第 " + rowNumber + " 行：" + fieldNameEn + " VARCHAR 未填写 data_length");
                    result.setSkipped(result.getSkipped() + 1);
                    continue;
                }
                if (("DECIMAL".equals(normalizedType) || "NUMERIC".equals(normalizedType)) && dataPrecision == null) {
                    result.addError("第 " + rowNumber + " 行：" + fieldNameEn + " DECIMAL 未填写 data_precision");
                    result.setSkipped(result.getSkipped() + 1);
                    continue;
                }

                MetadataStandardUpsertRequest req = new MetadataStandardUpsertRequest();
                req.setFieldNameCn(fieldNameCn.trim());
                req.setFieldNameEn(fieldNameEn.trim());
                req.setDataType(normalizedType);
                req.setDataLength(dataLength);
                req.setDataPrecision(dataPrecision);
                req.setDataScale(dataScale);
                req.setNullable(nullable);
                req.setDomain(domain.trim());
                req.setDescription(description.trim());
                req.setSourceSystem(sourceSystem.trim());
                req.setCodeSet(trimToNull(get(values, index, "code_set")));
                req.setDefaultValue(trimToNull(get(values, index, "default_value")));
                req.setIsPk(parseBooleanYN(get(values, index, "is_pk")));
                req.setSecurityLevel(parseSecurityLevel(get(values, index, "security_level")));

                try {
                    Optional<MetadataStandard> existing = repository.findByFieldNameEnIgnoreCaseAndDomainIgnoreCase(
                        req.getFieldNameEn(),
                        req.getDomain()
                    );
                    existing.ifPresentOrElse(
                        standard -> {
                            service.update(standard.getId(), req);
                            result.setUpdated(result.getUpdated() + 1);
                        },
                        () -> {
                            service.create(req);
                            result.setCreated(result.getCreated() + 1);
                        }
                    );
                } catch (RuntimeException ex) {
                    result.addError("第 " + rowNumber + " 行：" + req.getFieldNameEn() + " 导入失败：" + safeMessage(ex));
                    result.setSkipped(result.getSkipped() + 1);
                } finally {
                    result.setTotalRows(result.getTotalRows() + 1);
                }
            }
        } catch (Exception ex) {
            result.addError("读取 CSV 失败：" + safeMessage(ex));
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

    private Integer parseInteger(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Boolean parseBooleanYN(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if ("Y".equals(normalized) || "YES".equals(normalized) || "TRUE".equals(normalized)) return Boolean.TRUE;
        if ("N".equals(normalized) || "NO".equals(normalized) || "FALSE".equals(normalized)) return Boolean.FALSE;
        return null;
    }

    private DataSecurityLevel parseSecurityLevel(String raw) {
        if (!StringUtils.hasText(raw)) return DataSecurityLevel.INTERNAL;
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return DataSecurityLevel.valueOf(normalized);
        } catch (Exception ex) {
            return DataSecurityLevel.INTERNAL;
        }
    }

    private TypeParts parseTypeParts(String raw) {
        String text = raw.trim().toUpperCase(Locale.ROOT);
        Matcher matcher = TYPE_WITH_SIZE.matcher(text);
        if (matcher.find()) {
            String base = matcher.group(1);
            Integer p1 = parseInteger(matcher.group(2));
            Integer p2 = parseInteger(matcher.group(3));
            if ("DECIMAL".equals(base) || "NUMERIC".equals(base)) {
                return new TypeParts(base, null, p1, p2);
            }
            return new TypeParts(base, p1, null, null);
        }
        return new TypeParts(text, null, null, null);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safeMessage(Exception ex) {
        String msg = ex.getMessage();
        return StringUtils.hasText(msg) ? msg : ex.getClass().getSimpleName();
    }

    private record TypeParts(String baseType, Integer length, Integer precision, Integer scale) {}
}
