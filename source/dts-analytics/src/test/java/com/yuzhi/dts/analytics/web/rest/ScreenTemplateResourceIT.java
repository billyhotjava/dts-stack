package com.yuzhi.dts.analytics.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ScreenTemplateResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AnalyticsUserRepository userRepository;

    @Autowired
    private AnalyticsSessionService sessionService;

    @Test
    void screenTemplateShouldSupportCreateAndListingUpdate() throws Exception {
        Cookie sessionCookie = authenticate();

        MvcResult createdResult = mockMvc.perform(post("/api/screen-templates")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Retail Wallboard",
                                  "description": "for retail ops",
                                  "category": "custom",
                                  "thumbnail": "🧩",
                                  "tags": ["retail", "ops"],
                                  "visibilityScope": "team",
                                  "listed": false,
                                  "config": {
                                    "schemaVersion": 2,
                                    "width": 1920,
                                    "height": 1080,
                                    "backgroundColor": "#0f172a",
                                    "components": [],
                                    "globalVariables": []
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Retail Wallboard"))
                .andExpect(jsonPath("$.listed").value(false))
                .andReturn();

        JsonNode created = objectMapper.readTree(createdResult.getResponse().getContentAsString());
        String templateId = created.path("id").asText();

        mockMvc.perform(put("/api/screen-templates/{id}/listing", templateId)
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"listed": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(templateId))
                .andExpect(jsonPath("$.listed").value(true));

        mockMvc.perform(get("/api/screen-templates").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(templateId));
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
