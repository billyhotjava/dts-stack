package com.yuzhi.dts.copilot.analytics.web.rest;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsLoginHistory;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsLoginHistoryRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/login-history")
@Transactional(readOnly = true)
public class LoginHistoryResource {

    private final AnalyticsSessionService sessionService;
    private final AnalyticsLoginHistoryRepository loginHistoryRepository;

    public LoginHistoryResource(AnalyticsSessionService sessionService, AnalyticsLoginHistoryRepository loginHistoryRepository) {
        this.sessionService = sessionService;
        this.loginHistoryRepository = loginHistoryRepository;
    }

    @GetMapping(path = "/current", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> current(HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }

        List<AnalyticsLoginHistory> history = loginHistoryRepository.findTop20ByUserIdOrderByLoggedInAtDesc(user.get().getId());
        List<Map<String, Object>> response = new ArrayList<>();
        for (AnalyticsLoginHistory entry : history) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", entry.getId());
            item.put("user_id", entry.getUserId());
            item.put("ip_address", entry.getIpAddress());
            item.put("user_agent", entry.getUserAgent());
            item.put("logged_in_at", entry.getLoggedInAt() == null ? null : entry.getLoggedInAt().toString());
            response.add(item);
        }
        return ResponseEntity.ok(response);
    }
}

