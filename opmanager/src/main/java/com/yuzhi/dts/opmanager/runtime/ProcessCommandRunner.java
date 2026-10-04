package com.yuzhi.dts.opmanager.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

@Component
public class ProcessCommandRunner implements CommandRunner {

    @Override
    public CommandResult run(List<String> command, Duration timeout) {
        Instant start = Instant.now();
        Process process = null;
        try {
            process = new ProcessBuilder(command).start();
            CompletableFuture<String> stdout = readAsync(process.getInputStream());
            CompletableFuture<String> stderr = readAsync(process.getErrorStream());
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(1, TimeUnit.SECONDS);
                return new CommandResult(command, -1, readFuture(stdout), readFuture(stderr), true, Duration.between(start, Instant.now()));
            }
            return new CommandResult(command, process.exitValue(), readFuture(stdout), readFuture(stderr), false, Duration.between(start, Instant.now()));
        } catch (IOException e) {
            return new CommandResult(command, 127, "", e.getMessage(), false, Duration.between(start, Instant.now()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return new CommandResult(command, -1, "", "interrupted", true, Duration.between(start, Instant.now()));
        }
    }

    private CompletableFuture<String> readAsync(InputStream inputStream) {
        return CompletableFuture.supplyAsync(() -> readStream(inputStream));
    }

    private String readStream(InputStream inputStream) {
        try {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return e.getMessage();
        }
    }

    private String readFuture(CompletableFuture<String> future) {
        try {
            return future.get(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrupted";
        } catch (ExecutionException | TimeoutException e) {
            return e.getMessage();
        }
    }
}
