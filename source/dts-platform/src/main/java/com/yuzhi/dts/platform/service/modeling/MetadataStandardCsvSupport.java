package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.domain.modeling.DataSecurityLevel;
import com.yuzhi.dts.platform.domain.modeling.MetadataStandard;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * 数据元 CSV 行校验的单一事实源：MetadataStandardImportService（单文件直导）与
 * StandardPackageImportService（标准包导入）共用，避免两套校验漂移。
 */
public final class MetadataStandardCsvSupport {

    public static final Set<String> ALLOWED_TYPES = Set.of(
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

    private MetadataStandardCsvSupport() {}

    public record ElementRow(MetadataStandardUpsertRequest request, String error) {
        public boolean hasError() {
            return error != null;
        }
    }

    /**
     * 校验一行数据元并组装 upsert 请求。column 以表头名取值（已裁剪空白，缺列返回 null）。
     * 出错时返回与既有单文件导入完全一致的消息体（不含行号前缀，由调用方补）。
     */
    public static ElementRow buildElementRow(Function<String, String> column) {
        String fieldNameCn = column.apply("field_name_cn");
        String fieldNameEn = column.apply("field_name_en");
        String dataTypeRaw = column.apply("data_type");
        String nullableRaw = column.apply("nullable");
        String domain = column.apply("domain");
        String description = column.apply("description");
        String sourceSystem = column.apply("source_system");

        if (!StringUtils.hasText(fieldNameCn) || !StringUtils.hasText(fieldNameEn)) {
            return new ElementRow(null, "field_name_cn 或 field_name_en 为空，已跳过");
        }
        if (!StringUtils.hasText(dataTypeRaw)) {
            return new ElementRow(null, fieldNameEn + " data_type 为空，已跳过");
        }
        if (!StringUtils.hasText(nullableRaw)) {
            return new ElementRow(null, fieldNameEn + " nullable 为空，已跳过");
        }
        if (!StringUtils.hasText(domain)) {
            return new ElementRow(null, fieldNameEn + " domain 为空，已跳过");
        }
        if (!StringUtils.hasText(description)) {
            return new ElementRow(null, fieldNameEn + " description 为空，已跳过");
        }
        if (!StringUtils.hasText(sourceSystem)) {
            return new ElementRow(null, fieldNameEn + " source_system 为空，已跳过");
        }

        Boolean nullable = parseBooleanYN(nullableRaw);
        if (nullable == null) {
            return new ElementRow(null, fieldNameEn + " nullable 仅支持 Y/N");
        }

        TypeParts typeParts = parseTypeParts(dataTypeRaw);
        String normalizedType = typeParts.baseType();
        if (!ALLOWED_TYPES.contains(normalizedType)) {
            return new ElementRow(null, fieldNameEn + " data_type 不在建议集合内");
        }

        Integer dataLength = parseInteger(column.apply("data_length"));
        Integer dataPrecision = parseInteger(column.apply("data_precision"));
        Integer dataScale = parseInteger(column.apply("data_scale"));

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
            return new ElementRow(null, fieldNameEn + " VARCHAR 未填写 data_length");
        }
        if (("DECIMAL".equals(normalizedType) || "NUMERIC".equals(normalizedType)) && dataPrecision == null) {
            return new ElementRow(null, fieldNameEn + " DECIMAL 未填写 data_precision");
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
        req.setCodeSet(trimToNull(column.apply("code_set")));
        req.setDefaultValue(trimToNull(column.apply("default_value")));
        req.setIsPk(parseBooleanYN(column.apply("is_pk")));
        req.setSecurityLevel(parseSecurityLevel(column.apply("security_level")));
        return new ElementRow(req, null);
    }

    public static Integer parseInteger(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static Boolean parseBooleanYN(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if ("Y".equals(normalized) || "YES".equals(normalized) || "TRUE".equals(normalized)) return Boolean.TRUE;
        if ("N".equals(normalized) || "NO".equals(normalized) || "FALSE".equals(normalized)) return Boolean.FALSE;
        return null;
    }

    public static DataSecurityLevel parseSecurityLevel(String raw) {
        if (!StringUtils.hasText(raw)) return DataSecurityLevel.INTERNAL;
        SecurityLevelCatalog.DataSecurityLevel parsed = SecurityLevelCatalog.DataSecurityLevel.parse(raw);
        return parsed == null ? DataSecurityLevel.INTERNAL : DataSecurityLevel.valueOf(parsed.code());
    }

    /** 数据元更新前的 before-image（供导入 run 回滚还原），字段与回滚 restore 一一对应。 */
    public static Map<String, Object> elementImage(MetadataStandard element) {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("fieldNameCn", element.getFieldNameCn());
        image.put("fieldNameEn", element.getFieldNameEn());
        image.put("dataType", element.getDataType());
        image.put("dataLength", element.getDataLength());
        image.put("dataPrecision", element.getDataPrecision());
        image.put("dataScale", element.getDataScale());
        image.put("nullable", element.getNullable());
        image.put("domain", element.getDomain());
        image.put("description", element.getDescription());
        image.put("sourceSystem", element.getSourceSystem());
        image.put("codeSet", element.getCodeSet());
        image.put("defaultValue", element.getDefaultValue());
        image.put("isPk", element.getIsPk());
        image.put("securityLevel", element.getSecurityLevel() == null ? null : element.getSecurityLevel().name());
        return image;
    }

    public static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static TypeParts parseTypeParts(String raw) {
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

    private record TypeParts(String baseType, Integer length, Integer precision, Integer scale) {}
}
