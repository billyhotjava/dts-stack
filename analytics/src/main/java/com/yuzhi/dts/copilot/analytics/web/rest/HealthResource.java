package com.yuzhi.dts.copilot.analytics.web.rest;

import com.yuzhi.dts.copilot.analytics.web.support.RequestContextUtils;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthResource {

    private final DataSource dataSource;

    public HealthResource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> checks = new LinkedHashMap<>();
        boolean appDbUp = probeAppDatabase(checks);

        String status = appDbUp ? "UP" : "DEGRADED";
        HttpStatus httpStatus = appDbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("timestamp", OffsetDateTime.now().toString());
        payload.put("requestId", resolveRequestId());
        payload.put("checks", checks);
        return ResponseEntity.status(httpStatus).body(payload);
    }

    private boolean probeAppDatabase(Map<String, Object> checks) {
        long started = System.nanoTime();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("select 1");
                ResultSet ignored = statement.executeQuery()) {
            checks.put("appDatabase", Map.of(
                    "status", "UP",
                    "latencyMs", (System.nanoTime() - started) / 1_000_000));
            return true;
        } catch (Exception e) {
            checks.put("appDatabase", Map.of(
                    "status", "DOWN",
                    "latencyMs", (System.nanoTime() - started) / 1_000_000,
                    "error", summarizeError(e)));
            return false;
        }
    }

    private String summarizeError(Exception e) {
        if (e == null) {
            return "unknown";
        }
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return message;
    }

    private String resolveRequestId() {
        String requestId = RequestContextUtils.resolveRequestId();
        if (requestId == null || requestId.isBlank()) {
            return "unknown";
        }
        return requestId;
    }
}
