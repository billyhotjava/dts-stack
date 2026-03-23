package com.yuzhi.dts.analytics.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class ScreenResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AnalyticsUserRepository userRepository;

    @Autowired
    private AnalyticsSessionService sessionService;

    @Test
    void screenShouldPersistPagesAndCarouselForDraftAndPublicViews() throws Exception {
        Cookie sessionCookie = authenticate();

        MvcResult createdResult = mockMvc.perform(post("/api/screens")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schemaVersion": 2,
                                  "name": "项目管理指挥大屏",
                                  "description": "测试多页大屏",
                                  "width": 1920,
                                  "height": 1080,
                                  "backgroundColor": "#08121f",
                                  "theme": "legacy-dark",
                                  "components": [],
                                  "globalVariables": [
                                    { "key": "majorProjectId", "label": "项目", "type": "string", "defaultValue": "" }
                                  ],
                                  "pages": [
                                    {
                                      "id": "page-overview",
                                      "name": "总体态势",
                                      "backgroundColor": "#08121f",
                                      "components": [
                                        {
                                          "id": "overview-title",
                                          "type": "title",
                                          "name": "总体态势标题",
                                          "x": 40,
                                          "y": 24,
                                          "width": 420,
                                          "height": 48,
                                          "zIndex": 1,
                                          "locked": false,
                                          "visible": true,
                                          "config": { "text": "总体态势", "fontSize": 36, "color": "#d7ecff" }
                                        }
                                      ]
                                    },
                                    {
                                      "id": "page-risk",
                                      "name": "风险与变更",
                                      "backgroundColor": "#08121f",
                                      "components": [
                                        {
                                          "id": "risk-title",
                                          "type": "title",
                                          "name": "风险页标题",
                                          "x": 40,
                                          "y": 24,
                                          "width": 420,
                                          "height": 48,
                                          "zIndex": 1,
                                          "locked": false,
                                          "visible": true,
                                          "config": { "text": "风险与变更", "fontSize": 36, "color": "#d7ecff" }
                                        }
                                      ]
                                    }
                                  ],
                                  "carouselConfig": {
                                    "enabled": true,
                                    "intervalSeconds": 15,
                                    "transition": "fade",
                                    "transitionDuration": 800,
                                    "loop": true
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pages[0].components[0].id").value("overview-title"))
                .andExpect(jsonPath("$.pages[1].components[0].id").value("risk-title"))
                .andExpect(jsonPath("$.carouselConfig.enabled").value(true))
                .andReturn();

        JsonNode created = objectMapper.readTree(createdResult.getResponse().getContentAsString());
        long screenId = created.path("id").asLong();

        mockMvc.perform(get("/api/screens/{id}", screenId).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pages.length()").value(2))
                .andExpect(jsonPath("$.pages[0].name").value("总体态势"))
                .andExpect(jsonPath("$.pages[1].name").value("风险与变更"))
                .andExpect(jsonPath("$.carouselConfig.transition").value("fade"));

        mockMvc.perform(post("/api/screens/{id}/publish", screenId).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screen.pages[0].components[0].id").value("overview-title"))
                .andExpect(jsonPath("$.screen.carouselConfig.intervalSeconds").value(15));

        MvcResult publicLinkResult = mockMvc.perform(post("/api/screens/{id}/public_link", screenId)
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();

        String publicUuid = objectMapper.readTree(publicLinkResult.getResponse().getContentAsString()).path("uuid").asText();

        mockMvc.perform(get("/api/public/screen/{uuid}", publicUuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pages.length()").value(2))
                .andExpect(jsonPath("$.pages[1].components[0].id").value("risk-title"))
                .andExpect(jsonPath("$.carouselConfig.enabled").value(true));
    }

    @Test
    void publicScreenProjectCockpitProxyShouldServeAnonymousRequests() throws Exception {
        Cookie sessionCookie = authenticate();

        MvcResult createdResult = mockMvc.perform(post("/api/screens")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schemaVersion": 2,
                                  "name": "项目管理指挥大屏",
                                  "description": "测试公共专题代理",
                                  "width": 1920,
                                  "height": 1080,
                                  "backgroundColor": "#08121f",
                                  "theme": "legacy-dark",
                                  "components": []
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        long screenId = objectMapper.readTree(createdResult.getResponse().getContentAsString()).path("id").asLong();

        mockMvc.perform(post("/api/screens/{id}/publish", screenId).cookie(sessionCookie))
                .andExpect(status().isOk());

        MvcResult publicLinkResult = mockMvc.perform(post("/api/screens/{id}/public_link", screenId)
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();

        String publicUuid = objectMapper.readTree(publicLinkResult.getResponse().getContentAsString()).path("uuid").asText();

        mockMvc.perform(get("/api/public/screen/{uuid}/project-cockpit/overview", publicUuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screenKey").value("overview"))
                .andExpect(jsonPath("$.screenTitle").value("总体态势"))
                .andExpect(jsonPath("$.filters").exists());
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
