package com.yuzhi.dts.opmanager.job;

public record JobEvent(String jobId, String timestamp, UpgradeJobState state, String message) {}
