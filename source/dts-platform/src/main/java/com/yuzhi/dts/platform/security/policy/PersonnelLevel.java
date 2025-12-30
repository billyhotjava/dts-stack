package com.yuzhi.dts.platform.security.policy;

import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public enum PersonnelLevel {
    // Rule (current system supports up to CONFIDENTIAL):
    // GENERAL -> PUBLIC/INTERNAL/SECRET
    // IMPORTANT -> PUBLIC/INTERNAL/SECRET/CONFIDENTIAL
    // CORE -> capped at CONFIDENTIAL for now
    GENERAL(List.of(DataLevel.DATA_PUBLIC, DataLevel.DATA_INTERNAL, DataLevel.DATA_SECRET), "SECRET"),
    IMPORTANT(List.of(DataLevel.DATA_PUBLIC, DataLevel.DATA_INTERNAL, DataLevel.DATA_SECRET, DataLevel.DATA_CONFIDENTIAL), "CONFIDENTIAL"),
    CORE(List.of(DataLevel.DATA_PUBLIC, DataLevel.DATA_INTERNAL, DataLevel.DATA_SECRET, DataLevel.DATA_CONFIDENTIAL), "CONFIDENTIAL");

    private final List<DataLevel> allowedDataLevels;
    private final List<String> allowedClassifications;
    private final String highestClassification;

    PersonnelLevel(List<DataLevel> allowedDataLevels, String highestClassification) {
        this.allowedDataLevels = List.copyOf(allowedDataLevels);
        this.allowedClassifications = this.allowedDataLevels
            .stream()
            .map(DataLevel::classification)
            .collect(Collectors.toUnmodifiableList());
        this.highestClassification = highestClassification;
    }

    /** Maximum rank corresponds to the most sensitive data level the personnel category may access. */
    public int rank() {
        return allowedDataLevels.get(allowedDataLevels.size() - 1).rank();
    }

    /** Ordered list of data levels accessible to this personnel category (PUBLIC → CONFIDENTIAL). */
    public List<DataLevel> allowedDataLevels() {
        return Collections.unmodifiableList(allowedDataLevels);
    }

    /** Ordered list of classification strings (PUBLIC/INTERNAL/SECRET/CONFIDENTIAL). */
    public List<String> allowedClassifications() {
        return allowedClassifications;
    }

    /** Highest classification string this personnel category may access. */
    public String maxClassification() {
        if (highestClassification != null && !highestClassification.isBlank()) {
            return highestClassification;
        }
        return allowedClassifications.get(allowedClassifications.size() - 1);
    }

    public static PersonnelLevel normalize(String value) {
        SecurityLevelCatalog.PersonnelSecurityLevel parsed = SecurityLevelCatalog.PersonnelSecurityLevel.parse(value);
        if (parsed == null) return null;
        return switch (parsed) {
            case GENERAL -> GENERAL;
            case IMPORTANT -> IMPORTANT;
            case CORE -> CORE;
        };
    }

}
