package com.yuzhi.dts.analytics.web.rest;

import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsUserRepository;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/permissions")
public class PermissionsResource {

    private final AnalyticsSessionService sessionService;
    private final AnalyticsUserRepository userRepository;

    public PermissionsResource(AnalyticsSessionService sessionService, AnalyticsUserRepository userRepository) {
        this.sessionService = sessionService;
        this.userRepository = userRepository;
    }

    @GetMapping(path = "/group", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> groups(HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }

        long userCount = userRepository.count();
        long adminCount = userRepository.countBySuperuserTrue();

        return ResponseEntity.ok(List.of(
                Map.of("id", 2, "name", "Administrators", "member_count", adminCount),
                Map.of("id", 1, "name", "All Users", "member_count", userCount)));
    }

    @GetMapping(path = "/group/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> groupDetails(@PathVariable("id") int id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }
        if (id == 1) {
            return ResponseEntity.ok(Map.of("id", 1, "name", "All Users"));
        }
        if (id == 2) {
            return ResponseEntity.ok(Map.of("id", 2, "name", "Administrators"));
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping(path = "/graph", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> graph(HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("revision", 0);
        response.put("groups", Map.of("1", Map.of(), "2", Map.of()));
        return ResponseEntity.ok(response);
    }
}
