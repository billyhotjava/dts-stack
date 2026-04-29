package com.yuzhi.dts.platform.service.infra;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HiveConnectionTestResult(
    boolean success,
    String message,
    long elapsedMillis,
    String engineVersion,
    String driverVersion,
    List<String> warnings,
    String errorType,
    String suggestion
) {

    public HiveConnectionTestResult {
        if (warnings == null) {
            warnings = Collections.emptyList();
        }
    }

    public HiveConnectionTestResult(
        boolean success,
        String message,
        long elapsedMillis,
        String engineVersion,
        String driverVersion,
        List<String> warnings
    ) {
        this(success, message, elapsedMillis, engineVersion, driverVersion, warnings, null, null);
    }

    public static HiveConnectionTestResult success(String message, long elapsedMillis, String engineVersion, String driverVersion, List<String> warnings) {
        return new HiveConnectionTestResult(true, message, elapsedMillis, engineVersion, driverVersion, warnings, null, null);
    }

    public static HiveConnectionTestResult failure(String message, long elapsedMillis) {
        return failure(message, elapsedMillis, "UNKNOWN", "查看错误摘要并确认网络、驱动、账号和目标库状态。");
    }

    public static HiveConnectionTestResult failure(String message, long elapsedMillis, String errorType, String suggestion) {
        return new HiveConnectionTestResult(false, message, elapsedMillis, null, null, Collections.emptyList(), errorType, suggestion);
    }
}
