package com.yuzhi.dts.platform.security.policy;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public enum PersonnelLevel {
    GENERAL(List.of(DataLevel.DATA_PUBLIC, DataLevel.DATA_INTERNAL)),
    IMPORTANT(List.of(DataLevel.DATA_PUBLIC, DataLevel.DATA_INTERNAL, DataLevel.DATA_SECRET)),
    CORE(List.of(DataLevel.DATA_PUBLIC, DataLevel.DATA_INTERNAL, DataLevel.DATA_SECRET, DataLevel.DATA_TOP_SECRET));

    private final List<DataLevel> allowedDataLevels;
    private final List<String> allowedClassifications;

    PersonnelLevel(List<DataLevel> allowedDataLevels) {
        this.allowedDataLevels = List.copyOf(allowedDataLevels);
        this.allowedClassifications = this.allowedDataLevels
            .stream()
            .map(DataLevel::classification)
            .collect(Collectors.toUnmodifiableList());
    }

    /** Maximum rank corresponds to the most sensitive data level the personnel category may access. */
    public int rank() {
        return allowedDataLevels.get(allowedDataLevels.size() - 1).rank();
    }

    /** Ordered list of data levels accessible to this personnel category (PUBLIC → TOP_SECRET). */
    public List<DataLevel> allowedDataLevels() {
        return Collections.unmodifiableList(allowedDataLevels);
    }

    /** Ordered list of classification strings (PUBLIC/INTERNAL/SECRET/TOP_SECRET). */
    public List<String> allowedClassifications() {
        return allowedClassifications;
    }

    /** Highest classification string this personnel category may access. */
    public String maxClassification() {
        return allowedClassifications.get(allowedClassifications.size() - 1);
    }

    public static PersonnelLevel normalize(String value) {
        if (value == null) return null;
        String v = value.trim().toUpperCase(Locale.ROOT);
        return switch (v) {
            case "GENERAL" -> GENERAL;
            case "IMPORTANT" -> IMPORTANT;
            case "CORE" -> CORE;
            default -> null;
        };
    }

}
