package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Deletes only paths declared with a managed-file tag and contained by the configured DTS root. */
@Component
@Order(20)
public class ManagedFileCopyDestructionAdapter implements CatalogManagedCopyDestructionAdapter {

    private static final String TAG_PREFIX = "managed-file:";

    private final Path managedRoot;

    public ManagedFileCopyDestructionAdapter(
        @Value("${dts.lifecycle.destruction.file-root:}") String managedRoot
    ) {
        this.managedRoot = managedRoot == null || managedRoot.isBlank()
            ? null
            : Path.of(managedRoot).toAbsolutePath().normalize();
    }

    @Override
    public boolean supports(CatalogDataset dataset) {
        String type = dataset == null || dataset.getType() == null
            ? ""
            : dataset.getType().trim().toLowerCase(Locale.ROOT);
        return List.of("file", "excel", "csv").contains(type);
    }

    @Override
    public DestructionResult destroy(CatalogDataset dataset, String actionRef) {
        if (managedRoot == null) {
            return DestructionResult.blocked("DTS_MANAGED_FILE", "Managed file destruction root is not configured");
        }
        String relative = managedPath(dataset.getTags());
        if (relative == null) {
            return DestructionResult.blocked("DTS_MANAGED_FILE", "Dataset has no managed-file ownership tag");
        }
        Path target = managedRoot.resolve(relative).normalize();
        if (!target.startsWith(managedRoot) || target.equals(managedRoot)) {
            return DestructionResult.blocked("DTS_MANAGED_FILE", "Managed file path escapes the configured root");
        }
        try {
            boolean existed = Files.deleteIfExists(target);
            return new DestructionResult(
                true,
                "DTS_MANAGED_FILE",
                List.of("file:" + target),
                false,
                Map.of("existed", existed, "actionRef", actionRef),
                null
            );
        } catch (Exception failure) {
            return DestructionResult.blocked("DTS_MANAGED_FILE", failure.getMessage());
        }
    }

    private static String managedPath(String tags) {
        if (tags == null) {
            return null;
        }
        for (String tag : tags.split(",")) {
            String normalized = tag.trim();
            if (normalized.toLowerCase(Locale.ROOT).startsWith(TAG_PREFIX)) {
                String value = normalized.substring(TAG_PREFIX.length()).trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }
}
