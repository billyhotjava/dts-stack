package com.yuzhi.dts.opmanager.runtime;

import java.time.Duration;
import java.util.List;

@FunctionalInterface
public interface CommandRunner {
    CommandResult run(List<String> command, Duration timeout);
}
