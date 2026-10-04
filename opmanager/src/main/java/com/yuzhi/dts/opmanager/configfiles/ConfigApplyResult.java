package com.yuzhi.dts.opmanager.configfiles;

public record ConfigApplyResult(boolean changed, String path, ConfigApplyAction action, String message, String backupPath, String writtenPath) {}
