package com.yuzhi.dts.platform.service.iam.dto;

import java.util.List;

public record SubjectVisibleDto(
    List<VisibleObject> objects,
    List<VisibleField> fields,
    List<VisibleExpression> expressions
) {
    public record VisibleObject(String datasetId, String datasetName, String effect) {}
    public record VisibleField(String datasetName, String field, String effect) {}
    public record VisibleExpression(String datasetName, String expression) {}
}
