package com.yuzhi.dts.opmanager.runtime;

import java.time.Duration;
import java.util.List;

public record CommandResult(List<String> command, int exitCode, String stdout, String stderr, boolean timedOut, Duration duration) {
    public boolean success() {
        return !timedOut && exitCode == 0;
    }

    public String trimmedStdout() {
        return stdout == null ? "" : stdout.trim();
    }

    public String summary() {
        if (success()) {
            return trimmedStdout();
        }
        String error = stderr == null || stderr.isBlank() ? stdout : stderr;
        return error == null ? "" : error.trim();
    }
}
