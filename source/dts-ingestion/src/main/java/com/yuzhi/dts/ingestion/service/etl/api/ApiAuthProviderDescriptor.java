package com.yuzhi.dts.ingestion.service.etl.api;

import java.util.List;

public record ApiAuthProviderDescriptor(
    String id,
    String label,
    String description,
    List<ApiAuthProviderField> fields,
    Boolean supportsRotation,
    Boolean enabled
) {
    public ApiAuthProviderDescriptor(
        String id,
        String label,
        String description,
        List<ApiAuthProviderField> fields,
        Boolean supportsRotation
    ) {
        this(id, label, description, fields, supportsRotation, Boolean.TRUE);
    }
}
