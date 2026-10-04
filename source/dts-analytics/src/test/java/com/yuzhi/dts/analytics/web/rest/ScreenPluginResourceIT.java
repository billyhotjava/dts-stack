package com.yuzhi.dts.analytics.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsUserRepository;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ScreenPluginResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AnalyticsUserRepository userRepository;

    @Autowired
    private AnalyticsSessionService sessionService;

    @Test
    void screenPluginsShouldReflectMarketplaceInstallationState() throws Exception {
        Cookie sessionCookie = authenticate();

        mockMvc.perform(get("/api/screen-plugins").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].components[0].installed").value(false));

        mockMvc.perform(post("/api/marketplace/components/{id}/install", "demo-stat-pack:kpi-card-pro")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/screen-plugins").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("demo-stat-pack"))
                .andExpect(jsonPath("$[0].components[0].id").value("kpi-card-pro"))
                .andExpect(jsonPath("$[0].components[0].installed").value(true));
    }

    private Cookie authenticate() {
        AnalyticsUser admin = userRepository.findByEmailIgnoreCase("admin@example.com").orElseGet(() -> {
            AnalyticsUser row = new AnalyticsUser();
            row.setEmail("admin@example.com");
            row.setFirstName("Admin");
            row.setLastName("User");
            row.setPasswordHash("test-hash");
            row.setSuperuser(true);
            row.setActive(true);
            return userRepository.save(row);
        });
        String sessionId = sessionService.createSession(admin.getId()).toString();
        return new Cookie("metabase.SESSION", sessionId);
    }
}
