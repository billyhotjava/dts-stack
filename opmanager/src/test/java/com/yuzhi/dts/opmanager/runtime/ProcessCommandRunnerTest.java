package com.yuzhi.dts.opmanager.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessCommandRunnerTest {

    @Test
    void capturesLargeStdoutWithoutDeadlock() {
        ProcessCommandRunner runner = new ProcessCommandRunner();

        CommandResult result = runner.run(List.of("sh", "-c", "i=0; while [ $i -lt 20000 ]; do echo line-$i; i=$((i+1)); done"), Duration.ofSeconds(5));

        assertThat(result.success()).isTrue();
        assertThat(result.stdout()).contains("line-0");
        assertThat(result.stdout()).contains("line-19999");
        assertThat(result.timedOut()).isFalse();
    }
}
