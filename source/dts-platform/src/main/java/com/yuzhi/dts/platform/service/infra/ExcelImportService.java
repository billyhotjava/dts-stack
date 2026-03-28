package com.yuzhi.dts.platform.service.infra;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.data.DataFormatData;
import com.alibaba.excel.util.DateUtils;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.config.AirflowProperties;
import com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile;
import com.yuzhi.dts.platform.repository.infra.InfraExternalExchangeFileRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.infra.dto.ExcelColumnSpecDto;
import com.yuzhi.dts.platform.service.infra.dto.ExcelImportErrorPreviewResponse;
import com.yuzhi.dts.platform.service.infra.dto.ExcelImportErrorRow;
import com.yuzhi.dts.platform.service.infra.dto.ExcelImportParseRequest;
import com.yuzhi.dts.platform.service.infra.dto.ExcelImportParseResponse;
import com.yuzhi.dts.platform.service.infra.dto.ExcelImportPrepareResponse;
import com.yuzhi.dts.platform.service.infra.dto.ExcelSheetInfo;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExcelImportService {

    private static final Logger LOG = LoggerFactory.getLogger(ExcelImportService.class);
    private static final String ENTRY_KEY = "EXCEL_IMPORT";
    private static final long MAX_FILE_SIZE = 200L * 1024 * 1024;
    private static final int DEFAULT_PREVIEW = 20;
    private static final int DEFAULT_HEADER_ROW = 1;
    private static final int DEFAULT_DATA_START_ROW = 2;
    private static final String DEFAULT_DELIMITER = ",";
    private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
    private static final int DEFAULT_ERROR_PREVIEW_LIMIT = 50;
    private static final Duration RETENTION = Duration.ofDays(7);
    private static final String DEFAULT_BASE_DIR = "/opt/airflow/dags";
    private static final String DEFAULT_CONTAINER_DIR = "/opt/addax/jobs";

    private final AirflowProperties airflowProperties;
    private final InfraExternalExchangeFileRepository repository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ExcelImportService(
        AirflowProperties airflowProperties,
        InfraExternalExchangeFileRepository repository,
        AuditService auditService,
        ObjectMapper objectMapper
    ) {
        this.airflowProperties = airflowProperties;
        this.repository = repository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public ExcelImportPrepareResponse prepare(MultipartFile file, String operator, String ownerDept) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件超过 200MB 限制");
        }
        String originalName = StringUtils.hasText(file.getOriginalFilename())
            ? file.getOriginalFilename()
            : "upload.xlsx";
        String ext = normalizeExt(originalName);
        if (!isSupported(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 xlsx / csv 文件");
        }
        cleanupExpired();

        Path baseDir = resolveBaseDir();
        String batchCode = buildBatchCode();
        Path batchDir = baseDir.resolve("exchange").resolve("excel").resolve(batchCode);
        try {
            Files.createDirectories(batchDir);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "创建目录失败: " + ex.getMessage());
        }
        String storedName = "csv".equals(ext) ? "source.csv" : "source.xlsx";
        Path storedPath = batchDir.resolve(storedName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, storedPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "保存文件失败: " + ex.getMessage());
        }
        String checksum = checksum(storedPath);
        List<ExcelSheetInfo> sheets = "csv".equals(ext) ? List.of() : listSheets(storedPath);

        InfraExternalExchangeFile entity = new InfraExternalExchangeFile();
        entity.setEntryKey(ENTRY_KEY);
        entity.setFileName(originalName);
        entity.setFilePath(storedPath.toString());
        entity.setFileSize(file.getSize());
        entity.setChecksum(checksum);
        entity.setBatchCode(batchCode);
        entity.setOwnerDept(StringUtils.hasText(ownerDept) ? ownerDept.trim() : null);
        entity.setStatus("RECEIVED");
        entity.setReceivedAt(Instant.now());
        entity.setProcessedAt(null);
        entity.setEnabled(Boolean.TRUE);
        entity.setClassification("INTERNAL");
        entity.setProps(writeProps(Map.of("format", ext, "sheets", sheets)));
        InfraExternalExchangeFile saved = repository.save(entity);
        auditService.auditAction(
            "INFRA_EXCEL_IMPORT",
            AuditStage.SUCCESS,
            saved.getId().toString(),
            Map.of("summary", "上传 Excel/CSV", "file", originalName, "batch", batchCode, "operator", operator)
        );
        return new ExcelImportPrepareResponse(saved.getId(), originalName, batchCode, sheets);
    }

    public ExcelImportParseResponse parse(ExcelImportParseRequest request, String operator, String ownerDept, boolean privileged) {
        if (request == null || request.fileId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fileId 不能为空");
        }
        InfraExternalExchangeFile entity = loadFile(request.fileId(), ownerDept, privileged);
        String filePath = entity.getFilePath();
        if (!StringUtils.hasText(filePath)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件路径为空");
        }
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件不存在: " + filePath);
        }
        String ext = normalizeExt(path.getFileName().toString());
        if (!isSupported(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 xlsx / csv 文件");
        }

        int headerRow = normalizeInt(request.headerRow(), DEFAULT_HEADER_ROW);
        int dataStartRow = normalizeInt(request.dataStartRow(), DEFAULT_DATA_START_ROW);
        int previewLimit = normalizeInt(request.previewLimit(), DEFAULT_PREVIEW);
        String delimiter = StringUtils.hasText(request.delimiter()) ? request.delimiter().trim() : DEFAULT_DELIMITER;
        boolean skipErrors = request.skipErrors() == null || request.skipErrors();
        boolean fillMerged = request.fillMerged() == null || request.fillMerged();
        String dateFormat = StringUtils.hasText(request.dateFormat()) ? request.dateFormat().trim() : DEFAULT_DATE_FORMAT;

        ParseContext ctx = new ParseContext(headerRow, dataStartRow, previewLimit, delimiter, skipErrors, fillMerged, dateFormat);
        Path csvPath = path.getParent().resolve("data.csv");
        Path errorPath = path.getParent().resolve("error.csv");
        if ("csv".equals(ext)) {
            ctx.sheetName = "csv";
            parseCsv(path, csvPath, errorPath, ctx);
        } else {
            parseExcel(path, csvPath, errorPath, ctx, request.sheetName(), request.sheetIndex());
        }

        String csvContainerPath = toContainerPath(csvPath);
        List<ExcelColumnSpecDto> columns = new ArrayList<>();
        for (int i = 0; i < ctx.columns.size(); i++) {
            String safeName = ctx.columns.get(i);
            String label = i < ctx.rawColumns.size() ? ctx.rawColumns.get(i) : "";
            label = StringUtils.hasText(label) ? label.trim() : "";
            if (!StringUtils.hasText(label)) {
                label = safeName;
            }
            columns.add(new ExcelColumnSpecDto(safeName, "string", label));
        }

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("format", ext);
        props.put("sheetName", ctx.sheetName);
        props.put("csvPath", csvPath.toString());
        props.put("csvContainerPath", csvContainerPath);
        props.put("errorPath", errorPath.toString());
        props.put("errorContainerPath", toContainerPath(errorPath));
        props.put("delimiter", ctx.delimiter);
        props.put("columns", columns);
        props.put("rowCount", ctx.rowCount);
        props.put("errorCount", ctx.errorCount);
        props.put("headerRow", ctx.headerRow);
        props.put("dataStartRow", ctx.dataStartRow);
        entity.setProps(writeProps(props));
        entity.setStatus("PARSED");
        entity.setProcessedAt(Instant.now());
        repository.save(entity);

        auditService.auditAction(
            "INFRA_EXCEL_IMPORT",
            ctx.errorCount > 0 ? AuditStage.FAIL : AuditStage.SUCCESS,
            entity.getId().toString(),
            Map.of(
                "summary",
                "解析 Excel/CSV",
                "batch",
                entity.getBatchCode(),
                "rows",
                ctx.rowCount,
                "errors",
                ctx.errorCount,
                "operator",
                operator
            )
        );
        return new ExcelImportParseResponse(
            entity.getId(),
            entity.getBatchCode(),
            ctx.sheetName,
            csvPath.toString(),
            csvContainerPath,
            errorPath.toString(),
            toContainerPath(errorPath),
            ctx.delimiter,
            columns,
            ctx.preview,
            ctx.rowCount,
            ctx.errorCount
        );
    }

    public ExcelImportErrorPreviewResponse errorPreview(UUID fileId, Integer limit, String operator, String ownerDept, boolean privileged) {
        if (fileId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fileId 不能为空");
        }
        InfraExternalExchangeFile entity = loadFile(fileId, ownerDept, privileged);
        Map<String, Object> props = readProps(entity.getProps());
        String errorPath = text(props.get("errorPath"));
        String delimiter = text(props.get("delimiter"));
        int resolvedLimit = normalizeInt(limit, DEFAULT_ERROR_PREVIEW_LIMIT);
        List<ExcelImportErrorRow> rows = new ArrayList<>();
        if (StringUtils.hasText(errorPath)) {
            Path path = Path.of(errorPath);
            if (Files.exists(path)) {
                try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    String line;
                    int count = 0;
                    while ((line = reader.readLine()) != null && count < resolvedLimit) {
                        List<String> values = parseCsvLine(line, delimiter);
                        if (values.isEmpty()) {
                            continue;
                        }
                        Integer rowIndex = parseRowIndex(values.size() > 0 ? values.get(0) : null);
                        String message = values.size() > 1 ? values.get(1) : "";
                        rows.add(new ExcelImportErrorRow(rowIndex, message));
                        count++;
                    }
                } catch (Exception ex) {
                    LOG.warn("[excel-import] read error preview failed: {}", ex.getMessage());
                }
            }
        }
        Integer errorCount = parseRowIndex(text(props.get("errorCount")));
        if (errorCount == null) {
            errorCount = rows.size();
        }
        auditService.auditAction(
            "INFRA_EXCEL_IMPORT",
            AuditStage.SUCCESS,
            entity.getId().toString(),
            Map.of(
                "summary",
                "查看错误行预览",
                "batch",
                entity.getBatchCode(),
                "errors",
                errorCount,
                "operator",
                operator
            )
        );
        return new ExcelImportErrorPreviewResponse(entity.getId(), errorCount, resolvedLimit, rows);
    }

    private void parseCsv(Path source, Path csvPath, Path errorPath, ParseContext ctx) {
        List<String> lastValues = new ArrayList<>();
        try (
            BufferedReader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8);
            BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8);
            BufferedWriter errorWriter = Files.newBufferedWriter(errorPath, StandardCharsets.UTF_8)
        ) {
            String line;
            int rowIndex = 0;
            while ((line = reader.readLine()) != null) {
                rowIndex++;
                List<String> values = parseCsvLine(line, ctx.delimiter);
                if (rowIndex == ctx.headerRow) {
                    ctx.rawColumns = new ArrayList<>(values);
                    ctx.columns = normalizeHeaders(values);
                    lastValues = new ArrayList<>(ctx.columns.size());
                    for (int i = 0; i < ctx.columns.size(); i++) {
                        lastValues.add("");
                    }
                    writeCsvLine(writer, ctx.columns, ctx.delimiter);
                    continue;
                }
                if (rowIndex < ctx.dataStartRow) {
                    continue;
                }
                if (isRawBlankRow(values)) {
                    continue;
                }
                List<String> row = normalizeRow(values, ctx.columns.size(), lastValues, ctx.fillMerged);
                ctx.rowCount++;
                if (ctx.preview.size() < ctx.previewLimit) {
                    ctx.preview.add(row);
                }
                writeCsvLine(writer, row, ctx.delimiter);
            }
        } catch (Exception ex) {
            if (!ctx.skipErrors) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "解析 CSV 失败: " + ex.getMessage());
            }
            ctx.errorCount++;
            LOG.warn("[excel-import] csv parse failed: {}", ex.getMessage());
        }
    }

    private void parseExcel(Path source, Path csvPath, Path errorPath, ParseContext ctx, String sheetName, Integer sheetIndex) {
        ctx.sheetName = StringUtils.hasText(sheetName) ? sheetName.trim() : null;
        if (!StringUtils.hasText(ctx.sheetName) && sheetIndex != null) {
            ctx.sheetName = "sheet-" + sheetIndex;
        }
        if (!StringUtils.hasText(ctx.sheetName)) {
            ctx.sheetName = "sheet-0";
        }
        Path parseSource = source;
        try (
            BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8);
            BufferedWriter errorWriter = Files.newBufferedWriter(errorPath, StandardCharsets.UTF_8)
        ) {
            parseSource = createFormulaEvaluatedCopy(source);
            ExcelRowListener listener = new ExcelRowListener(ctx, writer, errorWriter);
            var readerBuilder = EasyExcel.read(parseSource.toFile(), listener).headRowNumber(0);
            if (StringUtils.hasText(ctx.sheetName)) {
                readerBuilder.sheet(ctx.sheetName).doRead();
            } else if (sheetIndex != null) {
                readerBuilder.sheet(sheetIndex).doRead();
            } else {
                readerBuilder.sheet(0).doRead();
            }
        } catch (Exception ex) {
            if (!ctx.skipErrors) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "解析 Excel 失败: " + ex.getMessage());
            }
            ctx.errorCount++;
            LOG.warn("[excel-import] excel parse failed: {}", ex.getMessage());
        } finally {
            if (parseSource != null && !parseSource.equals(source)) {
                deleteFile(parseSource.toString());
            }
        }
    }

    private Path createFormulaEvaluatedCopy(Path source) {
        try (InputStream in = Files.newInputStream(source); Workbook workbook = WorkbookFactory.create(in)) {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            boolean hasFormula = false;
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                var sheet = workbook.getSheetAt(sheetIndex);
                if (sheet == null) {
                    continue;
                }
                for (var row : sheet) {
                    if (row == null) {
                        continue;
                    }
                    for (Cell cell : row) {
                        if (cell == null || cell.getCellType() != CellType.FORMULA) {
                            continue;
                        }
                        hasFormula = true;
                        replaceFormulaCellWithResolvedValue(cell, evaluator, formatter);
                    }
                }
            }
            if (!hasFormula) {
                return source;
            }
            Path copy = Files.createTempFile(source.getParent(), "formula-evaluated-", ".xlsx");
            try (var out = Files.newOutputStream(copy)) {
                workbook.write(out);
            }
            return copy;
        } catch (Exception ex) {
            LOG.warn("[excel-import] formula evaluation pre-processing failed: {}", ex.getMessage());
            return source;
        }
    }

    private void replaceFormulaCellWithResolvedValue(Cell cell, FormulaEvaluator evaluator, DataFormatter formatter) {
        try {
            String formatted = formatter.formatCellValue(cell, evaluator);
            CellType evaluatedType = evaluator.evaluateFormulaCell(cell);
            cell.setBlank();
            if (evaluatedType == CellType.ERROR || !StringUtils.hasText(formatted)) {
                return;
            }
            cell.setCellValue(formatted.trim());
        } catch (Exception ex) {
            cell.setBlank();
        }
    }

    private List<ExcelSheetInfo> listSheets(Path file) {
        List<ExcelSheetInfo> sheets = new ArrayList<>();
        try (InputStream in = Files.newInputStream(file); Workbook workbook = WorkbookFactory.create(in)) {
            int count = workbook.getNumberOfSheets();
            for (int i = 0; i < count; i++) {
                sheets.add(new ExcelSheetInfo(i, workbook.getSheetName(i)));
            }
        } catch (Exception ex) {
            LOG.warn("[excel-import] sheet listing failed: {}", ex.getMessage());
        }
        return sheets;
    }

    private void cleanupExpired() {
        Instant cutoff = Instant.now().minus(RETENTION);
        List<InfraExternalExchangeFile> expired = repository.findByEntryKeyIgnoreCaseAndReceivedAtBefore(ENTRY_KEY, cutoff);
        if (expired == null || expired.isEmpty()) {
            return;
        }
        for (InfraExternalExchangeFile file : expired) {
            deleteFile(file.getFilePath());
            Map<String, Object> props = readProps(file.getProps());
            deleteFile(text(props.get("csvPath")));
            deleteFile(text(props.get("errorPath")));
            file.setEnabled(Boolean.FALSE);
            file.setStatus("EXPIRED");
            file.setProcessedAt(Instant.now());
            repository.save(file);
        }
    }

    private void deleteFile(String path) {
        if (!StringUtils.hasText(path)) return;
        try {
            Files.deleteIfExists(Path.of(path));
        } catch (Exception ex) {
            LOG.debug("[excel-import] failed to delete {}: {}", path, ex.getMessage());
        }
    }

    private Path resolveBaseDir() {
        String configured = airflowProperties.getDagsDir();
        String base = StringUtils.hasText(configured) ? configured.trim() : DEFAULT_BASE_DIR;
        Path path = Path.of(base);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException ex) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "DAG 目录不可用: " + ex.getMessage());
            }
        }
        return path;
    }

    private String buildBatchCode() {
        String ts = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        return "excel-" + ts + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String normalizeExt(String name) {
        if (!StringUtils.hasText(name)) {
            return "";
        }
        int idx = name.lastIndexOf('.');
        String ext = idx >= 0 ? name.substring(idx + 1) : name;
        return ext.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isSupported(String ext) {
        return "xlsx".equals(ext) || "csv".equals(ext);
    }

    private String checksum(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                digest.update(buffer, 0, len);
            }
            return Base64.getEncoder().encodeToString(digest.digest());
        } catch (Exception ex) {
            return null;
        }
    }

    private String writeProps(Map<String, Object> props) {
        if (props == null || props.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(props);
        } catch (Exception ex) {
            return null;
        }
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

    private String text(Object value) {
        if (value == null) return null;
        String text = value.toString();
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private int normalizeInt(Integer value, int fallback) {
        if (value == null || value <= 0) {
            return fallback;
        }
        return value;
    }

    private Integer parseRowIndex(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private String toContainerPath(Path csvPath) {
        Path baseDir = resolveBaseDir();
        Path normalized = csvPath.toAbsolutePath().normalize();
        Path base = baseDir.toAbsolutePath().normalize();
        if (normalized.startsWith(base)) {
            Path rel = base.relativize(normalized);
            String relPath = rel.toString().replace('\\', '/');
            return DEFAULT_CONTAINER_DIR + "/" + relPath;
        }
        return normalized.toString();
    }

    private String normalizeColumnName(String raw, int index, Set<String> used) {
        return SqlFieldNameResolver.resolve(raw, index, used);
    }

    private List<String> normalizeHeaders(List<String> values) {
        List<String> headers = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < values.size(); i++) {
            String raw = values.get(i);
            String candidate = normalizeColumnName(raw, i, seen);
            headers.add(candidate);
        }
        return headers;
    }

    private List<String> normalizeRow(List<String> values, int size, List<String> lastValues, boolean fillMerged) {
        List<String> row = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String raw = i < values.size() ? values.get(i) : "";
            String value = raw == null ? "" : raw;
            if (fillMerged && !StringUtils.hasText(value)) {
                if (i < lastValues.size() && StringUtils.hasText(lastValues.get(i))) {
                    value = lastValues.get(i);
                }
            }
            row.add(value);
            if (i < lastValues.size() && StringUtils.hasText(value)) {
                lastValues.set(i, value);
            }
        }
        return row;
    }

    private void writeCsvLine(BufferedWriter writer, List<String> values, String delimiter) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(delimiter);
            sb.append(escapeCsv(values.get(i), delimiter));
        }
        writer.write(sb.toString());
        writer.newLine();
    }

    private String escapeCsv(String value, String delimiter) {
        if (value == null) return "";
        boolean needQuote = value.contains(delimiter) || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needQuote ? "\"" + escaped + "\"" : escaped;
    }

    private List<String> parseCsvLine(String line, String delimiter) {
        List<String> out = new ArrayList<>();
        if (line == null) return out;
        char sep = delimiter != null && delimiter.length() == 1 ? delimiter.charAt(0) : ',';
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
                continue;
            }
            if (c == sep && !quoted) {
                out.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        out.add(current.toString());
        return out;
    }

    private InfraExternalExchangeFile loadFile(UUID fileId, String ownerDept, boolean privileged) {
        InfraExternalExchangeFile entity = repository
            .findById(fileId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在"));
        if (!privileged) {
            String targetDept = entity.getOwnerDept();
            if (StringUtils.hasText(targetDept)) {
                if (!StringUtils.hasText(ownerDept) || !targetDept.equalsIgnoreCase(ownerDept.trim())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限访问该文件");
                }
            }
        }
        if (!Boolean.TRUE.equals(entity.getEnabled())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件已失效");
        }
        return entity;
    }

    private static final class ParseContext {
        private final int headerRow;
        private final int dataStartRow;
        private final int previewLimit;
        private final String delimiter;
        private final boolean skipErrors;
        private final boolean fillMerged;
        private final String dateFormat;
        private final List<List<String>> preview = new ArrayList<>();
        private List<String> columns = new ArrayList<>();
        private List<String> rawColumns = new ArrayList<>();
        private int rowCount = 0;
        private int errorCount = 0;
        private String sheetName;

        private ParseContext(int headerRow, int dataStartRow, int previewLimit, String delimiter, boolean skipErrors, boolean fillMerged, String dateFormat) {
            this.headerRow = headerRow;
            this.dataStartRow = dataStartRow;
            this.previewLimit = previewLimit;
            this.delimiter = delimiter;
            this.skipErrors = skipErrors;
            this.fillMerged = fillMerged;
            this.dateFormat = dateFormat;
        }
    }

    private final class ExcelRowListener extends AnalysisEventListener<Map<Integer, Object>> {
        private final ParseContext ctx;
        private final BufferedWriter writer;
        private final BufferedWriter errorWriter;
        private List<String> lastValues = new ArrayList<>();

        private ExcelRowListener(ParseContext ctx, BufferedWriter writer, BufferedWriter errorWriter) {
            this.ctx = ctx;
            this.writer = writer;
            this.errorWriter = errorWriter;
        }

        @Override
        public void invoke(Map<Integer, Object> data, AnalysisContext context) {
            int rowIndex = context.readRowHolder().getRowIndex() + 1;
            try {
                List<String> values = readValues(data);
                if (rowIndex == ctx.headerRow) {
                    ctx.rawColumns = new ArrayList<>(values);
                    ctx.columns = normalizeHeaders(values);
                    lastValues = new ArrayList<>(ctx.columns.size());
                    for (int i = 0; i < ctx.columns.size(); i++) {
                        lastValues.add("");
                    }
                    writeCsvLine(writer, ctx.columns, ctx.delimiter);
                    return;
                }
                if (rowIndex < ctx.dataStartRow) {
                    return;
                }
                if (isRawBlankRow(values)) {
                    return;
                }
                List<String> row = normalizeRow(values, ctx.columns.size(), lastValues, ctx.fillMerged);
                ctx.rowCount++;
                if (ctx.preview.size() < ctx.previewLimit) {
                    ctx.preview.add(row);
                }
                writeCsvLine(writer, row, ctx.delimiter);
            } catch (Exception ex) {
                ctx.errorCount++;
                if (ctx.skipErrors) {
                    try {
                        writeCsvLine(errorWriter, List.of(String.valueOf(rowIndex), ex.getMessage()), ctx.delimiter);
                    } catch (IOException ignored) {}
                } else {
                    throw new IllegalStateException("解析失败: " + ex.getMessage(), ex);
                }
            }
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {}

        private List<String> readValues(Map<Integer, Object> data) {
            List<String> values = new ArrayList<>();
            if (data == null || data.isEmpty()) {
                return values;
            }
            int maxIndex = data.keySet().stream().filter(Objects::nonNull).mapToInt(Integer::intValue).max().orElse(-1);
            for (int i = 0; i <= maxIndex; i++) {
                Object raw = data.get(i);
                if (raw instanceof ReadCellData<?> cell) {
                    values.add(cellToString(cell, ctx.dateFormat));
                } else if (raw != null) {
                    values.add(raw.toString().trim());
                } else {
                    values.add("");
                }
            }
            return values;
        }
    }

    private boolean isRawBlankRow(List<String> values) {
        if (values == null || values.isEmpty()) {
            return true;
        }
        return values.stream().noneMatch(StringUtils::hasText);
    }

    private String cellToString(ReadCellData<?> cell, String dateFormat) {
        if (cell == null) {
            return "";
        }
        try {
            switch (cell.getType()) {
                case STRING:
                case DIRECT_STRING:
                case RICH_TEXT_STRING:
                    return safe(cell.getStringValue());
                case BOOLEAN:
                    return cell.getBooleanValue() == null ? "" : cell.getBooleanValue().toString();
                case NUMBER:
                    if (cell.getNumberValue() == null) return "";
                    return normalizeNumber(cell.getNumberValue().toPlainString());
                case DATE:
                    return formatDate(cell, dateFormat);
                case ERROR:
                    return "";
                case EMPTY:
                    return "";
                default:
                    return safe(cell.getStringValue());
            }
        } catch (Exception ex) {
            return safe(cell.getStringValue());
        }
    }

    private String formatDate(ReadCellData<?> cell, String dateFormat) {
        Object data = cell.getData();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateFormat);
        if (data instanceof LocalDateTime dateTime) {
            return formatter.format(dateTime);
        }
        if (data instanceof LocalDate date) {
            return formatter.format(date.atStartOfDay());
        }
        if (data instanceof Date date) {
            return formatter.format(date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
        }
        if (cell.getNumberValue() != null) {
            LocalDateTime dateTime = DateUtils.getLocalDateTime(cell.getNumberValue().doubleValue(), false);
            return formatter.format(dateTime);
        }
        if (StringUtils.hasText(cell.getStringValue())) {
            return cell.getStringValue().trim();
        }
        return safeFormat(cell.getDataFormatData());
    }

    private String safeFormat(DataFormatData data) {
        if (data == null) {
            return "";
        }
        return safe(data.getFormat());
    }

    private String safe(String value) {
        if (!StringUtils.hasText(value)) return "";
        return value.trim();
    }

    private String normalizeNumber(String value) {
        if (!StringUtils.hasText(value)) return "";
        if (value.contains(".")) {
            String trimmed = value.replaceAll("0+$", "").replaceAll("\\.$", "");
            return trimmed;
        }
        return value;
    }
}
