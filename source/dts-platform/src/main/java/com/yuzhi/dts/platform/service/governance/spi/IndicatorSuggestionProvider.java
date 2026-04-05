package com.yuzhi.dts.platform.service.governance.spi;

import java.util.List;
import java.util.Map;

/**
 * SPI for LLM-based indicator suggestions.
 * Default implementation is a no-op; a real LLM adapter can be plugged in later.
 */
public interface IndicatorSuggestionProvider {
    List<IndicatorSuggestion> suggestFromSchema(TableSchemaInfo schema);
    SqlReviewResult reviewSql(String sql, String indicatorCode);
    List<TemplateSuggestion> suggestFromUpload(UploadedFileInfo file);

    record IndicatorSuggestion(String code, String name, String aggregationType,
        String measureField, String expression, double confidence, String reasoning) {}
    record SqlReviewResult(boolean approved, List<String> suggestions, List<String> warnings) {}
    record TemplateSuggestion(String templateCode, double confidence, String reasoning,
        Map<String, String> suggestedFieldMapping) {}
    record TableSchemaInfo(String tableName, List<ColumnInfo> columns) {}
    record ColumnInfo(String name, String type, boolean nullable) {}
    record UploadedFileInfo(String fileName, String fileType, List<String> headers,
        List<List<String>> sampleRows) {}
}
