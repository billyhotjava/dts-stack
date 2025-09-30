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
public class ApprovalResource {

    private final AtomicLong seq = new AtomicLong(1);
    private final Map<Long, Map<String, Object>> approvals = new ConcurrentHashMap<>();

    public ApprovalResource() {
        // seed a sample request
        long id = seq.getAndIncrement();
        Map<String, Object> cr = new LinkedHashMap<>();
        cr.put("id", id);
        cr.put("type", "USER_ROLE_ASSIGN");
        cr.put("status", "PENDING");
        cr.put("applicant", "alice");
        cr.put("requestedAt", Instant.now().toString());
        cr.put("detail", Map.of("user", "alice", "role", "DATA_VIEWER"));
        approvals.put(id, cr);
    }

    // list and detail for platform admin page
    @GetMapping("/api/approval-requests")
    public ApiResponse<List<Map<String, Object>>> list() { return ApiResponses.ok(new ArrayList<>(approvals.values())); }

    @GetMapping("/api/approval-requests/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detail(@PathVariable long id) {
        Map<String, Object> v = approvals.get(id);
        return v == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(ApiResponses.ok(v));
    }

    // keycloak-themed actions used by front-end services
    @PostMapping("/api/keycloak/approvals/{id}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(@PathVariable long id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> v = approvals.get(id);
        if (v == null) return ResponseEntity.notFound().build();
        v.put("status", "APPROVED");
        v.put("decidedAt", Instant.now().toString());
        v.put("decisionNote", body != null ? body.getOrDefault("note", "Approved") : "Approved");
        return ResponseEntity.ok(ApiResponses.ok(null));
    }

    @PostMapping("/api/keycloak/approvals/{id}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(@PathVariable long id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> v = approvals.get(id);
        if (v == null) return ResponseEntity.notFound().build();
        v.put("status", "REJECTED");
        v.put("decidedAt", Instant.now().toString());
        v.put("decisionNote", body != null ? body.getOrDefault("note", "Rejected") : "Rejected");
        return ResponseEntity.ok(ApiResponses.ok(null));
    }

    @PostMapping("/api/keycloak/approvals/{id}/process")
    public ResponseEntity<ApiResponse<Void>> process(@PathVariable long id) {
        Map<String, Object> v = approvals.get(id);
        if (v == null) return ResponseEntity.notFound().build();
        v.put("processedAt", Instant.now().toString());
        return ResponseEntity.ok(ApiResponses.ok(null));
    }

    // user sync simulation
    @PostMapping("/api/keycloak/user-sync/process/{id}")
    public ResponseEntity<ApiResponse<Void>> userSync(@PathVariable long id) {
        return ResponseEntity.ok(ApiResponses.ok(null));
    }
}
