package com.yuzhi.dts.analytics.service.analysis;

import java.util.UUID;

public interface GovernedAnalysisDatasetContractProvider {
    GovernedAnalysisDatasetContract get(UUID datasetId, int version, String checksum);
}
