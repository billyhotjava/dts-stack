package com.yuzhi.dts.opmanager.workspace;

import java.util.List;

public record WorkspaceStatus(
    String packageRoot,
    String imagesDir,
    String stackDir,
    String miscDir,
    boolean packageRootExists,
    boolean imagesDirExists,
    boolean stackDirExists,
    boolean miscDirExists,
    List<WorkspaceImage> images
) {}
