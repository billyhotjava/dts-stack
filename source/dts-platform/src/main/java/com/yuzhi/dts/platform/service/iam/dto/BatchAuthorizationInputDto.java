package com.yuzhi.dts.platform.service.iam.dto;

import java.time.Instant;
import java.util.List;

public record BatchAuthorizationInputDto(
    List<SubjectRef> subjects,
    List<ObjectRef> objects,
    Scope scope
) {
    public record SubjectRef(String type, String id, String name) {}
    public record ObjectRef(String datasetId, String datasetName) {}
    public record FieldRef(String name, String effect) {}

    public record Scope(
        String objectEffect,
        List<FieldRef> fields,
        String rowExpression,
        Instant validFrom,
        Instant validTo
    ) {}
}
