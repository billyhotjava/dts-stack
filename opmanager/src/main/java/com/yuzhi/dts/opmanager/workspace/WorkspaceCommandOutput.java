package com.yuzhi.dts.opmanager.workspace;

import java.util.List;

public record WorkspaceCommandOutput(List<String> command, boolean success, String message) {}
