package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.governance.StdCodeDirectory;
import com.yuzhi.dts.platform.domain.governance.StdCodeValue;
import com.yuzhi.dts.platform.domain.modeling.MetadataStandard;
import com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm;
import com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRun;
import com.yuzhi.dts.platform.repository.governance.StdCodeDirectoryRepository;
import com.yuzhi.dts.platform.repository.governance.StdCodeValueRepository;
import com.yuzhi.dts.platform.repository.modeling.MetadataStandardRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingGlossaryTermRepository;
import com.yuzhi.dts.platform.repository.modeling.StandardPackageImportRunRepository;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 标准包（数据元+公共码表+业务术语）导入管道：preview 阶段解析 zip、做跨文件引用校验并落 run 记录，
 * 不写任何业务表；apply/rollback 基于 run 记录执行（见 StandardPackageApplyService）。
 */
@Service
@Transactional
public class StandardPackageImportService {

    public static final String FILE_TERMS = "01-business-terms.csv";
    public static final String FILE_ELEMENTS = "02-data-elements.csv";
    public static final String FILE_CODE_DIRECTORIES = "03-reference-code-directories.csv";
    public static final String FILE_CODE_ITEMS = "04-reference-code-items.csv";
    public static final String FILE_CODE_MAPPINGS = "05-reference-code-mappings.csv";

    public static final String STATUS_PREVIEWED = "PREVIEWED";
    public static final String SOURCE_UPLOAD = "UPLOAD";

    private static final Set<String> KNOWN_FILES = Set.of(
        FILE_TERMS,
        FILE_ELEMENTS,
        FILE_CODE_DIRECTORIES,
        FILE_CODE_ITEMS,
        FILE_CODE_MAPPINGS
    );

    private static final int MAX_ENTRIES = 64;
    private static final long MAX_ENTRY_BYTES = 10L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 40L * 1024 * 1024;

    private final MetadataStandardRepository metadataStandardRepository;
    private final ModelingGlossaryTermRepository glossaryTermRepository;
    private final StdCodeDirectoryRepository codeDirectoryRepository;
    private final StdCodeValueRepository codeValueRepository;
    private final StandardPackageImportRunRepository runRepository;
    private final ObjectMapper objectMapper;

