package com.yuzhi.dts.opmanager.workspace;

import java.util.List;

public record WorkspaceOperationResult(boolean success, String message, List<WorkspaceCommandOutput> commands) {}
