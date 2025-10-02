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
    List<String> warnings
) {

    public HiveConnectionTestResult {
        if (warnings == null) {
            warnings = Collections.emptyList();
        }
    }

    public static HiveConnectionTestResult success(String message, long elapsedMillis, String engineVersion, String driverVersion, List<String> warnings) {
        return new HiveConnectionTestResult(true, message, elapsedMillis, engineVersion, driverVersion, warnings);
    }

    public static HiveConnectionTestResult failure(String message, long elapsedMillis) {
        return new HiveConnectionTestResult(false, message, elapsedMillis, null, null, Collections.emptyList());
    }
}

