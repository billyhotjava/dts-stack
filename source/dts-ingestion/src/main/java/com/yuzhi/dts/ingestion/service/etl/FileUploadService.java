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
        String fileHash,
        Long fileSize,
        String sheetName,
        Integer sheetIndex,
        String keyVersion,
        boolean encrypted
    ) {}

    public record FileColumn(String name, String type, String label) {}

    public FileUploadResult handleUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        // 机密级：密钥未配置时 fail-fast，禁止明文落盘回退
        if (!crypto.isEncryptionReady()) {
            throw new IllegalStateException("加密密钥未配置，禁止上传涉密文件");
        }
        String originalName = StringUtils.hasText(file.getOriginalFilename())
            ? file.getOriginalFilename().trim()
            : "upload";
        String extension = resolveExtension(originalName);
        String fileType = resolveFileType(extension);
        if (fileType == null) {
            throw new IllegalArgumentException("不支持的文件类型: " + extension + "，仅支持 .xlsx, .xls, .csv");
        }

        byte[] plain;
        try {
            plain = file.getBytes();
        } catch (Exception ex) {
            throw new IllegalStateException("读取上传文件失败: " + ex.getMessage(), ex);
        }

        String jobDir = resolveJobDir();
        Path uploadsDir = Paths.get(jobDir, UPLOADS_SUBDIR);
        try {
            Files.createDirectories(uploadsDir);
        } catch (Exception ex) {
            throw new IllegalStateException("无法创建上传目录: " + uploadsDir, ex);
        }

        // 密文文件名加 .enc 后缀；自描述布局 [verLen:1B][keyVersion:verLen B][IV:12B][AES-GCM ciphertext+16B tag]
        String storedName = UUID.randomUUID().toString().substring(0, 8) + "_" + sanitizeFileName(originalName) + ".enc";
        Path hostPath = uploadsDir.resolve(storedName);
        byte[] iv = crypto.randomIv();
        String keyVersion = crypto.currentKeyVersion();
        try {
            byte[] cipher = crypto.encrypt(plain, iv);
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
                out.write(versionBytes.length); // verLen: 1 字节
                out.write(versionBytes);        // keyVersion（文件自描述加密版本，支持轮转）
                out.write(iv);                  // IV: 12B
                out.write(cipher);              // AES-GCM 密文 + 16B tag
            }
        } catch (Exception ex) {
            throw new IllegalStateException("文件加密保存失败: " + ex.getMessage(), ex);
        }

        String containerPath = ADDAX_CONTAINER_DIR + "/" + UPLOADS_SUBDIR + "/" + storedName;
        String fileHash = sha256(plain); // 明文摘要，供 F2 解密后完整性校验
        Long fileSize = (long) plain.length;
        String sheetName = null;
        Integer sheetIndex = null;

        List<FileColumn> columns;
        try {
            if ("excel".equals(fileType)) {
                sheetName = firstSheetName(plain);
                sheetIndex = 0;
                columns = parseExcelHeaders(plain);
            } else {
                columns = parseCsvHeaders(plain);
            }
        } catch (Exception ex) {
            LOG.warn("Failed to parse file headers: {}", ex.getMessage());
            columns = List.of();
        }

        LOG.info("Uploaded encrypted file: {} -> {} ({} columns detected)", originalName, hostPath, columns.size());
        return new FileUploadResult(
            hostPath.toString(),
            containerPath,
            fileType,
            columns,
            originalName,
            fileHash,
            fileSize,
            sheetName,
            sheetIndex,
            keyVersion, // 复用写入文件头的同一版本号，保证 .enc 头与审计元数据严格一致（P0-3 依赖）
            true
        );
    }

    private String firstSheetName(byte[] data) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(data))) {
            Sheet sheet = workbook.getSheetAt(0);
            return sheet == null ? null : sheet.getSheetName();
        } catch (Exception ex) {
            return null;
        }
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

    private List<FileColumn> parseExcelHeaders(byte[] data) throws Exception {
        List<FileColumn> columns = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(data))) {
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

    private List<FileColumn> parseCsvHeaders(byte[] data) throws Exception {
        try (InputStream inputStream = new ByteArrayInputStream(data)) {
            return csvParseService.parseHeaders(inputStream);
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

    private String inferTypeFromString(String value) {
        if (!StringUtils.hasText(value)) {
            return "string";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return "string";
        }
        try {
            Long.parseLong(trimmed);
            return "long";
        } catch (NumberFormatException ignored) {}
        try {
            Double.parseDouble(trimmed);
            return "double";
        } catch (NumberFormatException ignored) {}
        return "string";
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

    /**
     * Clean up uploaded files for a task.
     * Returns list of deleted file paths.
     */
    public List<String> cleanupForTask(com.yuzhi.dts.ingestion.domain.IngestionTask task) {
        List<String> deleted = new ArrayList<>();
        if (task == null || task.getSourceConfig() == null) return deleted;
        var config = task.getSourceConfig();
        if (config.has("hostPath")) {
            String hostPath = config.get("hostPath").asText(null);
            if (StringUtils.hasText(hostPath)) {
                Path path = Paths.get(hostPath.trim());
                try {
                    if (Files.exists(path)) {
                        Files.delete(path);
                        deleted.add(hostPath);
                        LOG.info("[rollback] Deleted upload file: {}", hostPath);
                    }
                } catch (Exception ex) {
                    LOG.warn("[rollback] Failed to delete upload file {}: {}", hostPath, ex.getMessage());
                }
            }
        }
        return deleted;
    }

    private String resolveJobDir() {
        var settings = settingsService.getSettings(com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService.SERVICE_ADDAX);
        String jobDir = settings.getString("jobDir", properties.getJobDir());
        if (!StringUtils.hasText(jobDir)) {
            throw new IllegalStateException("未配置 Addax 作业目录");
        }
        return jobDir.trim();
    }
}
