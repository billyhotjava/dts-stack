package com.yuzhi.dts.platform.service.security;

import java.util.Locale;
import java.util.Set;

public interface AssetActionSubjectResolver {
    Set<SubjectKey> currentSubjects();

    record SubjectKey(String type, String id) {
        public SubjectKey {
            if (type == null || type.isBlank() || id == null || id.isBlank()) {
                throw new IllegalArgumentException("Action-policy subject type and id are required");
            }
            type = type.trim().toUpperCase(Locale.ROOT);
            id = id.trim();
        }
    }
}
