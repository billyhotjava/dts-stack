package com.yuzhi.dts.ingestion.service.etl;

import com.yuzhi.dts.ingestion.config.AddaxProperties;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileUploadService {

    private static final Logger LOG = LoggerFactory.getLogger(FileUploadService.class);
    private static final String ADDAX_CONTAINER_DIR = "/opt/addax/jobs";
    private static final String UPLOADS_SUBDIR = "uploads";
    private static final int DEFAULT_PREVIEW_LIMIT = 20;
    private static final int MAX_PREVIEW_LIMIT = 2000;

    private final AddaxProperties properties;
    private final com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService settingsService;
    private final CsvParseService csvParseService;
    private final com.yuzhi.dts.ingestion.service.infra.InfraSettingsCryptoService crypto;

    public FileUploadService(
        AddaxProperties properties,
        com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService settingsService,
        CsvParseService csvParseService,
        com.yuzhi.dts.ingestion.service.infra.InfraSettingsCryptoService crypto
    ) {
        this.properties = properties;
        this.settingsService = settingsService;
        this.csvParseService = csvParseService;
        this.crypto = crypto;
    }

    public record FileUploadResult(
        String hostPath,
        String containerPath,
        String fileType,
        List<FileColumn> columns,
        String originalName,
        String fileId,
        String batchCode,
        String sheetName,
        Integer sheetIndex,
        List<SheetMetadata> sheets,
        String keyVersion,
        boolean encrypted,
        String fileHash,
        Long fileSize,
        Integer rowCount,
        Integer errorCount,
        List<List<String>> preview,
        String delimiter
    ) {}

    public record SheetMetadata(Integer index, String name) {}

    public record FileColumn(String name, String type, String label) {}

    public record ParsedUploadPayload(
        String fileId,
        String hostPath,
        String fileType,
        byte[] plain,
        List<FileColumn> columns,
        List<SheetMetadata> sheets,
        String sheetName,
        Integer sheetIndex,
        Integer rowCount,
        Integer errorCount,
        List<List<String>> preview,
        String delimiter,
        String fileHash,
        Long fileSize,
        String keyVersion,
        String containerPath
    ) {
    }

    public record ManagedUpload(
        String fileId,
        String hostPath,
        String containerPath,
        String fileHash
    ) {
    }

    public FileUploadResult handleUpload(MultipartFile file) {
        return handleUpload(file, null);
    }

    public FileUploadResult handleUpload(MultipartFile file, String uploadTraceId) {
        return handleUploadInternal(file, null, null, null, false, uploadTraceId);
    }

    public FileUploadResult handleUploadAndParse(MultipartFile file, Integer previewLimit, Integer sheetIndex, String sheetName) {
        return handleUploadAndParse(file, previewLimit, sheetIndex, sheetName, null);
    }

    public FileUploadResult handleUploadAndParse(
        MultipartFile file,
        Integer previewLimit,
        Integer sheetIndex,
        String sheetName,
        String uploadTraceId
    ) {
        return handleUploadInternal(file, previewLimit, sheetIndex, sheetName, true, uploadTraceId);
    }

    public FileUploadResult parseById(String fileId, Integer previewLimit, Integer sheetIndex, String sheetName, String originalName) {
        LOG.info(
            "Ingestion uploaded file parse-by-id start: fileId={}, previewLimit={}, sheetIndex={}, sheetName={}, originalName={}",
            fileId,
            previewLimit,
            sheetIndex,
            sheetName,
            originalName
        );
        try {
            ParsedUploadPayload parsed = parseExistingUpload(fileId, previewLimit, sheetIndex, sheetName);
            String resolvedOriginalName = resolveOriginalNameFromStoredFile(fileId, parsed.hostPath())
                .orElse(sanitizeOrDefault(originalName, parsed.fileId()));
            FileUploadResult result = buildResult(
                parsed.hostPath(),
                parsed.containerPath(),
                parsed.fileType(),
                parsed.columns(),
                resolvedOriginalName,
                parsed.fileId(),
                parsed.fileId(),
                parsed.sheetName(),
                parsed.sheetIndex(),
                parsed.sheets(),
                parsed.keyVersion(),
                true,
                parsed.fileHash(),
                parsed.fileSize(),
                parsed.rowCount(),
                parsed.errorCount(),
                parsed.preview(),
                parsed.delimiter()
            );
            LOG.info(
                "Ingestion uploaded file parse-by-id completed: fileId={}, fileType={}, columns={}, rows={}, errors={}, sheetIndex={}, sheetName={}, hostPath={}",
                result.fileId(),
                result.fileType(),
                result.columns() == null ? 0 : result.columns().size(),
                result.rowCount(),
                result.errorCount(),
                result.sheetIndex(),
                result.sheetName(),
                result.hostPath()
            );
            return result;
        } catch (RuntimeException ex) {
            LOG.info("Ingestion uploaded file parse-by-id failed: fileId={}, error={}", fileId, ex.getMessage(), ex);
            throw ex;
        }
    }

    private Optional<String> resolveOriginalNameFromStoredFile(String fileId, String containerPath) {
        if (!StringUtils.hasText(fileId) || !StringUtils.hasText(containerPath)) {
            return Optional.empty();
        }
        String fileName = Path.of(containerPath).getFileName().toString();
        int dotEnc = fileName.lastIndexOf(".enc");
        if (dotEnc > 0) {
            fileName = fileName.substring(0, dotEnc);
        }
        if (!fileName.startsWith(fileId + "_")) {
            return Optional.empty();
        }
        String originalName = fileName.substring(fileId.length() + 1);
        if (!StringUtils.hasText(originalName)) {
            return Optional.empty();
        }
        return Optional.of(originalName);
    }

    public byte[] readPlainBytes(Path filePath) {
        try {
            if (!Files.exists(filePath)) {
                throw new IllegalArgumentException("文件不存在: " + filePath);
            }
            byte[] stored = Files.readAllBytes(filePath);
            if (isEncryptedFile(filePath.getFileName().toString(), stored)) {
                return decryptStoredPayload(stored);
            }
            return stored;
        } catch (Exception ex) {
            throw new IllegalStateException("读取上传文件失败: " + ex.getMessage(), ex);
        }
    }

    public ManagedUpload verifyManagedUpload(String fileId, String expectedPlainSha256) {
        String normalizedFileId = StringUtils.hasText(fileId) ? fileId.trim() : null;
        if (
            !StringUtils.hasText(normalizedFileId)
                || !normalizedFileId.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")
        ) {
            throw new IllegalStateException("MANAGED_FILE_ID_INVALID: _fileId 无效");
        }
        if (!StringUtils.hasText(expectedPlainSha256)) {
            throw new IllegalStateException("FILE_CHECKSUM_REQUIRED: 文件封存缺少 fileChecksum");
        }

        try {
            Path uploadsDir = Paths.get(resolveJobDir(), UPLOADS_SUBDIR).toRealPath();
            Path resolved = resolveUploadedPath(normalizedFileId);
            if (
                Files.isSymbolicLink(resolved)
                    || !Files.isRegularFile(resolved, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    || !resolved.getFileName().toString().endsWith(".enc")
            ) {
                throw new IllegalStateException("MANAGED_FILE_ENCRYPTION_REQUIRED: 受管上传必须是加密 .enc 文件");
            }
            Path canonicalHostPath = resolved.toRealPath(java.nio.file.LinkOption.NOFOLLOW_LINKS);
            if (!uploadsDir.equals(canonicalHostPath.getParent())) {
                throw new IllegalStateException("MANAGED_FILE_PATH_INVALID: 受管上传文件不在规范目录");
            }

            byte[] stored = Files.readAllBytes(canonicalHostPath);
            if (!isEncryptedFile(canonicalHostPath.getFileName().toString(), stored)) {
                throw new IllegalStateException("MANAGED_FILE_ENCRYPTION_REQUIRED: 受管上传不是有效密文");
            }
            byte[] plain = decryptStoredPayload(stored);
            String actualPlainSha256 = sha256(plain);
            if (!expectedPlainSha256.trim().equalsIgnoreCase(actualPlainSha256)) {
                throw new IllegalStateException("FILE_CHECKSUM_MISMATCH: 文件明文摘要与密级封存不一致");
            }
            return new ManagedUpload(
                normalizedFileId,
                canonicalHostPath.toString(),
                ADDAX_CONTAINER_DIR + "/" + UPLOADS_SUBDIR + "/" + canonicalHostPath.getFileName(),
                actualPlainSha256
            );
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("MANAGED_FILE_VERIFICATION_FAILED: " + ex.getMessage(), ex);
        }
    }

    public String resolveJobDir() {
        var settings = settingsService.getSettings(com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService.SERVICE_ADDAX);
        String jobDir = settings.getString("jobDir", properties.getJobDir());
        if (!StringUtils.hasText(jobDir)) {
            throw new IllegalStateException("未配置 Addax 作业目录");
        }
        return jobDir.trim();
    }

    private FileUploadResult handleUploadInternal(
        MultipartFile file,
        Integer previewLimit,
        Integer requestedSheetIndex,
        String requestedSheetName,
        boolean includeParsed,
        String uploadTraceId
    ) {
        String traceId = normalizeTraceId(uploadTraceId);
        String requestName = file == null ? null : file.getOriginalFilename();
        Long declaredSize = file == null ? null : file.getSize();
        String contentType = file == null ? null : file.getContentType();
        LOG.info(
            "Ingestion file upload service received: traceId={}, name={}, declaredSize={}, contentType={}, includeParsed={}, previewLimit={}, requestedSheetIndex={}, requestedSheetName={}",
            traceId,
            requestName,
            declaredSize,
            contentType,
            includeParsed,
            previewLimit,
            requestedSheetIndex,
            requestedSheetName
        );
        if (file == null || file.isEmpty()) {
            LOG.info(
                "Ingestion file upload rejected: traceId={}, reason=empty_file, name={}, declaredSize={}, contentType={}",
                traceId,
                requestName,
                declaredSize,
                contentType
            );
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (!crypto.isEncryptionReady()) {
            LOG.info(
                "Ingestion file upload rejected: traceId={}, reason=encryption_not_ready, keyVersionPresent={}, name={}, declaredSize={}, contentType={}",
                traceId,
                StringUtils.hasText(crypto.currentKeyVersion()),
                requestName,
                declaredSize,
                contentType
            );
            throw new IllegalStateException("加密密钥未配置，禁止上传涉密文件");
        }
        String originalName = StringUtils.hasText(file.getOriginalFilename())
            ? file.getOriginalFilename().trim()
            : "upload";
        String fileId = UUID.randomUUID().toString();
        String extension = resolveExtension(originalName);
        String fileType = resolveFileType(extension);
        if (fileType == null) {
            LOG.info(
                "Ingestion file upload rejected: traceId={}, fileId={}, reason=unsupported_file_type, originalName={}, extension={}",
                traceId,
                fileId,
                originalName,
                extension
            );
            throw new IllegalArgumentException("不支持的文件类型: " + extension + "，仅支持 .xlsx, .xls, .csv");
        }
        LOG.info(
            "Ingestion file upload classified: traceId={}, fileId={}, originalName={}, extension={}, fileType={}, encryptionReady={}, keyVersionPresent={}",
            traceId,
            fileId,
            originalName,
            extension,
            fileType,
            crypto.isEncryptionReady(),
            StringUtils.hasText(crypto.currentKeyVersion())
        );

        byte[] plain;
        try {
            plain = file.getBytes();
            LOG.info(
                "Ingestion file upload bytes read: traceId={}, fileId={}, declaredSize={}, actualSize={}, contentType={}",
                traceId,
                fileId,
                declaredSize,
                plain.length,
                contentType
            );
        } catch (Exception ex) {
            LOG.info(
                "Ingestion file upload bytes read failed: traceId={}, fileId={}, name={}, declaredSize={}, contentType={}, error={}",
                traceId,
                fileId,
                originalName,
                declaredSize,
                contentType,
                ex.getMessage(),
                ex
            );
            throw new IllegalStateException("读取上传文件失败: " + ex.getMessage(), ex);
        }

        String jobDir = resolveJobDir();
        Path uploadsDir = Paths.get(jobDir, UPLOADS_SUBDIR);
        try {
            Files.createDirectories(uploadsDir);
            LOG.info(
                "Ingestion file upload directory ready: traceId={}, fileId={}, jobDir={}, uploadsDir={}, tmpDir={}",
                traceId,
                fileId,
                jobDir,
                uploadsDir,
                System.getProperty("java.io.tmpdir")
            );
        } catch (Exception ex) {
            LOG.info(
                "Ingestion file upload directory failed: traceId={}, fileId={}, jobDir={}, uploadsDir={}, error={}",
                traceId,
                fileId,
                jobDir,
                uploadsDir,
                ex.getMessage(),
                ex
            );
            throw new IllegalStateException("无法创建上传目录: " + uploadsDir, ex);
        }

        String storedName = fileId + "_" + sanitizeFileName(originalName) + ".enc";
        Path hostPath = uploadsDir.resolve(storedName);
        byte[] iv = crypto.randomIv();
        String keyVersion = crypto.currentKeyVersion();
        if (!StringUtils.hasText(keyVersion)) {
            LOG.info("Ingestion file upload rejected: traceId={}, fileId={}, reason=missing_key_version", traceId, fileId);
            throw new IllegalStateException("加密密钥版本未配置，禁止上传涉密文件");
        }
        writeEncryptedPayload(hostPath, plain, keyVersion, iv);
        String containerPath = ADDAX_CONTAINER_DIR + "/" + UPLOADS_SUBDIR + "/" + storedName;
        LOG.info(
            "Ingestion file upload encrypted payload written: traceId={}, fileId={}, hostPath={}, containerPath={}, plainSize={}, storedSize={}, keyVersion={}",
            traceId,
            fileId,
            hostPath,
            containerPath,
            plain.length,
            storedFileSize(hostPath),
            keyVersion
        );

        try {
            String sheetName = null;
            Integer sheetIndex = null;
            List<SheetMetadata> sheets = List.of();
            int rowCount = 0;
            int errorCount = 0;
            List<FileColumn> columns;
            List<List<String>> preview = null;
            String delimiter = null;

            if (includeParsed) {
                int normalizedPreviewLimit = normalizePreviewLimit(previewLimit);
                LOG.info(
                    "Ingestion file upload parse start: traceId={}, fileId={}, fileType={}, previewLimit={}, requestedSheetIndex={}, requestedSheetName={}",
                    traceId,
                    fileId,
                    fileType,
                    normalizedPreviewLimit,
                    requestedSheetIndex,
                    requestedSheetName
                );
                ParsedUploadPayload parsed = parseUploadedPayload(
                    plain,
                    fileId,
                    fileType,
                    requestedSheetIndex,
                    requestedSheetName,
                    normalizedPreviewLimit
                );
                columns = parsed.columns();
                sheets = parsed.sheets();
                sheetName = parsed.sheetName();
                sheetIndex = parsed.sheetIndex();
                rowCount = parsed.rowCount();
                errorCount = parsed.errorCount();
                preview = parsed.preview();
                delimiter = parsed.delimiter();
                LOG.info(
                    "Ingestion file upload parse completed: traceId={}, fileId={}, fileType={}, columns={}, rows={}, errors={}, sheetIndex={}, sheetName={}, sheets={}, previewRows={}",
                    traceId,
                    fileId,
                    fileType,
                    columns == null ? 0 : columns.size(),
                    rowCount,
                    errorCount,
                    sheetIndex,
                    sheetName,
                    sheets == null ? 0 : sheets.size(),
                    preview == null ? 0 : preview.size()
                );
            } else {
                LOG.info(
                    "Ingestion file upload header scan start: traceId={}, fileId={}, fileType={}, requestedSheetIndex={}, requestedSheetName={}",
                    traceId,
                    fileId,
                    fileType,
                    requestedSheetIndex,
                    requestedSheetName
                );
                if ("excel".equals(fileType)) {
                    sheetIndex = resolveRequestedExcelSheetIndex(plain, requestedSheetIndex, requestedSheetName);
                    sheetName = resolveRequestedSheetName(plain, sheetIndex);
                    sheets = listExcelSheets(plain);
                }
                columns = parseHeadersForUpload(plain, fileType);
                rowCount = 0;
                errorCount = 0;
                preview = null;
                delimiter = "csv".equals(fileType) ? "," : null;
                LOG.info(
                    "Ingestion file upload header scan completed: traceId={}, fileId={}, fileType={}, columns={}, sheetIndex={}, sheetName={}, sheets={}",
                    traceId,
                    fileId,
                    fileType,
                    columns == null ? 0 : columns.size(),
                    sheetIndex,
                    sheetName,
                    sheets == null ? 0 : sheets.size()
                );
            }

            String fileHash = sha256(plain);
            Long fileSize = (long) plain.length;

            return buildResult(
                hostPath.toString(),
                containerPath,
                fileType,
                columns,
                originalName,
                fileId,
                fileId,
                sheetName,
                sheetIndex,
                sheets,
                keyVersion,
                true,
                fileHash,
                fileSize,
                rowCount,
                errorCount,
                preview,
                delimiter
            );
        } catch (RuntimeException ex) {
            LOG.info(
                "Ingestion file upload post-processing failed: traceId={}, fileId={}, fileType={}, hostPath={}, includeParsed={}, error={}",
                traceId,
                fileId,
                fileType,
                hostPath,
                includeParsed,
                ex.getMessage(),
                ex
            );
            deleteUploadQuietly(hostPath, "upload post-processing failed");
            throw ex;
        }
    }

    private ParsedUploadPayload parseExistingUpload(
        String fileId,
        Integer previewLimit,
        Integer requestedSheetIndex,
        String requestedSheetName
    ) {
        if (!StringUtils.hasText(fileId)) {
            throw new IllegalArgumentException("fileId 不能为空");
        }
        Path hostPath = resolveUploadedPath(fileId);
        String fileType = detectFileTypeFromPath(hostPath);
        byte[] plain = readPlainBytes(hostPath);
        int normalizedPreviewLimit = normalizePreviewLimit(previewLimit);
        ParsedUploadPayload parsed = parseUploadedPayload(
            plain,
            fileId,
            fileType,
            requestedSheetIndex,
            requestedSheetName,
            normalizedPreviewLimit
        );
        String keyVersion = parseStoredKeyVersion(hostPath);
        return new ParsedUploadPayload(
            fileId,
            hostPath.toString(),
            fileType,
            plain,
            parsed.columns(),
            parsed.sheets(),
            parsed.sheetName(),
            parsed.sheetIndex(),
            parsed.rowCount(),
            parsed.errorCount(),
            parsed.preview(),
            parsed.delimiter(),
            sha256(plain),
            (long) plain.length,
            keyVersion,
            ADDAX_CONTAINER_DIR + "/" + UPLOADS_SUBDIR + "/" + hostPath.getFileName()
        );
    }

    private ParsedUploadPayload parseUploadedPayload(
        byte[] plain,
        String fileId,
        String fileType,
        Integer requestedSheetIndex,
        String requestedSheetName,
        int previewLimit
    ) {
        if ("excel".equals(fileType)) {
            int sheetIndex = resolveRequestedExcelSheetIndex(plain, requestedSheetIndex, requestedSheetName);
            List<SheetMetadata> sheets = listExcelSheets(plain);
            String sheetName = resolveRequestedSheetNameFromMetadata(sheets, sheetIndex);
            try {
                return parseExcelPreview(plain, fileId, sheetIndex, sheetName, previewLimit, sheets);
            } catch (Exception ex) {
                throw new IllegalStateException("解析 Excel 文件失败: " + ex.getMessage(), ex);
            }
        }

        if ("csv".equals(fileType)) {
            try {
                var parseResult = csvParseService.parse(new ByteArrayInputStream(plain));
                List<FileColumn> columns = toFileColumns(parseResult.columns());
                int rowCount = parseResult.totalRows();
                List<List<String>> preview = parseResult.rows().stream().limit(previewLimit).toList();
                return new ParsedUploadPayload(
                    fileId,
                    "",
                    fileType,
                    plain,
                    columns,
                    List.of(),
                    null,
                    null,
                    rowCount,
                    0,
                    preview,
                    ",",
                    sha256(plain),
                    (long) plain.length,
                    crypto.currentKeyVersion(),
                    ""
                );
            } catch (Exception ex) {
                throw new IllegalStateException("解析 CSV 文件失败: " + ex.getMessage(), ex);
            }
        }

        throw new IllegalArgumentException("暂不支持的文件类型: " + fileType);
    }

    private List<FileColumn> parseHeadersForUpload(byte[] plain, String fileType) {
        try {
            if ("excel".equals(fileType)) {
                return parseExcelHeaders(plain);
            }
            return parseCsvHeaders(plain);
        } catch (Exception ex) {
            LOG.warn("Failed to parse file headers: {}", ex.getMessage());
            return List.of();
        }
    }

    private List<FileColumn> toFileColumns(List<com.yuzhi.dts.ingestion.service.dto.ColumnInfo> columns) {
        List<FileColumn> result = new ArrayList<>();
        for (var column : columns) {
            result.add(new FileColumn(column.name(), lowerType(column.inferredType()), column.name()));
        }
        return result;
    }

    private ParsedUploadPayload parseExcelPreview(
        byte[] plain,
        String fileId,
        int sheetIndex,
        String sheetName,
        int previewLimit,
        List<SheetMetadata> sheets
    ) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(plain))) {
            Sheet sheet = resolveSheet(workbook, sheetIndex);
            Row headerRow = sheet == null ? null : sheet.getRow(0);
            if (sheet == null || headerRow == null) {
                return new ParsedUploadPayload(
                    fileId,
                    "",
                    "excel",
                    plain,
                    List.of(),
                    sheets,
                    sheetName,
                    sheetIndex,
                    0,
                    0,
                    List.of(),
                    null,
                    sha256(plain),
                    (long) plain.length,
                    crypto.currentKeyVersion(),
                    ""
                );
            }

            int colCount = Math.max(headerRow.getLastCellNum(), 0);
            Set<String> used = new LinkedHashSet<>();
            List<FileColumn> columns = new ArrayList<>();
            Row sampleRow = sheet.getRow(1);
            for (int i = 0; i < colCount; i++) {
                Cell headerCell = headerRow.getCell(i);
                String rawLabel = cellToString(headerCell);
                String label = rawLabel == null ? "" : rawLabel.trim();
                String name = SqlFieldNameResolver.resolve(label, i, used);
                String type = "string";
                if (sampleRow != null) {
                    type = inferTypeFromCell(sampleRow.getCell(i));
                }
                columns.add(new FileColumn(name, type, label));
            }

            int dataStartRow = 1;
            int lastRowNum = sheet.getLastRowNum();
            int rowCount = 0;
            List<List<String>> preview = new ArrayList<>();
            for (int rowIndex = dataStartRow; rowIndex <= lastRowNum; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                List<String> rowData = normalizeExcelRow(row, colCount);
                rowCount++;
                if (preview.size() < previewLimit) {
                    preview.add(rowData);
                }
            }
            return new ParsedUploadPayload(
                fileId,
                "",
                "excel",
                plain,
                columns,
                sheets,
                sheetName,
                sheetIndex,
                rowCount,
                0,
                preview,
                null,
                sha256(plain),
                (long) plain.length,
                crypto.currentKeyVersion(),
                ""
            );
        }
    }

    private String resolveRequestedSheetNameFromMetadata(List<SheetMetadata> sheets, int sheetIndex) {
        for (SheetMetadata meta : sheets) {
            if (Objects.equals(meta.index(), sheetIndex)) {
                return meta.name();
            }
        }
        return null;
    }

    private int resolveRequestedExcelSheetIndex(byte[] plain, Integer requestedSheetIndex, String requestedSheetName) {
        List<SheetMetadata> sheets = listExcelSheets(plain);
        if (requestedSheetIndex != null) {
            int index = requestedSheetIndex;
            for (SheetMetadata meta : sheets) {
                if (meta.index() == index) {
                    return index;
                }
            }
        }
        if (StringUtils.hasText(requestedSheetName)) {
            for (SheetMetadata meta : sheets) {
                if (requestedSheetName.equals(meta.name())) {
                    return meta.index();
                }
            }
        }
        return sheets.isEmpty() ? 0 : sheets.get(0).index();
    }

    private String resolveRequestedSheetName(byte[] plain, int sheetIndex) {
        for (SheetMetadata meta : listExcelSheets(plain)) {
            if (meta.index() == sheetIndex) {
                return meta.name();
            }
        }
        return null;
    }

    private List<SheetMetadata> listExcelSheets(byte[] plain) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(plain))) {
            List<SheetMetadata> result = new ArrayList<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String name = sheet == null ? ("Sheet" + i) : sheet.getSheetName();
                result.add(new SheetMetadata(i, name));
            }
            return result;
        } catch (Exception ex) {
            LOG.warn("Failed to list excel sheets: {}", ex.getMessage());
            return List.of();
        }
    }

    private Sheet resolveSheet(Workbook workbook, int sheetIndex) {
        if (workbook == null || workbook.getNumberOfSheets() <= 0) {
            return null;
        }
        if (sheetIndex >= 0 && sheetIndex < workbook.getNumberOfSheets()) {
            return workbook.getSheetAt(sheetIndex);
        }
        return workbook.getSheetAt(0);
    }

    private FileUploadResult buildResult(
        String hostPath,
        String containerPath,
        String fileType,
        List<FileColumn> columns,
        String originalName,
        String fileId,
        String batchCode,
        String sheetName,
        Integer sheetIndex,
        List<SheetMetadata> sheets,
        String keyVersion,
        boolean encrypted,
        String fileHash,
        Long fileSize,
        Integer rowCount,
        Integer errorCount,
        List<List<String>> preview,
        String delimiter
    ) {
        LOG.info(
            "Uploaded encrypted file: {} -> {} ({} columns detected)",
            originalName,
            hostPath,
            columns == null ? 0 : columns.size()
        );
        return new FileUploadResult(
            hostPath,
            containerPath,
            fileType,
            columns,
            originalName,
            fileId,
            batchCode,
            sheetName,
            sheetIndex,
            sheets,
            keyVersion,
            encrypted,
            fileHash,
            fileSize,
            rowCount,
            errorCount,
            preview,
            delimiter
        );
    }

    private void writeEncryptedPayload(Path hostPath, byte[] plain, String keyVersion, byte[] iv) {
        try {
            byte[] cipher = crypto.encryptStrict(plain, iv);
            byte[] versionBytes = keyVersion.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (versionBytes.length > 255) {
                throw new IllegalStateException("keyVersion 过长，无法写入文件头: " + keyVersion);
            }
            try (
                OutputStream out = Files.newOutputStream(
                    hostPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
                )
            ) {
                out.write(versionBytes.length);
                out.write(versionBytes);
                out.write(iv);
                out.write(cipher);
            }
        } catch (Exception ex) {
            LOG.info(
                "Ingestion file upload encrypted payload write failed: hostPath={}, plainSize={}, keyVersion={}, error={}",
                hostPath,
                plain == null ? null : plain.length,
                keyVersion,
                ex.getMessage(),
                ex
            );
            deleteUploadQuietly(hostPath, "encrypted payload write failed");
            throw new IllegalStateException("文件加密保存失败: " + ex.getMessage(), ex);
        }
    }

    private void deleteUploadQuietly(Path hostPath, String reason) {
        if (hostPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(hostPath);
        } catch (Exception cleanupEx) {
            LOG.warn("Failed to clean upload file after {}: {} ({})", reason, hostPath, cleanupEx.getMessage());
        }
    }

    private byte[] decryptStoredPayload(byte[] stored) {
        if (stored == null || stored.length < 14) {
            throw new IllegalStateException("文件内容不足，无法解析加密数据");
        }
        int versionLen = stored[0] & 0xFF;
        if (versionLen < 1 || versionLen > 64 || versionLen + 12 >= stored.length) {
            throw new IllegalStateException("加密文件头格式异常");
        }
        int ivStart = 1 + versionLen;
        int cipherStart = ivStart + 12;
        if (cipherStart >= stored.length) {
            throw new IllegalStateException("加密文件内容异常");
        }
        byte[] iv = java.util.Arrays.copyOfRange(stored, ivStart, cipherStart);
        byte[] cipherText = java.util.Arrays.copyOfRange(stored, cipherStart, stored.length);
        try {
            return crypto.decryptStrict(cipherText, iv);
        } catch (Exception ex) {
            throw new IllegalStateException("读取或解密文件失败: " + ex.getMessage(), ex);
        }
    }

    private String parseStoredKeyVersion(Path hostPath) {
        try {
            byte[] stored = Files.readAllBytes(hostPath);
            if (!isEncryptedFile(hostPath.getFileName().toString(), stored)) {
                return crypto.plaintextKeyVersion();
            }
            int versionLen = stored[0] & 0xFF;
            return new String(stored, 1, versionLen, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return crypto.currentKeyVersion();
        }
    }

    private boolean isEncryptedFile(String fileName, byte[] stored) {
        return StringUtils.hasText(fileName) && fileName.endsWith(".enc") && stored != null && stored.length > 13;
    }

    private int normalizePreviewLimit(Integer previewLimit) {
        if (previewLimit == null || previewLimit < 1) {
            return DEFAULT_PREVIEW_LIMIT;
        }
        if (previewLimit > MAX_PREVIEW_LIMIT) {
            return MAX_PREVIEW_LIMIT;
        }
        return previewLimit;
    }

    private String normalizeTraceId(String uploadTraceId) {
        return StringUtils.hasText(uploadTraceId) ? uploadTraceId.trim() : "-";
    }

    private Long storedFileSize(Path hostPath) {
        try {
            return hostPath == null || !Files.exists(hostPath) ? null : Files.size(hostPath);
        } catch (Exception ex) {
            LOG.debug("Failed to inspect stored upload size {}: {}", hostPath, ex.getMessage());
            return null;
        }
    }

    private String sanitizeOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private Path resolveUploadedPath(String fileId) {
        String jobDir = resolveJobDir();
        Path uploadsDir = Paths.get(jobDir, UPLOADS_SUBDIR);
        String prefix = fileId + "_";
        try {
            try (var stream = Files.list(uploadsDir)) {
                return stream
                    .filter(path -> path.getFileName().toString().startsWith(prefix) && path.getFileName().toString().endsWith(".enc"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("未找到上传文件，fileId=" + fileId));
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("未找到上传文件，fileId=" + fileId, ex);
        }
    }

    private String inferTypeFromCell(Cell cell) {
        if (cell == null) {
            return "string";
        }
        return switch (cell.getCellType()) {
            case NUMERIC -> DateUtil.isCellDateFormatted(cell) ? "date" : "double";
            case BOOLEAN -> "boolean";
            default -> "string";
        };
    }

    private String lowerType(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            return "string";
        }
        return rawType.toLowerCase(Locale.ROOT);
    }

    private List<String> normalizeExcelRow(Row row, int colCount) {
        List<String> values = new ArrayList<>(colCount);
        for (int i = 0; i < colCount; i++) {
            if (row == null || row.getCell(i) == null) {
                values.add(null);
            } else {
                values.add(cellToString(row.getCell(i)));
            }
        }
        return values;
    }

    private String cellToString(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue();
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            double val = cell.getNumericCellValue();
            if (val == Math.floor(val)) {
                return String.valueOf((long) val);
            }
            return String.valueOf(val);
        }
        return cell.toString();
    }

    private String detectFileTypeFromPath(Path path) {
        String name = StringUtils.hasText(path.getFileName().toString()) ? path.getFileName().toString() : "";
        String baseName = name.endsWith(".enc") ? name.substring(0, Math.max(0, name.length() - 4)) : name;
        return resolveFileType(resolveExtension(baseName));
    }

    private String resolveFileType(String extension) {
        if (extension == null) {
            return null;
        }
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "xlsx", "xls" -> "excel";
            case "csv" -> "csv";
            default -> null;
        };
    }

    private String resolveExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int idx = filename.lastIndexOf('.');
        if (idx < 0 || idx >= filename.length() - 1) {
            return "";
        }
        return filename.substring(idx + 1);
    }

    private String sanitizeFileName(String name) {
        if (!StringUtils.hasText(name)) {
            return "upload";
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public List<FileColumn> parseExcelHeaders(byte[] plain) throws Exception {
        List<FileColumn> columns = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(plain))) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return columns;
            }
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return columns;
            }
            Row sampleRow = sheet.getRow(1);
            Set<String> used = new LinkedHashSet<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell headerCell = headerRow.getCell(i);
                String label = cellToString(headerCell);
                String name = SqlFieldNameResolver.resolve(label, i, used);
                String type = "string";
                if (sampleRow != null) {
                    Cell sampleCell = sampleRow.getCell(i);
                    type = inferTypeFromCell(sampleCell);
                }
                columns.add(new FileColumn(name.trim(), type, label == null ? "" : label.trim()));
            }
        }
        return columns;
    }

    private List<FileColumn> parseCsvHeaders(byte[] plain) {
        try (InputStream inputStream = new ByteArrayInputStream(plain)) {
            return parseCsvHeaders(inputStream);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private List<FileColumn> parseCsvHeaders(InputStream inputStream) throws Exception {
        return csvParseService.parseHeaders(inputStream);
    }

    private String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (Exception ex) {
            LOG.warn("Failed to calculate file hash: {}", ex.getMessage());
            return null;
        }
    }

    public List<String> cleanupForTask(com.yuzhi.dts.ingestion.domain.IngestionTask task) {
        List<String> deleted = new ArrayList<>();
        if (task == null || task.getSourceConfig() == null) return List.of();
        var config = task.getSourceConfig();
        String hostPath = config.path("hostPath").asText(null);
        if (!StringUtils.hasText(hostPath)) {
            hostPath = config.path("_filePath").asText(null);
        }
        if (!StringUtils.hasText(hostPath)) {
            hostPath = config.path("filePath").asText(null);
        }
        if (!StringUtils.hasText(hostPath)) {
            hostPath = config.path("path").asText(null);
        }
        if (StringUtils.hasText(hostPath)) {
            try {
                Path path = Paths.get(hostPath.trim());
                if (Files.exists(path)) {
                    Files.delete(path);
                    deleted.add(hostPath);
                    LOG.info("[rollback] Deleted upload file: {}", hostPath);
                }
            } catch (Exception ex) {
                LOG.warn("[rollback] Failed to delete upload file {}: {}", hostPath, ex.getMessage());
            }
        }
        return deleted;
    }
}
