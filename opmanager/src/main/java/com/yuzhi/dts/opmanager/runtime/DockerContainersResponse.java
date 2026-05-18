package com.yuzhi.dts.opmanager.runtime;

import java.util.List;

public record DockerContainersResponse(boolean available, String message, List<DockerContainer> containers) {}
