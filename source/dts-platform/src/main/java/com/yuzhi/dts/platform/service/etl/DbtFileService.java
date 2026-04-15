package com.yuzhi.dts.platform.service.etl;

import com.yuzhi.dts.platform.config.DbtProperties;
import com.yuzhi.dts.platform.domain.modeling.ModelingSqlModel;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DbtFileService {

    private static final Logger LOG = LoggerFactory.getLogger(DbtFileService.class);

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        ".sql", ".yml", ".yaml", ".csv", ".tsv", ".md", ".txt",
        ".json", ".py", ".sh", ".bash", ".toml", ".cfg", ".conf"
    );

    private static final Set<String> IGNORED_DIRS = Set.of(
        "target", "logs", "dbt_packages", ".git", "__pycache__", ".venv"
    );

    private static final Set<String> READ_ONLY_FILES = Set.of(
        "profiles/profiles.yml", "profiles/.user.yml"
    );

    private static final long MAX_FILE_SIZE = 1024 * 1024; // 1 MB

    // Zip upload safety limits
    private static final int MAX_ZIP_ENTRIES = 5000;
    private static final long MAX_ZIP_ENTRY_SIZE = 20L * 1024 * 1024;  // 20 MB per entry
    private static final long MAX_ZIP_TOTAL_SIZE = 200L * 1024 * 1024; // 200 MB total
    private static final long MAX_ARCHIVE_FILE_SIZE = 100L * 1024 * 1024; // 100 MB zip file
    private final DbtProperties properties;
    private final DbtConfigService configService;
    private final ModelingSqlModelRepository sqlModelRepository;

    public DbtFileService(DbtProperties properties, DbtConfigService configService, ModelingSqlModelRepository sqlModelRepository) {
        this.properties = properties;
        this.configService = configService;
        this.sqlModelRepository = sqlModelRepository;
    }

    // ── Directory Tree ────────────────────────────────────────

    public DbtFileNode getTree() {
        Path projectDir = resolveProjectDir();
        if (!Files.isDirectory(projectDir)) {
            return new DbtFileNode(projectDir.getFileName().toString(), "", "directory",
                0L, null, true, List.of());
        }
        return buildTree(projectDir, projectDir);
    }

    private DbtFileNode buildTree(Path current, Path root) {
        String name = root.equals(current)
            ? current.getFileName().toString()
            : current.getFileName().toString();
        String relativePath = root.equals(current) ? "" : root.relativize(current).toString().replace('\\', '/');
        boolean readOnly = isReadOnly(relativePath);

        if (Files.isDirectory(current)) {
            List<DbtFileNode> children = new ArrayList<>();
            try (var stream = Files.list(current)) {
                stream
                    .filter(p -> !IGNORED_DIRS.contains(p.getFileName().toString()))
                    .sorted(Comparator.<Path, Boolean>comparing(p -> !Files.isDirectory(p))
                        .thenComparing(p -> p.getFileName().toString().toLowerCase()))
                    .forEach(p -> children.add(buildTree(p, root)));
            } catch (IOException ex) {
                LOG.warn("[dbt-files] failed to list directory: {}", current, ex);
            }
            return new DbtFileNode(name, relativePath, "directory", 0L, null, readOnly, children);
        }

        long size = 0;
        Instant lastModified = null;
        try {
            BasicFileAttributes attrs = Files.readAttributes(current, BasicFileAttributes.class);
            size = attrs.size();
            lastModified = attrs.lastModifiedTime().toInstant();
        } catch (IOException ignored) {}
        return new DbtFileNode(name, relativePath, "file", size, lastModified, readOnly, null);
    }

    // ── File Content Read ─────────────────────────────────────

    public DbtFileContent readFile(String relativePath) {
        Path file = resolveAndValidate(relativePath);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("文件不存在: " + relativePath);
        }
        try {
            long size = Files.size(file);
            if (size > MAX_FILE_SIZE) {
                throw new IllegalArgumentException("文件过大 (" + (size / 1024) + " KB)，最大允许 1 MB");
            }
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String language = detectLanguage(relativePath);
            boolean readOnly = isReadOnly(relativePath);
            return new DbtFileContent(relativePath, content, language, size, readOnly);
        } catch (IOException ex) {
            throw new IllegalStateException("读取文件失败: " + ex.getMessage(), ex);
        }
    }

    // ── File Content Save ─────────────────────────────────────

    public void saveFile(String relativePath, String content) {
        if (isReadOnly(relativePath)) {
            throw new IllegalArgumentException("该文件为只读，不允许编辑: " + relativePath);
        }
        validateExtension(relativePath);
        Path file = resolveAndValidate(relativePath);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("文件不存在: " + relativePath);
        }
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
            LOG.info("[dbt-files] saved: {}", relativePath);
        } catch (IOException ex) {
            throw new IllegalStateException("保存文件失败: " + ex.getMessage(), ex);
        }
        // Sync SQL content back to modeling_sql_model and reset status to DRAFT
        if (relativePath.startsWith("models/") && relativePath.endsWith(".sql")) {
            try {
                List<ModelingSqlModel> models = sqlModelRepository.findByModelPathIn(List.of(relativePath));
                for (ModelingSqlModel model : models) {
                    // Strip {{ config(...) }} header before storing — header is regenerated at write time
                    model.setSqlText(stripConfigHeader(content));
                    model.setStatus("DRAFT");
                }
                if (!models.isEmpty()) {
                    sqlModelRepository.saveAll(models);
                    LOG.info("[dbt-files] Synced SQL content back to {} model(s), status reset to DRAFT", models.size());
                }
            } catch (RuntimeException ex) {
                LOG.warn("[dbt-files] Failed to sync SQL back to model record: {}", ex.getMessage());
            }
        }
    }

    // ── Create File / Directory ───────────────────────────────

    public void createFile(String relativePath, String type, String content) {
        validateExtension(relativePath);
        Path target = resolveAndValidate(relativePath);
        if (Files.exists(target)) {
            throw new IllegalArgumentException("已存在: " + relativePath);
        }
        try {
            if ("directory".equals(type)) {
                Files.createDirectories(target);
                LOG.info("[dbt-files] created directory: {}", relativePath);
            } else {
                Files.createDirectories(target.getParent());
                Files.writeString(target, content != null ? content : "", StandardCharsets.UTF_8);
                LOG.info("[dbt-files] created file: {}", relativePath);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("创建失败: " + ex.getMessage(), ex);
        }
    }

    // ── Delete ────────────────────────────────────────────────

    public void deleteFile(String relativePath) {
        if (isReadOnly(relativePath)) {
            throw new IllegalArgumentException("该文件为只读，不允许删除: " + relativePath);
        }
        Path target = resolveAndValidate(relativePath);
        if (!Files.exists(target)) {
            throw new IllegalArgumentException("文件不存在: " + relativePath);
        }
        // Prevent deleting top-level dbt directories entirely
        Path projectDir = resolveProjectDir();
        if (target.getParent().equals(projectDir) && Files.isDirectory(target)) {
            throw new IllegalArgumentException("不允许删除顶级目录: " + relativePath);
        }
        try {
            if (Files.isDirectory(target)) {
                Files.walkFileTree(target, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                Files.delete(target);
            }
            LOG.info("[dbt-files] deleted: {}", relativePath);
        } catch (IOException ex) {
            throw new IllegalStateException("删除失败: " + ex.getMessage(), ex);
        }
    }

    // ── Rename / Move ─────────────────────────────────────────

    public void renameFile(String oldPath, String newPath) {
        if (isReadOnly(oldPath)) {
            throw new IllegalArgumentException("该文件为只读: " + oldPath);
        }
        Path source = resolveAndValidate(oldPath);
        Path dest = resolveAndValidate(newPath);
        if (!Files.exists(source)) {
            throw new IllegalArgumentException("源文件不存在: " + oldPath);
        }
        if (Files.exists(dest)) {
            throw new IllegalArgumentException("目标已存在: " + newPath);
        }
        try {
            Files.createDirectories(dest.getParent());
            Files.move(source, dest, StandardCopyOption.ATOMIC_MOVE);
            LOG.info("[dbt-files] renamed: {} -> {}", oldPath, newPath);
        } catch (IOException ex) {
            throw new IllegalStateException("重命名失败: " + ex.getMessage(), ex);
        }
    }

    // ── Archive Upload (ZIP overwrite) ────────────────────────

    /**
     * Extract a user-uploaded ZIP on top of the dbt project directory.
     *
     * @param archive the uploaded zip file
     * @param cleanBeforeExtract if true, for each top-level directory present in the zip
     *                           (e.g. "macros/", "models/") the corresponding directory under
     *                           projectDir is deleted first. Protected files and ignored dirs
     *                           (dbt_project.yml, profiles/, target/, logs/, dbt_packages/, .git/, ...)
     *                           are never touched.
     */
    public DbtArchiveUploadResult uploadArchive(MultipartFile archive, boolean cleanBeforeExtract) {
        if (archive == null || archive.isEmpty()) {
            throw new IllegalArgumentException("请上传 ZIP 压缩包");
        }
        String originalName = archive.getOriginalFilename();
        if (originalName != null && !originalName.toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException("仅支持 .zip 格式");
        }
        if (archive.getSize() > MAX_ARCHIVE_FILE_SIZE) {
            throw new IllegalArgumentException("压缩包过大，最大允许 " + (MAX_ARCHIVE_FILE_SIZE / 1024 / 1024) + " MB");
        }

        Path projectDir = resolveProjectDir();
        try {
            Files.createDirectories(projectDir);
        } catch (IOException ex) {
            throw new IllegalStateException("创建项目目录失败: " + ex.getMessage(), ex);
        }

        Path tempFile;
        try {
            tempFile = Files.createTempFile("dbt-upload-", ".zip");
            archive.transferTo(tempFile.toFile());
        } catch (IOException ex) {
            throw new IllegalStateException("保存上传文件失败: " + ex.getMessage(), ex);
        }

        List<String> extracted = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> cleaned = new ArrayList<>();
        try {
            if (cleanBeforeExtract) {
                Set<String> topLevels = peekTopLevelEntries(tempFile);
                for (String top : topLevels) {
                    if (isProtectedTopLevel(top)) {
                        continue;
                    }
                    Path target = projectDir.resolve(top).normalize();
                    if (!target.startsWith(projectDir) || target.equals(projectDir)) {
                        continue;
                    }
                    if (Files.exists(target)) {
                        deleteRecursively(target);
                        cleaned.add(top);
                    }
                }
            }
            extractZip(tempFile, projectDir, extracted, skipped);
        } catch (IOException ex) {
            throw new IllegalStateException("解压失败: " + ex.getMessage(), ex);
        } finally {
            try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
        }

        LOG.info("[dbt-files] archive uploaded: extracted={}, skipped={}, cleaned={}, clean={}",
            extracted.size(), skipped.size(), cleaned.size(), cleanBeforeExtract);
        return new DbtArchiveUploadResult(extracted, skipped, cleaned, cleanBeforeExtract);
    }

    private Set<String> peekTopLevelEntries(Path zipPath) throws IOException {
        Set<String> result = new LinkedHashSet<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("..")) {
                    continue;
                }
                int slash = name.indexOf('/');
                String top = slash < 0 ? name : name.substring(0, slash);
                if (StringUtils.hasText(top)) {
                    result.add(top);
                }
            }
        }
        return result;
    }

    private boolean isProtectedTopLevel(String top) {
        if (!StringUtils.hasText(top)) return true;
        if (IGNORED_DIRS.contains(top)) return true;
        if ("profiles".equals(top)) return true;
        if ("dbt_project.yml".equals(top)) return true;
        return false;
    }

    private void extractZip(Path zipPath, Path destDir, List<String> extracted, List<String> skipped) throws IOException {
        int count = 0;
        long total = 0;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (++count > MAX_ZIP_ENTRIES) {
                    throw new IOException("压缩包条目过多 (> " + MAX_ZIP_ENTRIES + ")");
                }
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("..")) {
                    skipped.add(name + " (非法路径)");
                    continue;
                }
                // Skip top-level protected/ignored directories
                int firstSlash = name.indexOf('/');
                String top = firstSlash < 0 ? name : name.substring(0, firstSlash);
                if (IGNORED_DIRS.contains(top)) {
                    skipped.add(name + " (忽略目录)");
                    continue;
                }
                // Skip read-only files
                if (READ_ONLY_FILES.contains(name) || "dbt_project.yml".equals(name)) {
                    skipped.add(name + " (只读)");
                    continue;
                }
                Path target = destDir.resolve(name).normalize();
                if (!target.startsWith(destDir)) {
                    skipped.add(name + " (路径越权)");
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                if (!hasAllowedExtension(name)) {
                    skipped.add(name + " (不支持的类型)");
                    continue;
                }
                long entrySize = entry.getSize();
                if (entrySize > MAX_ZIP_ENTRY_SIZE) {
                    skipped.add(name + " (单文件过大)");
                    continue;
                }

                Files.createDirectories(target.getParent());
                long written = copyBounded(zis, target, MAX_ZIP_ENTRY_SIZE);
                total += written;
                if (total > MAX_ZIP_TOTAL_SIZE) {
                    throw new IOException("解压总大小超过 " + (MAX_ZIP_TOTAL_SIZE / 1024 / 1024) + " MB");
                }
                extracted.add(name);
            }
        }
    }

    private long copyBounded(InputStream in, Path target, long maxBytes) throws IOException {
        byte[] buf = new byte[8192];
        long total = 0;
        try (var out = Files.newOutputStream(target)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > maxBytes) {
                    throw new IOException("单文件超过限制 " + (maxBytes / 1024 / 1024) + " MB: " + target.getFileName());
                }
                out.write(buf, 0, n);
            }
        }
        return total;
    }

    private void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // ── Security Helpers ──────────────────────────────────────

    private Path resolveProjectDir() {
        String dir = properties.getProjectDir();
        DbtConfigService.DbtConfigView view = configService.loadConfig();
        if (view != null && view.config() != null && StringUtils.hasText(view.config().projectDir())) {
            dir = view.config().projectDir();
        }
        if (!StringUtils.hasText(dir)) {
            dir = "/opt/dts/dbt";
        }
        return Path.of(dir);
    }

    private Path resolveAndValidate(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            throw new IllegalArgumentException("路径不能为空");
        }
        // Reject obvious traversal
        if (relativePath.contains("..")) {
            throw new SecurityException("非法路径");
        }
        Path projectDir = resolveProjectDir();
        Path resolved = projectDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(projectDir)) {
            throw new SecurityException("路径越权访问");
        }
        return resolved;
    }

    private void validateExtension(String path) {
        // Directories don't need extension check
        if (path.endsWith("/")) return;
        if (!hasAllowedExtension(path)) {
            throw new IllegalArgumentException("不支持的文件类型，允许: " + ALLOWED_EXTENSIONS);
        }
    }

    private boolean hasAllowedExtension(String path) {
        String lower = path.toLowerCase();
        for (String ext : ALLOWED_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    private boolean isReadOnly(String relativePath) {
        if (!StringUtils.hasText(relativePath)) return false;
        if (READ_ONLY_FILES.contains(relativePath)) return true;
        // dbt_project.yml at root is read-only
        return "dbt_project.yml".equals(relativePath);
    }

    private String detectLanguage(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".sql")) return "sql";
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return "yaml";
        if (lower.endsWith(".md")) return "markdown";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".py")) return "python";
        if (lower.endsWith(".sh") || lower.endsWith(".bash")) return "shell";
        if (lower.endsWith(".toml")) return "ini";
        if (lower.endsWith(".csv") || lower.endsWith(".tsv")) return "plaintext";
        if (lower.endsWith(".txt") || lower.endsWith(".cfg") || lower.endsWith(".conf")) return "plaintext";
        return "plaintext";
    }

    // ── DTOs ──────────────────────────────────────────────────

    public record DbtFileNode(
        String name,
        String path,
        String type,
        long size,
        Instant lastModified,
        boolean readOnly,
        List<DbtFileNode> children
    ) {}

    public record DbtFileContent(
        String path,
        String content,
        String language,
        long size,
        boolean readOnly
    ) {}

    public record DbtFileSaveRequest(String path, String content) {}

    /**
     * Strip the dbt {{ config(...) }} header from SQL content, leaving only the body.
     * The config header is regenerated by ModelingSqlModelService.buildSqlContent() at write time.
     */
    private static String stripConfigHeader(String content) {
        if (content == null) return "";
        String trimmed = content.strip();
        // Match {{ config(...) }} at the start, possibly spanning multiple lines
        if (trimmed.startsWith("{{") && trimmed.contains("config")) {
            int closingIdx = trimmed.indexOf("}}");
            if (closingIdx > 0) {
                String body = trimmed.substring(closingIdx + 2).strip();
                return body.isEmpty() ? "" : body;
            }
        }
        return trimmed;
    }

    public record DbtFileCreateRequest(String path, String type, String content) {}

    public record DbtFileRenameRequest(String oldPath, String newPath) {}

    public record DbtArchiveUploadResult(
        List<String> extracted,
        List<String> skipped,
        List<String> cleaned,
        boolean cleanBeforeExtract
    ) {}
}
