package com.yuzhi.dts.platform.service.modeling;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Adapter-neutral, credential-free contract for observing one physical warehouse relation. */
public interface PhysicalRelationInspector {

    Pattern IDENTIFIER = Pattern.compile(
        "^[A-Za-z_][A-Za-z0-9_]{0,127}$"
    );
    Pattern SHA_256_REF = Pattern.compile(
        "^sha256:[0-9a-f]{64}$"
    );

    String adapter();

    PhysicalRelationObservation observe(
        TargetContext target,
        RelationLocator locator
    );

    enum ExpectedRelationType {
        TABLE,
        VIEW,
        MATERIALIZED_VIEW,
    }

    record TargetContext(
        String executionTargetKey,
        String adapter,
        String databaseName,
        String schemaName,
        String credentialVersionRef
    ) {
        public TargetContext {
            executionTargetKey = required(
                executionTargetKey,
                "executionTargetKey"
            );
            adapter = required(adapter, "adapter")
                .toLowerCase(Locale.ROOT);
            databaseName = required(
                databaseName,
                "databaseName"
            );
            schemaName = canonicalIdentifier(
                schemaName,
                "schemaName"
            );
            credentialVersionRef = required(
                credentialVersionRef,
                "credentialVersionRef"
            );
            if (
                !SHA_256_REF
                    .matcher(credentialVersionRef)
                    .matches()
            ) {
                throw new IllegalArgumentException(
                    "credentialVersionRef must be SHA-256"
                );
            }
        }
    }

    record RelationLocator(
        String databaseName,
        String schemaName,
        String identifier,
        ExpectedRelationType expectedType,
        List<String> expectedColumns
    ) {
        public RelationLocator {
            databaseName = required(
                databaseName,
                "databaseName"
            );
            schemaName = canonicalIdentifier(
                schemaName,
                "schemaName"
            );
            identifier = canonicalIdentifier(
                identifier,
                "identifier"
            );
            if (expectedType == null) {
                throw new IllegalArgumentException(
                    "expectedType is required"
                );
            }
            expectedColumns = expectedColumns == null
                ? List.of()
                : expectedColumns
                    .stream()
                    .map(column ->
                        canonicalIdentifier(
                            column,
                            "expectedColumn"
                        )
                    )
                    .toList();
            if (
                expectedColumns.isEmpty() ||
                expectedColumns.size() > 1000 ||
                Set.copyOf(expectedColumns).size() !=
                expectedColumns.size()
            ) {
                throw new IllegalArgumentException(
                    "expectedColumns must contain 1 to 1000 unique identifiers"
                );
            }
        }
    }

    record PhysicalColumn(
        int ordinalPosition,
        String name,
        String dataType,
        boolean nullable
    ) {
        public PhysicalColumn {
            if (ordinalPosition < 1) {
                throw new IllegalArgumentException(
                    "ordinalPosition must be positive"
                );
            }
            name = canonicalIdentifier(name, "name");
            dataType = required(dataType, "dataType");
        }
    }

    record PhysicalRelationObservation(
        boolean exists,
        ExpectedRelationType actualType,
        List<PhysicalColumn> columns,
        String columnsChecksum,
        Instant observedAt,
        String errorCode
    ) {
        public PhysicalRelationObservation {
            columns = columns == null
                ? List.of()
                : List.copyOf(columns);
            if (observedAt == null) {
                throw new IllegalArgumentException(
                    "observedAt is required"
                );
            }
            if (exists) {
                if (
                    actualType == null ||
                    columns.isEmpty() ||
                    columnsChecksum == null ||
                    !columnsChecksum.matches("^[0-9a-f]{64}$") ||
                    errorCode != null
                ) {
                    throw new IllegalArgumentException(
                        "existing relation requires complete metadata"
                    );
                }
            } else if (
                actualType != null ||
                !columns.isEmpty() ||
                columnsChecksum != null ||
                errorCode == null ||
                errorCode.isBlank()
            ) {
                throw new IllegalArgumentException(
                    "missing relation requires only an error code"
                );
            }
        }
    }

    private static String canonicalIdentifier(
        String value,
        String name
    ) {
        String normalized = required(value, name);
        if (!IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                name + " is not a canonical identifier"
            );
        }
        return normalized;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                name + " is required"
            );
        }
        return value.trim();
    }
}
