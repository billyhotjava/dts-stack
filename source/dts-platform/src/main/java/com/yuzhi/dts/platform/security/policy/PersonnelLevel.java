package com.yuzhi.dts.platform.security.policy;

import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public enum PersonnelLevel {
    GENERAL(SecurityLevelCatalog.PersonnelSecurityLevel.GENERAL),
    IMPORTANT(SecurityLevelCatalog.PersonnelSecurityLevel.IMPORTANT),
    CORE(SecurityLevelCatalog.PersonnelSecurityLevel.CORE);

    private final SecurityLevelCatalog.PersonnelSecurityLevel catalogLevel;
    private final List<DataLevel> allowedDataLevels;
    private final List<String> allowedClassifications;
    private final String highestClassification;

    PersonnelLevel(SecurityLevelCatalog.PersonnelSecurityLevel catalogLevel) {
        this.catalogLevel = catalogLevel;
        this.allowedDataLevels = SecurityLevelCatalog
            .allowedDataLevelsForPersonnel(catalogLevel)
            .stream()
            .map(DataLevel::fromCatalog)
            .filter(Objects::nonNull)
            .collect(Collectors.toUnmodifiableList());
        this.allowedClassifications = this.allowedDataLevels
            .stream()
            .map(DataLevel::classification)
            .collect(Collectors.toUnmodifiableList());
        SecurityLevelCatalog.DataSecurityLevel max = SecurityLevelCatalog.maxDataLevelForPersonnel(catalogLevel);
        this.highestClassification = max == null ? null : max.code();
    }

    /** Maximum rank corresponds to the most sensitive data level the personnel category may access. */
    public int rank() {
        return allowedDataLevels.get(allowedDataLevels.size() - 1).rank();
    }

    /** Ordered list of data levels accessible to this personnel category (PUBLIC → CONFIDENTIAL). */
    public List<DataLevel> allowedDataLevels() {
        return Collections.unmodifiableList(allowedDataLevels);
    }

    public String code() {
        return catalogLevel.code();
    }

    /** Ordered list of canonical data classification strings from the shared catalog. */
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
