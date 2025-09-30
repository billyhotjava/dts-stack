package com.yuzhi.dts.platform.web.rest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogResource {

    private final Map<Long, Map<String, Object>> store = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ApiResponses.ok(new ArrayList<>(store.values()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable long id) {
        Map<String, Object> v = store.get(id);
        if (v == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(ApiResponses.ok(v));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> payload) {
        long id = seq.getAndIncrement();
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", id);
        v.put("action", payload.getOrDefault("action", "UNKNOWN"));
        v.put("target", payload.getOrDefault("target", ""));
        v.put("actor", payload.getOrDefault("actor", "system"));
        v.put("details", payload.getOrDefault("details", ""));
        v.put("at", Instant.now().toString());
        v.put("result", payload.getOrDefault("result", "SUCCESS"));
        store.put(id, v);
        return ApiResponses.ok(v);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(@PathVariable long id, @RequestBody Map<String, Object> payload) {
        Map<String, Object> v = store.get(id);
        if (v == null) return ResponseEntity.notFound().build();
        v.putAll(payload);
        v.put("id", id);
        return ResponseEntity.ok(ApiResponses.ok(v));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable long id) {
        store.remove(id);
        return ResponseEntity.ok(ApiResponses.ok(null));
    }
}
