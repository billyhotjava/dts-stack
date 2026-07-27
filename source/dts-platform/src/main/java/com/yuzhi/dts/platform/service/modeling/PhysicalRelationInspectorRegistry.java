package com.yuzhi.dts.platform.service.modeling;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Fail-closed adapter capability registry for physical relation inspection. */
@Component
public class PhysicalRelationInspectorRegistry {

    private final Map<String, PhysicalRelationInspector> inspectors;

    public PhysicalRelationInspectorRegistry(
        List<PhysicalRelationInspector> inspectors
    ) {
        LinkedHashMap<String, PhysicalRelationInspector> registered =
            new LinkedHashMap<>();
        for (
            PhysicalRelationInspector inspector : inspectors == null
                ? List.<PhysicalRelationInspector>of()
                : inspectors
        ) {
            String adapter = normalize(inspector.adapter());
            if (registered.putIfAbsent(adapter, inspector) != null) {
                throw new IllegalStateException(
                    "Duplicate physical relation inspector: " +
                    adapter
                );
            }
        }
        this.inspectors = Map.copyOf(registered);
    }

    public PhysicalRelationInspector require(String adapter) {
        PhysicalRelationInspector inspector =
            inspectors.get(normalize(adapter));
        if (inspector == null) {
            throw new PhysicalRelationInspectionException(
                "MODEL_PHYSICAL_RELATION_ADAPTER_UNSUPPORTED",
                "Physical relation adapter is unsupported"
            );
        }
        return inspector;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value
            .trim()
            .replace("-", "")
            .replace("_", "")
            .toLowerCase(Locale.ROOT);
        return "postgresql".equals(normalized)
            ? "postgres"
            : normalized;
    }
}
