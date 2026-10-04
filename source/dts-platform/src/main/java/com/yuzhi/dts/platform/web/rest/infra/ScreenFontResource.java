package com.yuzhi.dts.platform.web.rest.infra;

import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/infra/screen-fonts")
public class ScreenFontResource {

    private static final Logger LOG = LoggerFactory.getLogger(ScreenFontResource.class);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("ttf", "otf", "woff", "woff2");
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20 MB

    private final Path storageDir;

    public ScreenFontResource(
        @Value("${dts.screen-fonts.storage-dir:/opt/dts/upload/screen-fonts}") String storageDirStr
    ) {
        this.storageDir = Path.of(storageDirStr);
        try {
            Files.createDirectories(this.storageDir);
        } catch (IOException e) {
            LOG.warn("Failed to create screen-fonts dir {}: {}", storageDirStr, e.getMessage());
        }
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, String>> upload(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "字体文件大小不能超过 20MB");
        }
        String originalName = file.getOriginalFilename();
        String ext = extractExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "不支持的字体格式，仅允许: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        String baseName = originalName != null
            ? originalName.substring(0, originalName.lastIndexOf('.')).replaceAll("[^\\w\\u4e00-\\u9fff\\-]", "_")
            : "font";
        String storedName = baseName + "_" + UUID.randomUUID().toString().substring(0, 8) + "." + ext;
        Path target = storageDir.resolve(storedName);
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.error("Failed to save screen font: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "字体保存失败");
        }

        String url = "/api/infra/screen-fonts/" + storedName;
        String fontFamily = baseName;
        LOG.info("Screen font uploaded: {} → {} (family: {})", originalName, url, fontFamily);
        return ApiResponses.ok(Map.of("url", url, "fontFamily", fontFamily, "filename", storedName));
    }

    @GetMapping
    public ApiResponse<List<Map<String, String>>> list() {
        List<Map<String, String>> fonts = new ArrayList<>();
        if (!Files.isDirectory(storageDir)) {
            return ApiResponses.ok(fonts);
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(storageDir)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                String ext = extractExtension(name);
                if (ALLOWED_EXTENSIONS.contains(ext)) {
                    String baseName = name.contains("_")
                        ? name.substring(0, name.lastIndexOf('_'))
                        : name.substring(0, name.lastIndexOf('.'));
                    fonts.add(Map.of(
                        "filename", name,
                        "fontFamily", baseName,
                        "url", "/api/infra/screen-fonts/" + name,
                        "format", ext
                    ));
                }
            }
        } catch (IOException e) {
            LOG.warn("Failed to list screen fonts: {}", e.getMessage());
        }
        return ApiResponses.ok(fonts);
    }

    @GetMapping("/{filename}")
    public ResponseEntity<Resource> serve(@PathVariable String filename) {
        String ext = extractExtension(filename);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            return ResponseEntity.badRequest().build();
        }
        Path filePath = storageDir.resolve(filename).normalize();
        if (!filePath.startsWith(storageDir)) {
            return ResponseEntity.badRequest().build();
        }
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }
            String contentType = switch (ext) {
                case "ttf" -> "font/ttf";
                case "otf" -> "font/otf";
                case "woff" -> "font/woff";
                case "woff2" -> "font/woff2";
                default -> "application/octet-stream";
            };
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    private static String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT).trim() : "";
    }
}
