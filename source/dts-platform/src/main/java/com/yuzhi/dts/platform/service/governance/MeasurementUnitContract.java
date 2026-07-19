package com.yuzhi.dts.platform.service.governance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Stable command, snapshot, and reference contract for governed measurement units. */
public final class MeasurementUnitContract {

    public static final int MAX_PRECISION = 18;

    private MeasurementUnitContract() {}

    public enum MeasurementUnitStatus {
        ACTIVE,
        INACTIVE,
    }

    public record MeasurementUnitCommand(
        String code,
        String name,
        String symbol,
        String quantityKind,
        BigDecimal conversionFactor,
        UUID baseUnitRef,
        Integer precision
    ) {}

    public record MeasurementUnitView(
        UUID id,
        String code,
        String name,
        String symbol,
        String quantityKind,
        BigDecimal conversionFactor,
        UUID baseUnitRef,
        int precision,
        MeasurementUnitStatus status,
        int version,
        String checksum,
        Instant createdAt,
        Instant updatedAt
    ) {}

    public record ExpectedVersion(UUID unitId, int version, String checksum) {}

    public record FieldIssue(String field, String code, String message) {}

    public record ReferenceImpact(int totalReferences, int restrictedReferences, List<ReferenceItem> items) {
        public ReferenceImpact {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record ReferenceItem(
        String resourceType,
        UUID resourceId,
        String displayName,
        Integer referencedVersion,
        Integer currentVersion,
        String driftStatus,
        String repairRoute,
        boolean restricted
    ) {}

    public static List<FieldIssue> validate(MeasurementUnitCommand command) {
        if (command == null) {
            return List.of(issue("request", "MEASUREMENT_UNIT_REQUEST_REQUIRED", "Measurement unit request is required"));
        }
        List<FieldIssue> issues = new ArrayList<>();
        if (!hasText(command.code())) {
            issues.add(issue("code", "MEASUREMENT_UNIT_CODE_REQUIRED", "Code is required"));
        } else if (!command.code().trim().matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")) {
            issues.add(issue("code", "MEASUREMENT_UNIT_CODE_INVALID", "Code contains unsupported characters"));
        }
        if (!hasText(command.name())) {
            issues.add(issue("name", "MEASUREMENT_UNIT_NAME_REQUIRED", "Name is required"));
        } else if (command.name().trim().length() > 256) {
            issues.add(issue("name", "MEASUREMENT_UNIT_NAME_INVALID", "Name must not exceed 256 characters"));
        }
        if (!hasText(command.symbol())) {
            issues.add(issue("symbol", "MEASUREMENT_UNIT_SYMBOL_REQUIRED", "Symbol is required"));
        } else if (command.symbol().trim().length() > 64) {
            issues.add(issue("symbol", "MEASUREMENT_UNIT_SYMBOL_INVALID", "Symbol must not exceed 64 characters"));
        }
        if (!hasText(command.quantityKind())) {
            issues.add(issue("quantityKind", "MEASUREMENT_UNIT_QUANTITY_KIND_REQUIRED", "Quantity kind is required"));
        } else if (!command.quantityKind().trim().matches("[A-Za-z][A-Za-z0-9_.-]{0,127}")) {
            issues.add(
                issue(
                    "quantityKind",
                    "MEASUREMENT_UNIT_QUANTITY_KIND_INVALID",
                    "Quantity kind must be an ASCII code of at most 128 characters"
                )
            );
        }
        if (!safeFactor(command.conversionFactor())) {
            issues.add(
                issue(
                    "conversionFactor",
                    "MEASUREMENT_UNIT_CONVERSION_FACTOR_INVALID",
                    "Conversion factor must be greater than zero"
                )
            );
        }
        if (command.precision() == null || command.precision() < 0 || command.precision() > MAX_PRECISION) {
            issues.add(
                issue(
                    "precision",
                    "MEASUREMENT_UNIT_PRECISION_INVALID",
                    "Precision must be between 0 and " + MAX_PRECISION
                )
            );
        }
        return List.copyOf(issues);
    }

    public static MeasurementUnitCommand normalize(MeasurementUnitCommand command) {
        if (command == null) return null;
        return new MeasurementUnitCommand(
            upper(command.code()),
            trim(command.name()),
            trim(command.symbol()),
            upper(command.quantityKind()),
            normalizeFactor(command.conversionFactor()),
            command.baseUnitRef(),
            command.precision()
        );
    }

    public static MeasurementUnitCommand fromView(MeasurementUnitView view) {
        return new MeasurementUnitCommand(
            view.code(),
            view.name(),
            view.symbol(),
            view.quantityKind(),
            view.conversionFactor(),
            view.baseUnitRef(),
            view.precision()
        );
    }

    private static FieldIssue issue(String field, String code, String message) {
        return new FieldIssue(field, code, message);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String upper(String value) {
        String normalized = trim(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static BigDecimal normalizeFactor(BigDecimal value) {
        if (value == null || value.signum() == 0) return value;
        return value.stripTrailingZeros();
    }

    private static boolean safeFactor(BigDecimal value) {
        if (value == null || value.signum() <= 0) return false;
        BigDecimal normalized = value.stripTrailingZeros();
        int fractionalDigits = Math.max(normalized.scale(), 0);
        int integerDigits = Math.max(normalized.precision() - normalized.scale(), 0);
        return fractionalDigits <= 18 && integerDigits <= 20;
    }
}
