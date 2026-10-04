package com.yuzhi.dts.copilot.analytics.service.elt;

import java.time.Instant;

public interface EltSyncJob {

    String getTargetTable();

    int sync(Instant lastWatermark, String batchId) throws Exception;
}
