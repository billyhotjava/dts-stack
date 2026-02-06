package com.yuzhi.dts.platform.service.etl;

import com.yuzhi.dts.platform.config.DbtProperties;
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
        ".sql", ".yml", ".yaml", ".csv", ".md", ".txt"
    );

    private static final Set<String> ALLOWED_DIRS = Set.of(
        "models", "macros", "seeds", "tests", "snapshots", "analyses"
    );

    private static final Set<String> IGNORED_DIRS = Set.of(
        "target", "logs", "dbt_packages", ".git", "__pycache__", ".venv"
    );

    private static final Set<String> READ_ONLY_FILES = Set.of(
        "profiles/profiles.yml", "profiles/.user.yml"
    );

    private static final long MAX_FILE_SIZE = 1024 * 1024; // 1 MB
    private static final long MAX_ZIP_SIZE = 50 * 1024 * 1024; // 50 MB

    private final DbtProperties properties;

    public DbtFileService(DbtProperties properties) {
        this.properties = properties;
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

    // ── ZIP Upload & Extract ──────────────────────────────────

    public DbtImportResult importZip(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".zip")) {
            throw new IllegalArgumentException("请上传 .zip 文件");
        }
        if (file.getSize() > MAX_ZIP_SIZE) {
            throw new IllegalArgumentException("ZIP 文件过大，最大允许 50 MB");
        }

        Path projectDir = resolveProjectDir();
        int newFiles = 0;
        int overwrittenFiles = 0;
        List<String> skippedFiles = new ArrayList<>();
        List<String> importedFiles = new ArrayList<>();
        List<String> directories = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             ZipInputStream zis = new ZipInputStream(is, StandardCharsets.UTF_8)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName().replace('\\', '/');
                // Strip leading ./
                if (entryName.startsWith("./")) {
                    entryName = entryName.substring(2);
                }

                // Skip directories — they'll be created as needed
                if (entry.isDirectory()) {
                    continue;
                }

                // Must be under an allowed directory
                if (!isAllowedImportPath(entryName)) {
                    skippedFiles.add(entryName + " (不在允许的目录下)");
                    continue;
                }

                // Path traversal check
                if (entryName.contains("..")) {
                    skippedFiles.add(entryName + " (路径不安全)");
                    continue;
                }

                // Extension check
                if (!hasAllowedExtension(entryName)) {
                    skippedFiles.add(entryName + " (文件类型不允许)");
                    continue;
                }

                // Protected file check
                if (READ_ONLY_FILES.contains(entryName)) {
                    skippedFiles.add(entryName + " (系统保护文件)");
                    continue;
                }

                // Resolve and validate the target path
                Path targetFile = projectDir.resolve(entryName).normalize();
                if (!targetFile.startsWith(projectDir)) {
                    skippedFiles.add(entryName + " (路径越权)");
                    continue;
                }

                // Track directory
                String topDir = entryName.contains("/") ? entryName.substring(0, entryName.indexOf('/')) : entryName;
                if (!directories.contains(topDir)) {
                    directories.add(topDir);
                }

                // Check new vs overwrite
                boolean exists = Files.isRegularFile(targetFile);

                // Extract
                Files.createDirectories(targetFile.getParent());
                Files.copy(zis, targetFile, StandardCopyOption.REPLACE_EXISTING);

                if (exists) {
                    overwrittenFiles++;
                } else {
                    newFiles++;
                }
                importedFiles.add(entryName);

                zis.closeEntry();
            }
        } catch (IOException ex) {
            throw new IllegalStateException("解压 ZIP 失败: " + ex.getMessage(), ex);
        }

        int totalFiles = newFiles + overwrittenFiles;
        LOG.info("[dbt-files] imported ZIP '{}': {} files (new={}, overwrite={}, skipped={})",
            originalName, totalFiles, newFiles, overwrittenFiles, skippedFiles.size());

        return new DbtImportResult(totalFiles, newFiles, overwrittenFiles, directories, skippedFiles, importedFiles);
    }

    // ── Security Helpers ──────────────────────────────────────

    private Path resolveProjectDir() {
        String dir = properties.getProjectDir();
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

    private boolean isAllowedImportPath(String entryName) {
        for (String dir : ALLOWED_DIRS) {
            if (entryName.startsWith(dir + "/")) return true;
        }
        return false;
    }

    private String detectLanguage(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".sql")) return "sql";
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return "yaml";
        if (lower.endsWith(".md")) return "markdown";
        if (lower.endsWith(".csv")) return "plaintext";
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

    public record DbtImportResult(
        int totalFiles,
        int newFiles,
        int overwrittenFiles,
        List<String> directories,
        List<String> skippedFiles,
        List<String> importedFiles
    ) {}

    public record DbtFileSaveRequest(String path, String content) {}

    public record DbtFileCreateRequest(String path, String type, String content) {}

    public record DbtFileRenameRequest(String oldPath, String newPath) {}
}
