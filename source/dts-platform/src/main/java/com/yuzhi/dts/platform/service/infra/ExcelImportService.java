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
import com.yuzhi.dts.platform.domain.infra.InfraProjectCockpitBatch;
import com.yuzhi.dts.platform.domain.infra.InfraProjectCockpitIssue;
import com.yuzhi.dts.platform.domain.infra.InfraProjectCockpitRow;
import com.yuzhi.dts.platform.repository.infra.InfraExternalExchangeFileRepository;
import com.yuzhi.dts.platform.repository.infra.InfraProjectCockpitBatchRepository;
import com.yuzhi.dts.platform.repository.infra.InfraProjectCockpitIssueRepository;
import com.yuzhi.dts.platform.repository.infra.InfraProjectCockpitRowRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.infra.dto.ExcelColumnSpecDto;
import com.yuzhi.dts.platform.service.infra.dto.ExcelImportErrorPreviewResponse;
import com.yuzhi.dts.platform.service.infra.dto.ExcelImportErrorRow;
import com.yuzhi.dts.platform.service.infra.dto.ExcelImportParseRequest;
import com.yuzhi.dts.platform.service.infra.dto.ExcelImportParseResponse;
import com.yuzhi.dts.platform.service.infra.dto.ExcelImportPrepareResponse;
import com.yuzhi.dts.platform.service.infra.dto.ExcelSheetInfo;
import com.yuzhi.dts.platform.service.infra.dto.ProjectCockpitBatchIssuePreviewResponse;
import com.yuzhi.dts.platform.service.infra.dto.ProjectCockpitBatchIssueRow;
import com.yuzhi.dts.platform.service.infra.dto.ProjectCockpitBatchLoadResponse;
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
    private static final Set<String> PLACEHOLDER_VALUES =
        Set.of("", "/", "-", "--", "N/A", "NA", "#N/A", "#VALUE!", "#DIV/0!", "NULL");
    private static final Duration RETENTION = Duration.ofDays(7);
    private static final String DEFAULT_BASE_DIR = "/opt/airflow/dags";
    private static final String DEFAULT_CONTAINER_DIR = "/opt/addax/jobs";

    private final AirflowProperties airflowProperties;
    private final InfraExternalExchangeFileRepository repository;
    private final InfraProjectCockpitBatchRepository projectCockpitBatchRepository;
    private final InfraProjectCockpitRowRepository projectCockpitRowRepository;
    private final InfraProjectCockpitIssueRepository projectCockpitIssueRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ExcelImportService(
        AirflowProperties airflowProperties,
        InfraExternalExchangeFileRepository repository,
        InfraProjectCockpitBatchRepository projectCockpitBatchRepository,
        InfraProjectCockpitRowRepository projectCockpitRowRepository,
        InfraProjectCockpitIssueRepository projectCockpitIssueRepository,
        AuditService auditService,
        ObjectMapper objectMapper
    ) {
        this.airflowProperties = airflowProperties;
        this.repository = repository;
        this.projectCockpitBatchRepository = projectCockpitBatchRepository;
        this.projectCockpitRowRepository = projectCockpitRowRepository;
        this.projectCockpitIssueRepository = projectCockpitIssueRepository;
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
            ctx.errorCount > 0 ? AuditStage.SUCCESS : AuditStage.SUCCESS,
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

    public ProjectCockpitBatchLoadResponse loadProjectCockpitBatch(UUID fileId, String operator, String ownerDept, boolean privileged) {
        if (fileId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fileId 不能为空");
        }
        InfraExternalExchangeFile entity = loadFile(fileId, ownerDept, privileged);
        Map<String, Object> props = readProps(entity.getProps());
        String csvPath = text(props.get("csvPath"));
        if (!StringUtils.hasText(csvPath)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先完成 Excel/CSV 解析");
        }
        Path csv = Path.of(csvPath);
        if (!Files.exists(csv)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "解析后的 CSV 不存在");
        }

        InfraProjectCockpitBatch batch = projectCockpitBatchRepository
                .findByExternalExchangeFileId(entity.getId())
                .orElseGet(InfraProjectCockpitBatch::new);
        boolean existingBatch = batch.getId() != null;
        if (!existingBatch) {
            batch.setExternalExchangeFileId(entity.getId());
        }
        batch.setBatchCode(entity.getBatchCode());
        batch.setSourceFileName(entity.getFileName());
        batch.setSheetName(text(props.get("sheetName")));
        batch.setDelimiter(StringUtils.hasText(text(props.get("delimiter"))) ? text(props.get("delimiter")) : DEFAULT_DELIMITER);
        batch.setOwnerDept(entity.getOwnerDept());
        batch.setUploadedBy(operator);
        batch.setStatus("LOADED");
        batch.setCsvPath(csvPath);
        batch.setErrorPath(text(props.get("errorPath")));
        batch.setRowCount(parseRowIndex(text(props.get("rowCount"))));
        batch.setIssueCount(parseRowIndex(text(props.get("errorCount"))));
        batch.setLoadedAt(Instant.now());
        batch.setProps(writeProps(Map.of(
                "sourceFileId", entity.getId().toString(),
                "delimiter", batch.getDelimiter(),
                "sheetName", blankToEmpty(batch.getSheetName())
        )));
        batch = projectCockpitBatchRepository.saveAndFlush(batch);

        if (existingBatch) {
            projectCockpitRowRepository.deleteByBatchId(batch.getId());
            projectCockpitIssueRepository.deleteByBatchId(batch.getId());
        }

        ProjectCockpitLoadStats loadStats = persistProjectCockpitRows(batch, csv);
        int parseIssueCount = persistProjectCockpitIssues(batch, batch.getErrorPath(), batch.getDelimiter());
        projectCockpitRowRepository.flush();
        projectCockpitIssueRepository.flush();
        batch.setRowCount(loadStats.loadedRowCount());
        batch.setIssueCount(loadStats.issueCount() + parseIssueCount);
        batch.setIssueRowCount(loadStats.rejectedRowCount() + loadStats.warningRowCount() + parseIssueCount);
        batch.setStatus(resolveBatchStatus(loadStats, parseIssueCount));
        batch.setLoadedAt(Instant.now());
        Map<String, Object> batchProps = new LinkedHashMap<>(readProps(batch.getProps()));
        batchProps.put("sourceFileId", entity.getId().toString());
        batchProps.put("delimiter", batch.getDelimiter());
        batchProps.put("sheetName", blankToEmpty(batch.getSheetName()));
        batchProps.put("acceptedCsvPath", loadStats.acceptedCsvPath().toString());
        batchProps.put("acceptedCsvContainerPath", toContainerPath(loadStats.acceptedCsvPath()));
        batchProps.put("rejectedCsvPath", loadStats.rejectedCsvPath().toString());
        batchProps.put("rejectedCsvContainerPath", toContainerPath(loadStats.rejectedCsvPath()));
        batchProps.put("acceptedRowCount", loadStats.acceptedRowCount());
        batchProps.put("rejectedRowCount", loadStats.rejectedRowCount());
        batchProps.put("warningRowCount", loadStats.warningRowCount());
        batchProps.put("validationIssueCount", loadStats.issueCount());
        batchProps.put("parseIssueCount", parseIssueCount);
        batch.setProps(writeProps(batchProps));
        projectCockpitBatchRepository.saveAndFlush(batch);

        entity.setStatus("PROJECT_DOMAIN_LOADED");
        entity.setProcessedAt(Instant.now());
        repository.saveAndFlush(entity);

        auditService.auditAction(
            "INFRA_EXCEL_IMPORT",
            AuditStage.SUCCESS,
            entity.getId().toString(),
            Map.of(
                "summary",
                "项目主体域批次落库",
                "batch",
                entity.getBatchCode(),
                "rows",
                loadStats.loadedRowCount(),
                "acceptedRows",
                loadStats.acceptedRowCount(),
                "rejectedRows",
                loadStats.rejectedRowCount(),
                "issues",
                loadStats.issueCount() + parseIssueCount,
                "operator",
                operator
            )
        );
        return new ProjectCockpitBatchLoadResponse(
            batch.getId(),
            batch.getBatchCode(),
            loadStats.loadedRowCount(),
            loadStats.acceptedRowCount(),
            loadStats.rejectedRowCount(),
            loadStats.warningRowCount(),
            loadStats.issueCount() + parseIssueCount,
            batch.getStatus()
        );
    }

    public ProjectCockpitBatchIssuePreviewResponse projectCockpitIssuePreview(
        UUID batchId,
        String severity,
        Integer limit,
        String operator,
        String ownerDept,
        boolean privileged
    ) {
        if (batchId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "batchId 不能为空");
        }
        InfraProjectCockpitBatch batch = loadBatch(batchId, ownerDept, privileged);
        int resolvedLimit = normalizeInt(limit, DEFAULT_ERROR_PREVIEW_LIMIT);
        String resolvedSeverity = StringUtils.hasText(severity) ? severity.trim().toUpperCase(Locale.ROOT) : null;
        int issueRowCount = 0;
        List<ProjectCockpitBatchIssueRow> rows = new ArrayList<>();
        for (InfraProjectCockpitIssue issue : projectCockpitIssueRepository.findByBatchIdOrderByRowNoAscIdAsc(batchId)) {
            if (StringUtils.hasText(resolvedSeverity) && !resolvedSeverity.equalsIgnoreCase(issue.getSeverity())) {
                continue;
            }
            issueRowCount++;
            if (rows.size() >= resolvedLimit) {
                continue;
            }
            Map<String, Object> payload = readProps(issue.getRawPayload());
            rows.add(
                new ProjectCockpitBatchIssueRow(
                    issue.getRowNo(),
                    issue.getSeverity(),
                    issue.getIssueCode(),
                    issue.getIssueMessage(),
                    blankToEmpty(text(payload.get("projectNo"))),
                    blankToEmpty(text(payload.get("subsystem"))),
                    blankToEmpty(text(payload.get("nodeTask"))),
                    blankToEmpty(text(payload.get("planDate"))),
                    blankToEmpty(text(payload.get("completionStatus"))),
                    blankToEmpty(text(payload.get("riskLevel")))
                )
            );
        }
        auditService.auditAction(
            "INFRA_EXCEL_IMPORT",
            AuditStage.SUCCESS,
            batch.getId().toString(),
            Map.of(
                "summary",
                "查看项目主体域问题预览",
                "batch",
                batch.getBatchCode(),
                "severity",
                blankToEmpty(resolvedSeverity),
                "issues",
                issueRowCount,
                "operator",
                operator
            )
        );
        return new ProjectCockpitBatchIssuePreviewResponse(batch.getId(), issueRowCount, resolvedLimit, rows);
    }

    private ProjectCockpitLoadStats persistProjectCockpitRows(InfraProjectCockpitBatch batch, Path csvPath) {
        Path acceptedCsvPath = csvPath.getParent().resolve("accepted.csv");
        Path rejectedCsvPath = csvPath.getParent().resolve("rejected.csv");
        try (
            BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8);
            BufferedWriter acceptedWriter = Files.newBufferedWriter(acceptedCsvPath, StandardCharsets.UTF_8);
            BufferedWriter rejectedWriter = Files.newBufferedWriter(rejectedCsvPath, StandardCharsets.UTF_8)
        ) {
            String headerLine = reader.readLine();
            if (!StringUtils.hasText(headerLine)) {
                return new ProjectCockpitLoadStats(0, 0, 0, 0, 0, acceptedCsvPath, rejectedCsvPath);
            }
            List<String> headers = parseCsvLine(headerLine, batch.getDelimiter());
            writeCsvLine(acceptedWriter, headers, batch.getDelimiter());
            List<String> rejectedHeaders = new ArrayList<>(headers);
            rejectedHeaders.add("__issue_code");
            rejectedHeaders.add("__issue_message");
            writeCsvLine(rejectedWriter, rejectedHeaders, batch.getDelimiter());
            String line;
            int sourceRowNo = 0;
            int loadedRowCount = 0;
            int acceptedRowCount = 0;
            int rejectedRowCount = 0;
            int warningRowCount = 0;
            int issueCount = 0;
            while ((line = reader.readLine()) != null) {
                sourceRowNo++;
                List<String> values = normalizeRow(
                        parseCsvLine(line, batch.getDelimiter()),
                        headers.size(),
                        new ArrayList<>(headers.size()),
                        false
                );
                Map<String, String> payload = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    payload.put(headers.get(i), i < values.size() ? values.get(i) : "");
                }
                if (!isMeaningfulProjectCockpitRow(payload)) {
                    continue;
                }
                loadedRowCount++;
                ProjectCockpitRowAssessment assessment = assessProjectCockpitRow(payload);
                Map<String, Object> payloadJson = new LinkedHashMap<>(payload);
                InfraProjectCockpitRow row = new InfraProjectCockpitRow();
                row.setBatchId(batch.getId());
                row.setRowNo(sourceRowNo);
                row.setProjectNo(payload.get("project_no"));
                row.setSubsystem(payload.get("subsystem"));
                row.setNodeTask(payload.get("node_task"));
                row.setDept(payload.get("dept"));
                row.setOwner(payload.get("owner"));
                row.setProjectManager(payload.get("project_manager"));
                row.setPlanDateRaw(payload.get("plan_date"));
                row.setActualDateRaw(payload.get("actual_date"));
                row.setCompletionStatusRaw(payload.get("completion_status"));
                row.setRiskLevelRaw(payload.get("risk_level"));
                row.setParseStatus(assessment.parseStatus());
                row.setRawPayload(writeProps(payloadJson));
                row.setProps(writeProps(Map.of("sheetName", blankToEmpty(batch.getSheetName()))));
                projectCockpitRowRepository.save(row);
                for (ProjectCockpitIssueDescriptor descriptor : assessment.issues()) {
                    projectCockpitIssueRepository.save(buildProjectCockpitIssue(batch, sourceRowNo, descriptor, payload));
                    issueCount++;
                }
                if (assessment.hasWarnings()) {
                    warningRowCount++;
                }
                if (assessment.blocked()) {
                    rejectedRowCount++;
                    List<String> rejectedRow = new ArrayList<>(values);
                    rejectedRow.add(assessment.joinIssueCodes());
                    rejectedRow.add(assessment.joinIssueMessages());
                    writeCsvLine(rejectedWriter, rejectedRow, batch.getDelimiter());
                } else {
                    acceptedRowCount++;
                    writeCsvLine(acceptedWriter, values, batch.getDelimiter());
                }
            }
            return new ProjectCockpitLoadStats(
                loadedRowCount,
                acceptedRowCount,
                rejectedRowCount,
                warningRowCount,
                issueCount,
                acceptedCsvPath,
                rejectedCsvPath
            );
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "项目主体域 CSV 读取失败: " + ex.getMessage());
        }
    }

    private int persistProjectCockpitIssues(InfraProjectCockpitBatch batch, String errorPath, String delimiter) {
        if (!StringUtils.hasText(errorPath)) {
            return 0;
        }
        Path path = Path.of(errorPath);
        if (!Files.exists(path)) {
            return 0;
        }
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                List<String> values = parseCsvLine(line, delimiter);
                InfraProjectCockpitIssue issue = new InfraProjectCockpitIssue();
                issue.setBatchId(batch.getId());
                issue.setRowNo(parseRowIndex(values.isEmpty() ? null : values.get(0)));
                issue.setSeverity("WARN");
                issue.setIssueCode("PARSE_WARNING");
                issue.setIssueMessage(values.size() > 1 ? values.get(1) : "解析异常");
                issue.setRawPayload(writeProps(Map.of("rawLine", line)));
                projectCockpitIssueRepository.save(issue);
                count++;
            }
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "项目主体域错误文件读取失败: " + ex.getMessage());
        }
        return count;
    }

    private InfraProjectCockpitBatch loadBatch(UUID batchId, String ownerDept, boolean privileged) {
        InfraProjectCockpitBatch batch = projectCockpitBatchRepository
            .findById(batchId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "批次不存在"));
        if (!privileged) {
            String targetDept = batch.getOwnerDept();
            if (StringUtils.hasText(targetDept)) {
                if (!StringUtils.hasText(ownerDept) || !targetDept.equalsIgnoreCase(ownerDept.trim())) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限访问该批次");
                }
            }
        }
        return batch;
    }

    private String resolveBatchStatus(ProjectCockpitLoadStats stats, int parseIssueCount) {
        if (stats.rejectedRowCount() > 0) {
            return "LOADED_WITH_REJECTIONS";
        }
        if (stats.warningRowCount() > 0 || parseIssueCount > 0) {
            return "LOADED_WITH_WARNINGS";
        }
        return "LOADED";
    }

    private InfraProjectCockpitIssue buildProjectCockpitIssue(
        InfraProjectCockpitBatch batch,
        int rowNo,
        ProjectCockpitIssueDescriptor descriptor,
        Map<String, String> payload
    ) {
        InfraProjectCockpitIssue issue = new InfraProjectCockpitIssue();
        issue.setBatchId(batch.getId());
        issue.setRowNo(rowNo);
        issue.setSeverity(descriptor.severity());
        issue.setIssueCode(descriptor.issueCode());
        issue.setIssueMessage(descriptor.issueMessage());
        Map<String, Object> issuePayload = new LinkedHashMap<>();
        issuePayload.put("projectNo", blankToEmpty(payload.get("project_no")));
        issuePayload.put("subsystem", blankToEmpty(payload.get("subsystem")));
        issuePayload.put("nodeTask", blankToEmpty(payload.get("node_task")));
        issuePayload.put("planDate", blankToEmpty(payload.get("plan_date")));
        issuePayload.put("completionStatus", blankToEmpty(payload.get("completion_status")));
        issuePayload.put("riskLevel", blankToEmpty(payload.get("risk_level")));
        issuePayload.put("rawPayload", new LinkedHashMap<>(payload));
        issue.setRawPayload(writeProps(issuePayload));
        return issue;
    }

    private ProjectCockpitRowAssessment assessProjectCockpitRow(Map<String, String> payload) {
        List<String> errorCodes = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();
        List<String> warnCodes = new ArrayList<>();
        List<String> warnMessages = new ArrayList<>();

        if (!hasMeaningfulValue(payload.get("project_no"))) {
            errorCodes.add("MISSING_PROJECT_NO");
            errorMessages.add("缺少项目编号");
        }

        String planDate = normalizeProjectCockpitValue(payload.get("plan_date"));
        if (!StringUtils.hasText(planDate)) {
            errorCodes.add("MISSING_PLAN_DATE");
            errorMessages.add("缺少计划日期");
        } else if (!isSupportedProjectCockpitDate(planDate)) {
            errorCodes.add("INVALID_PLAN_DATE");
            errorMessages.add("计划日期格式不可解析");
        }

        String actualDate = normalizeProjectCockpitValue(payload.get("actual_date"));
        if (StringUtils.hasText(actualDate) && !isSupportedProjectCockpitDate(actualDate)) {
            warnCodes.add("INVALID_ACTUAL_DATE");
            warnMessages.add("实际日期格式不可解析");
        }

        if (!hasMeaningfulValue(payload.get("subsystem"))) {
            warnCodes.add("MISSING_SUBSYSTEM");
            warnMessages.add("缺少分系统");
        }
        if (!hasMeaningfulValue(payload.get("completion_status"))) {
            warnCodes.add("MISSING_COMPLETION_STATUS");
            warnMessages.add("缺少完成状态");
        }
        if (!hasMeaningfulValue(payload.get("risk_level"))) {
            warnCodes.add("MISSING_RISK_LEVEL");
            warnMessages.add("缺少风险等级");
        }

        List<ProjectCockpitIssueDescriptor> descriptors = new ArrayList<>();
        if (!errorCodes.isEmpty()) {
            descriptors.add(new ProjectCockpitIssueDescriptor("ERROR", String.join(",", errorCodes), String.join("；", errorMessages)));
        }
        if (!warnCodes.isEmpty()) {
            descriptors.add(new ProjectCockpitIssueDescriptor("WARN", String.join(",", warnCodes), String.join("；", warnMessages)));
        }
        String parseStatus = !errorCodes.isEmpty() ? "BLOCKED" : (!warnCodes.isEmpty() ? "PARSED_WITH_WARNINGS" : "PARSED");
        return new ProjectCockpitRowAssessment(parseStatus, descriptors);
    }

    private boolean isMeaningfulProjectCockpitRow(Map<String, String> payload) {
        if (payload == null || payload.isEmpty()) {
            return false;
        }
        return payload.values().stream().map(this::normalizeProjectCockpitValue).anyMatch(StringUtils::hasText);
    }

    private boolean hasMeaningfulValue(String value) {
        return StringUtils.hasText(normalizeProjectCockpitValue(value));
    }

    private String normalizeProjectCockpitValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (!StringUtils.hasText(trimmed)) {
            return null;
        }
        return PLACEHOLDER_VALUES.contains(trimmed.toUpperCase(Locale.ROOT)) ? null : trimmed;
    }

    private boolean isSupportedProjectCockpitDate(String value) {
        String normalized = normalizeProjectCockpitValue(value);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        return normalized.matches("^\\d{4}-\\d{2}-\\d{2}$")
            || normalized.matches("^\\d{4}-\\d{2}-\\d{2}\\s+.*$")
            || normalized.matches("^\\d{4}/\\d{2}/\\d{2}$")
            || normalized.matches("^\\d{4}/\\d{2}/\\d{2}\\s+.*$")
            || normalized.matches("^\\d{4}\\.\\d{2}\\.\\d{2}$")
            || normalized.matches("^\\d{4}\\.\\d{2}\\.\\d{2}\\s+.*$")
            || normalized.matches("^\\d{8}$");
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

    private record ProjectCockpitIssueDescriptor(String severity, String issueCode, String issueMessage) {}

    private record ProjectCockpitRowAssessment(String parseStatus, List<ProjectCockpitIssueDescriptor> issues) {
        private boolean blocked() {
            return issues.stream().anyMatch(issue -> "ERROR".equalsIgnoreCase(issue.severity()));
        }

        private boolean hasWarnings() {
            return issues.stream().anyMatch(issue -> "WARN".equalsIgnoreCase(issue.severity()));
        }

        private String joinIssueCodes() {
            return issues.stream().map(ProjectCockpitIssueDescriptor::issueCode).filter(StringUtils::hasText).reduce((a, b) -> a + "," + b).orElse("");
        }

        private String joinIssueMessages() {
            return issues.stream().map(ProjectCockpitIssueDescriptor::issueMessage).filter(StringUtils::hasText).reduce((a, b) -> a + "；" + b).orElse("");
        }
    }

    private record ProjectCockpitLoadStats(
        int loadedRowCount,
        int acceptedRowCount,
        int rejectedRowCount,
        int warningRowCount,
        int issueCount,
        Path acceptedCsvPath,
        Path rejectedCsvPath
    ) {}

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
