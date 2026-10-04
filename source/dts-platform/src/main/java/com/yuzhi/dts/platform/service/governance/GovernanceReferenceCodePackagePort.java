package com.yuzhi.dts.platform.service.governance;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Governance-owned mutation contract used by the standard-package orchestrator. */
public interface GovernanceReferenceCodePackagePort {

    DirectoryChanges applyDirectories(List<Map<String, Object>> directories);

    List<Change> applyValues(List<Map<String, Object>> values, Map<String, String> directoryIdsByCode);

    List<Change> applyMappings(List<Map<String, Object>> mappings, Map<String, String> directoryIdsByCode);

    boolean rollbackCreate(EntityType entityType, String entityId);

    boolean rollbackUpdate(EntityType entityType, String entityId, Map<String, Object> beforeImage);

    enum EntityType {
        CODE_DIRECTORY,
        CODE_VALUE,
        CODE_MAPPING,
    }

    enum Action {
        CREATE,
        UPDATE,
    }

    record DirectoryChanges(List<Change> changes, Map<String, String> directoryIdsByCode) {
        public DirectoryChanges {
            changes = changes == null ? List.of() : List.copyOf(changes);
            directoryIdsByCode = directoryIdsByCode == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(directoryIdsByCode));
        }
    }

    record Change(EntityType entityType, String entityId, Action action, Map<String, Object> beforeImage) {
        public Change {
            if (entityType == null || action == null || entityId == null || entityId.isBlank()) {
                throw new IllegalArgumentException("reference-code change identity is incomplete");
            }
            beforeImage = beforeImage == null
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(beforeImage));
        }
    }
}
