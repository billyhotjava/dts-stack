package com.yuzhi.dts.platform.service.governance.spi;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * No-op implementation of {@link IndicatorSuggestionProvider}.
 * Returns empty/approved results so the platform works without an LLM backend.
 * Automatically yields to any real LLM implementation registered as a Spring bean.
 */
@Service
@ConditionalOnMissingBean(IndicatorSuggestionProvider.class)
public class NoOpIndicatorSuggestionProvider implements IndicatorSuggestionProvider {

    @Override
    public List<IndicatorSuggestion> suggestFromSchema(TableSchemaInfo schema) {
        return List.of();
    }

    @Override
    public SqlReviewResult reviewSql(String sql, String indicatorCode) {
        return new SqlReviewResult(true, List.of(), List.of());
    }

    @Override
    public List<TemplateSuggestion> suggestFromUpload(UploadedFileInfo file) {
        return List.of();
    }
}
