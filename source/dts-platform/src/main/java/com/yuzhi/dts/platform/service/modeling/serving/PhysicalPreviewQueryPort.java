package com.yuzhi.dts.platform.service.modeling.serving;

import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.QueryResult;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.RelationEvidence;
import java.time.Instant;
import java.util.List;

public interface PhysicalPreviewQueryPort {
    /** Re-observes the physical relation under a database-side relation lock without selecting any row. */
    Instant verifyStructure(RelationEvidence evidence);

    QueryResult query(RelationEvidence evidence, List<String> selectedColumns, Integer limit);

    default QueryResult query(RelationEvidence evidence, Integer limit) {
        return query(evidence, evidence.columns().stream().map(column -> column.name()).toList(), limit);
    }
}