    public StandardPackageImportService(
        MetadataStandardRepository metadataStandardRepository,
        ModelingGlossaryTermRepository glossaryTermRepository,
        StdCodeDirectoryRepository codeDirectoryRepository,
        StdCodeValueRepository codeValueRepository,
        StandardPackageImportRunRepository runRepository,
        ObjectMapper objectMapper
    ) {
        this.metadataStandardRepository = metadataStandardRepository;
        this.glossaryTermRepository = glossaryTermRepository;
        this.codeDirectoryRepository = codeDirectoryRepository;
        this.codeValueRepository = codeValueRepository;
        this.runRepository = runRepository;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> previewZip(MultipartFile file, String actor) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("未上传文件或文件为空");
        }
        String packageName = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "standard-package.zip";
        Map<String, byte[]> entries;
        try (InputStream in = file.getInputStream()) {
            entries = readZipEntries(in);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("读取压缩包失败：" + safeMessage(ex));
        }
        return preview(entries, packageName, SOURCE_UPLOAD, actor);
    }

    /**
     * 通用入口：entries 为「包内文件名 → CSV 内容」。内置包安装（classpath 来源）复用此入口，免 zip 环节。
     */
    public Map<String, Object> preview(Map<String, byte[]> entries, String packageName, String source, String actor) {
        Map<String, ParsedCsv> parsed = new LinkedHashMap<>();
        for (String known : List.of(FILE_TERMS, FILE_ELEMENTS, FILE_CODE_DIRECTORIES, FILE_CODE_ITEMS, FILE_CODE_MAPPINGS)) {
            byte[] content = entries.get(known);
            if (content != null) {
                parsed.put(known, parseCsv(known, content));
            }
        }
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("压缩包中未找到标准包 CSV 文件（01~05）");
        }

        PackagePayload payload = new PackagePayload();
        // 校验按依赖序（目录先于码值/映射/数据元），报告按 01~05 呈现
        List<Map<String, Object>> fileReports = new ArrayList<>();
        Map<String, Object> termsReport = validateTerms(parsed.get(FILE_TERMS), payload);
        Map<String, Object> dirReport = validateCodeDirectories(parsed.get(FILE_CODE_DIRECTORIES), payload);
        Map<String, Object> itemsReport = validateCodeItems(parsed.get(FILE_CODE_ITEMS), payload);
        Map<String, Object> mappingsReport = validateCodeMappings(parsed.get(FILE_CODE_MAPPINGS), payload);
        Map<String, Object> elementsReport = validateElements(parsed.get(FILE_ELEMENTS), payload);
        fileReports.add(termsReport);
        fileReports.add(elementsReport);
        fileReports.add(dirReport);
        fileReports.add(itemsReport);
        fileReports.add(mappingsReport);

        int totalErrors = 0;
        for (Map<String, Object> report : fileReports) {
            totalErrors += ((List<?>) report.get("errors")).size();
        }

        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("packageName", packageName);
        preview.put("source", source);
        preview.put("files", fileReports);
        preview.put("totalErrors", totalErrors);
        preview.put("blocking", totalErrors > 0);

        StandardPackageImportRun run = new StandardPackageImportRun();
        run.setPackageName(packageName);
        run.setSource(source);
        run.setStatus(STATUS_PREVIEWED);
        run.setSummary("标准包导入预检：" + summarize(fileReports, totalErrors));
        run.setPreviewJson(writeJson(preview));
        run.setPayloadJson(writeJson(payload.toJsonMap()));
        run.setCreatedBy(StringUtils.hasText(actor) ? actor : "system");
        runRepository.save(run);
        preview.put("runId", run.getId().toString());
        return preview;
    }

    // ---------- zip ----------

    private Map<String, byte[]> readZipEntries(InputStream in) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        long totalBytes = 0;
        int entryCount = 0;
        try (ZipInputStream zis = new ZipInputStream(in, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                entryCount++;
                if (entryCount > MAX_ENTRIES) {
                    throw new IllegalArgumentException("压缩包条目数超限（最多 " + MAX_ENTRIES + " 个）");
                }
                String rawName = entry.getName();
                if (rawName.contains("..")) {
                    throw new IllegalArgumentException("压缩包包含非法路径：" + rawName);
                }
                String baseName = rawName.substring(rawName.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
                if (!KNOWN_FILES.contains(baseName)) {
                    continue;
                }
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                long entryBytes = 0;
                int read;
                while ((read = zis.read(chunk)) > 0) {
                    entryBytes += read;
                    totalBytes += read;
                    if (entryBytes > MAX_ENTRY_BYTES) {
                        throw new IllegalArgumentException("压缩包内单个文件过大：" + baseName);
                    }
                    if (totalBytes > MAX_TOTAL_BYTES) {
                        throw new IllegalArgumentException("压缩包解压后总大小超限");
                    }
                    buffer.write(chunk, 0, read);
                }
                entries.put(baseName, buffer.toByteArray());
            }
        }
        return entries;
    }

    // ---------- csv ----------

    private ParsedCsv parseCsv(String fileName, byte[] content) {
        List<RawRow> rows = new ArrayList<>();
        Map<String, Integer> headerIndex = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (StringUtils.hasText(headerLine)) {
                List<String> headers = CsvUtils.parseCsvLine(CsvUtils.stripBom(headerLine));
                for (int i = 0; i < headers.size(); i++) {
                    String h = headers.get(i);
                    if (StringUtils.hasText(h)) {
                        headerIndex.put(h.trim().toLowerCase(Locale.ROOT), i);
                    }
                }
            }
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                rows.add(new RawRow(lineNumber, CsvUtils.parseCsvLine(line)));
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException(fileName + " 解析失败：" + safeMessage(ex));
        }
        return new ParsedCsv(fileName, headerIndex, rows);
    }

    private String col(ParsedCsv csv, RawRow row, String key) {
        Integer i = csv.headerIndex().get(key);
        if (i == null || i < 0 || i >= row.values().size()) {
            return null;
        }
        String v = row.values().get(i);
        return StringUtils.hasText(v) ? v.trim() : null;
    }

    // ---------- validators ----------

    private Map<String, Object> validateTerms(ParsedCsv csv, PackagePayload payload) {
        List<Map<String, Object>> errors = new ArrayList<>();
        int toCreate = 0;
        int toUpdate = 0;
        int total = 0;
        if (csv != null) {
            if (!csv.headerIndex().containsKey("term_code") || !csv.headerIndex().containsKey("term_name")) {
                return fileReport(FILE_TERMS, true, 0, 0, 0, List.of(error(1, "缺少必需表头：term_code,term_name")));
            }
            Set<String> seen = new HashSet<>();
            Set<String> codes = new HashSet<>();
            for (RawRow row : csv.rows()) {
                total++;
                String code = col(csv, row, "term_code");
                String name = col(csv, row, "term_name");
                if (!StringUtils.hasText(code) || !StringUtils.hasText(name)) {
                    errors.add(error(row.lineNumber(), "term_code 或 term_name 为空"));
                    continue;
                }
                String normalized = code.toLowerCase(Locale.ROOT);
                if (!seen.add(normalized)) {
                    errors.add(error(row.lineNumber(), "term_code 在文件内重复：" + code));
                    continue;
                }
                codes.add(normalized);
                Map<String, Object> term = new LinkedHashMap<>();
                term.put("code", code);
                term.put("name", name);
                term.put("aliases", col(csv, row, "aliases"));
                term.put("definition", col(csv, row, "definition"));
                term.put("domain", col(csv, row, "domain"));
                term.put("ownerDept", col(csv, row, "owner_dept"));
                term.put("owner", col(csv, row, "owner"));
                term.put("tags", col(csv, row, "tags"));
                term.put("version", col(csv, row, "version"));
                term.put("status", col(csv, row, "status"));
                term.put("versionNotes", col(csv, row, "version_notes"));
                payload.terms.add(term);
            }
            if (!codes.isEmpty()) {
                Set<String> existing = new HashSet<>();
                for (ModelingGlossaryTerm term : glossaryTermRepository.findByCodeLowerIn(codes)) {
                    if (term.getCode() != null) {
                        existing.add(term.getCode().toLowerCase(Locale.ROOT));
                    }
                }
                for (Map<String, Object> term : payload.terms) {
                    String normalized = String.valueOf(term.get("code")).toLowerCase(Locale.ROOT);
                    if (existing.contains(normalized)) {
                        toUpdate++;
                    } else {
                        toCreate++;
                    }
                }
            }
        }
        return fileReport(FILE_TERMS, csv != null, total, toCreate, toUpdate, errors);
    }

    private Map<String, Object> validateCodeDirectories(ParsedCsv csv, PackagePayload payload) {
        List<Map<String, Object>> errors = new ArrayList<>();
        int toCreate = 0;
        int toUpdate = 0;
        int total = 0;
        if (csv != null) {
            if (!csv.headerIndex().containsKey("code_type_code") || !csv.headerIndex().containsKey("code_type_name")) {
                return fileReport(FILE_CODE_DIRECTORIES, true, 0, 0, 0, List.of(error(1, "缺少必需表头：code_type_code,code_type_name")));
            }
            Set<String> seen = new HashSet<>();
            for (RawRow row : csv.rows()) {
                total++;
                String code = col(csv, row, "code_type_code");
                String name = col(csv, row, "code_type_name");
                if (!StringUtils.hasText(code) || !StringUtils.hasText(name)) {
                    errors.add(error(row.lineNumber(), "code_type_code 或 code_type_name 为空"));
                    continue;
                }
                String normalized = code.toLowerCase(Locale.ROOT);
                if (!seen.add(normalized)) {
                    errors.add(error(row.lineNumber(), "code_type_code 在文件内重复：" + code));
                    continue;
                }
                String statusRaw = col(csv, row, "status");
                Integer status = MetadataStandardCsvSupport.parseInteger(statusRaw);
                if (StringUtils.hasText(statusRaw) && status == null) {
                    errors.add(error(row.lineNumber(), code + " status 需为整数"));
                    continue;
                }
                Map<String, Object> dir = new LinkedHashMap<>();
                dir.put("codeTypeId", col(csv, row, "code_type_id"));
                dir.put("codeTypeCode", code);
                dir.put("codeTypeName", name);
                dir.put("stdLevel", col(csv, row, "std_level"));
                dir.put("bizCatalog", col(csv, row, "biz_catalog"));
                dir.put("dataType", col(csv, row, "data_type"));
                dir.put("status", status);
                dir.put("ownerDept", col(csv, row, "owner_dept"));
                dir.put("version", col(csv, row, "version"));
                payload.codeDirectories.add(dir);
                payload.packageDirectoryCodes.add(normalized);

                if (codeDirectoryRepository.findByCodeTypeCodeIgnoreCase(code).isPresent()) {
                    toUpdate++;
                } else {
                    toCreate++;
                }
            }
        }
        return fileReport(FILE_CODE_DIRECTORIES, csv != null, total, toCreate, toUpdate, errors);
    }

    private Map<String, Object> validateCodeItems(ParsedCsv csv, PackagePayload payload) {
        List<Map<String, Object>> errors = new ArrayList<>();
        int toCreate = 0;
        int toUpdate = 0;
        int total = 0;
        if (csv != null) {
            List<String> required = List.of("code_type_code", "code_value", "code_name");
            for (String header : required) {
                if (!csv.headerIndex().containsKey(header)) {
                    return fileReport(FILE_CODE_ITEMS, true, 0, 0, 0, List.of(error(1, "缺少必需表头：code_type_code,code_value,code_name")));
                }
            }
            Set<String> seen = new HashSet<>();
            List<Integer> itemLineNumbers = new ArrayList<>();
            for (RawRow row : csv.rows()) {
                total++;
                String typeCode = col(csv, row, "code_type_code");
                String codeValue = col(csv, row, "code_value");
                String codeName = col(csv, row, "code_name");
                if (!StringUtils.hasText(typeCode) || !StringUtils.hasText(codeValue) || !StringUtils.hasText(codeName)) {
                    errors.add(error(row.lineNumber(), "code_type_code/code_value/code_name 不能为空"));
                    continue;
                }
                if (!resolvesDirectory(typeCode, payload)) {
                    errors.add(error(row.lineNumber(), "码表目录不存在（本包与库中均未找到）：" + typeCode));
                    continue;
                }
                String dupKey = typeCode.toLowerCase(Locale.ROOT) + " " + codeValue;
                if (!seen.add(dupKey)) {
                    errors.add(error(row.lineNumber(), typeCode + " 下 code_value 在文件内重复：" + codeValue));
                    continue;
                }
                String sortRaw = col(csv, row, "sort_num");
                Integer sortNum = MetadataStandardCsvSupport.parseInteger(sortRaw);
                if (StringUtils.hasText(sortRaw) && sortNum == null) {
                    errors.add(error(row.lineNumber(), codeValue + " sort_num 需为整数"));
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("codeTypeCode", typeCode);
                item.put("codeValue", codeValue);
                item.put("codeName", codeName);
                item.put("description", col(csv, row, "description"));
                item.put("sortNum", sortNum);
                item.put("parentCode", col(csv, row, "parent_code"));
                item.put("isDefault", MetadataStandardCsvSupport.parseBooleanYN(col(csv, row, "is_default")));
                payload.codeItems.add(item);
                itemLineNumbers.add(row.lineNumber());
                payload
                    .packageItemValues
                    .computeIfAbsent(typeCode.toLowerCase(Locale.ROOT), key -> new HashSet<>())
                    .add(codeValue);

                if (existingItemValues(typeCode, payload).contains(codeValue)) {
                    toUpdate++;
                } else {
                    toCreate++;
                }
            }
            // parent_code 需能在同目录（本包或库中）解析；本包内允许前向引用，故整体解析完再校验
            for (int i = 0; i < payload.codeItems.size(); i++) {
                Map<String, Object> item = payload.codeItems.get(i);
                String parentCode = (String) item.get("parentCode");
                if (!StringUtils.hasText(parentCode)) {
                    continue;
                }
                String typeCode = (String) item.get("codeTypeCode");
                Set<String> inPackage = payload.packageItemValues.getOrDefault(typeCode.toLowerCase(Locale.ROOT), Set.of());
                if (!inPackage.contains(parentCode) && !existingItemValues(typeCode, payload).contains(parentCode)) {
                    errors.add(error(itemLineNumbers.get(i), typeCode + " 下 " + item.get("codeValue") + " 的 parent_code 无法解析：" + parentCode));
                }
            }
        }
        return fileReport(FILE_CODE_ITEMS, csv != null, total, toCreate, toUpdate, errors);
    }

    private Map<String, Object> validateCodeMappings(ParsedCsv csv, PackagePayload payload) {
        List<Map<String, Object>> errors = new ArrayList<>();
        int total = 0;
        int toCreate = 0;
        if (csv != null) {
            List<String> required = List.of("code_type_code", "source_system", "source_code", "standard_code");
            for (String header : required) {
                if (!csv.headerIndex().containsKey(header)) {
                    return fileReport(
                        FILE_CODE_MAPPINGS,
                        true,
                        0,
                        0,
                        0,
                        List.of(error(1, "缺少必需表头：code_type_code,source_system,source_code,standard_code"))
                    );
                }
            }
            Set<String> seen = new HashSet<>();
            for (RawRow row : csv.rows()) {
                total++;
                String typeCode = col(csv, row, "code_type_code");
                String sourceSystem = col(csv, row, "source_system");
                String sourceCode = col(csv, row, "source_code");
                String standardCode = col(csv, row, "standard_code");
                if (
                    !StringUtils.hasText(typeCode) ||
                    !StringUtils.hasText(sourceSystem) ||
                    !StringUtils.hasText(sourceCode) ||
                    !StringUtils.hasText(standardCode)
                ) {
                    errors.add(error(row.lineNumber(), "code_type_code/source_system/source_code/standard_code 不能为空"));
                    continue;
                }
                if (!resolvesDirectory(typeCode, payload)) {
                    errors.add(error(row.lineNumber(), "码表目录不存在（本包与库中均未找到）：" + typeCode));
                    continue;
                }
                Set<String> inPackage = payload.packageItemValues.getOrDefault(typeCode.toLowerCase(Locale.ROOT), Set.of());
                if (!inPackage.contains(standardCode) && !existingItemValues(typeCode, payload).contains(standardCode)) {
                    errors.add(error(row.lineNumber(), typeCode + " 的 standard_code 无法解析：" + standardCode));
                    continue;
                }
                String dupKey = typeCode.toLowerCase(Locale.ROOT) + " " + sourceSystem + " " + sourceCode;
                if (!seen.add(dupKey)) {
                    errors.add(error(row.lineNumber(), typeCode + " 下映射在文件内重复：" + sourceSystem + "/" + sourceCode));
                    continue;
                }
                Map<String, Object> mapping = new LinkedHashMap<>();
                mapping.put("codeTypeCode", typeCode);
                mapping.put("sourceSystem", sourceSystem);
                mapping.put("sourceCode", sourceCode);
                mapping.put("standardCode", standardCode);
                payload.codeMappings.add(mapping);
                toCreate++;
            }
        }
        return fileReport(FILE_CODE_MAPPINGS, csv != null, total, toCreate, 0, errors);
    }

    private Map<String, Object> validateElements(ParsedCsv csv, PackagePayload payload) {
        List<Map<String, Object>> errors = new ArrayList<>();
        int toCreate = 0;
        int toUpdate = 0;
        int total = 0;
        if (csv != null) {
            if (!csv.headerIndex().containsKey("field_name_cn") || !csv.headerIndex().containsKey("field_name_en")) {
                return fileReport(FILE_ELEMENTS, true, 0, 0, 0, List.of(error(1, "缺少必需表头：field_name_cn,field_name_en")));
            }
            Set<String> seen = new HashSet<>();
            for (RawRow row : csv.rows()) {
                total++;
                final RawRow currentRow = row;
                MetadataStandardCsvSupport.ElementRow element = MetadataStandardCsvSupport.buildElementRow(key ->
                    col(csv, currentRow, key)
                );
                if (element.hasError()) {
                    errors.add(error(row.lineNumber(), element.error()));
                    continue;
                }
                MetadataStandardUpsertRequest req = element.request();
                String dupKey = req.getFieldNameEn().toLowerCase(Locale.ROOT) + " " + req.getDomain().toLowerCase(Locale.ROOT);
                if (!seen.add(dupKey)) {
                    errors.add(error(row.lineNumber(), req.getFieldNameEn() + " 在文件内重复（field_name_en+domain）"));
                    continue;
                }
                if (StringUtils.hasText(req.getCodeSet()) && !resolvesDirectory(req.getCodeSet(), payload)) {
                    errors.add(error(row.lineNumber(), req.getFieldNameEn() + " 的 code_set 无法解析：" + req.getCodeSet()));
                    continue;
                }

                Map<String, Object> elementMap = new LinkedHashMap<>();
                elementMap.put("fieldNameCn", req.getFieldNameCn());
                elementMap.put("fieldNameEn", req.getFieldNameEn());
                elementMap.put("dataType", req.getDataType());
                elementMap.put("dataLength", req.getDataLength());
                elementMap.put("dataPrecision", req.getDataPrecision());
                elementMap.put("dataScale", req.getDataScale());
                elementMap.put("nullable", req.getNullable());
                elementMap.put("domain", req.getDomain());
                elementMap.put("description", req.getDescription());
                elementMap.put("sourceSystem", req.getSourceSystem());
                elementMap.put("codeSet", req.getCodeSet());
                elementMap.put("defaultValue", req.getDefaultValue());
                elementMap.put("isPk", req.getIsPk());
                elementMap.put("securityLevel", req.getSecurityLevel() == null ? null : req.getSecurityLevel().name());
                payload.elements.add(elementMap);

                Optional<MetadataStandard> existing = metadataStandardRepository.findByFieldNameEnIgnoreCaseAndDomainIgnoreCase(
                    req.getFieldNameEn(),
                    req.getDomain()
                );
                if (existing.isPresent()) {
                    toUpdate++;
                } else {
                    toCreate++;
                }
            }
        }
        return fileReport(FILE_ELEMENTS, csv != null, total, toCreate, toUpdate, errors);
    }

    // ---------- helpers ----------

    private boolean resolvesDirectory(String codeTypeCode, PackagePayload payload) {
        String normalized = codeTypeCode.toLowerCase(Locale.ROOT);
        if (payload.packageDirectoryCodes.contains(normalized)) {
            return true;
        }
        return payload.directoryLookupCache.computeIfAbsent(
            normalized,
            key -> codeDirectoryRepository.findByCodeTypeCodeIgnoreCase(codeTypeCode).isPresent()
        );
    }

    private Set<String> existingItemValues(String codeTypeCode, PackagePayload payload) {
        String normalized = codeTypeCode.toLowerCase(Locale.ROOT);
        return payload.existingItemValuesCache.computeIfAbsent(normalized, key -> {
            Optional<StdCodeDirectory> directory = codeDirectoryRepository.findByCodeTypeCodeIgnoreCase(codeTypeCode);
            if (directory.isEmpty()) {
                return Set.of();
            }
            Set<String> values = new HashSet<>();
            for (StdCodeValue value : codeValueRepository.findByCodeTypeIdOrderBySortNumAscCodeValueAsc(directory.orElseThrow().getCodeTypeId())) {
                if (value.getCodeValue() != null) {
                    values.add(value.getCodeValue());
                }
            }
            return values;
        });
    }

    private Map<String, Object> fileReport(
        String file,
        boolean present,
        int total,
        int toCreate,
        int toUpdate,
        List<Map<String, Object>> errors
    ) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("file", file);
        report.put("present", present);
        report.put("total", total);
        report.put("toCreate", toCreate);
        report.put("toUpdate", toUpdate);
        report.put("errorCount", errors.size());
        report.put("errors", errors);
        return report;
    }

    private Map<String, Object> error(int row, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("row", row);
        error.put("message", message);
        return error;
    }

    private String summarize(List<Map<String, Object>> fileReports, int totalErrors) {
        int create = 0;
        int update = 0;
        for (Map<String, Object> report : fileReports) {
            create += (Integer) report.get("toCreate");
            update += (Integer) report.get("toUpdate");
        }
        return "新增 " + create + "，更新 " + update + "，错误 " + totalErrors;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("标准包导入序列化失败");
        }
    }

    private String safeMessage(Exception ex) {
        String msg = ex.getMessage();
        return StringUtils.hasText(msg) ? msg : ex.getClass().getSimpleName();
    }

    private record ParsedCsv(String fileName, Map<String, Integer> headerIndex, List<RawRow> rows) {}

    private record RawRow(int lineNumber, List<String> values) {}

    /** 解析后的规范化载荷，序列化进 run.payload_json 供 apply 阶段消费。 */
    static final class PackagePayload {

        final List<Map<String, Object>> terms = new ArrayList<>();
        final List<Map<String, Object>> elements = new ArrayList<>();
        final List<Map<String, Object>> codeDirectories = new ArrayList<>();
        final List<Map<String, Object>> codeItems = new ArrayList<>();
        final List<Map<String, Object>> codeMappings = new ArrayList<>();

        final Set<String> packageDirectoryCodes = new HashSet<>();
        final Map<String, Set<String>> packageItemValues = new HashMap<>();
        final Map<String, Boolean> directoryLookupCache = new HashMap<>();
        final Map<String, Set<String>> existingItemValuesCache = new HashMap<>();

        Map<String, Object> toJsonMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("terms", terms);
            map.put("elements", elements);
            map.put("codeDirectories", codeDirectories);
            map.put("codeItems", codeItems);
            map.put("codeMappings", codeMappings);
            return map;
        }
    }
}
