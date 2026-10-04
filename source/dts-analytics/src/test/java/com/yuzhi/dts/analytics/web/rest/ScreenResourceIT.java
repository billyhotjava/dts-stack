package com.yuzhi.dts.analytics.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
                                  "name": "项目运营管理大屏",
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
                                  "name": "项目运营管理大屏",
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

        mockMvc.perform(get("/api/public/screen/{uuid}/project-cockpit/metrics-compare", publicUuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").exists())
                .andExpect(jsonPath("$.groups").isArray())
                .andExpect(jsonPath("$.mismatchTop").isArray());

        mockMvc.perform(get("/api/public/screen/{uuid}/project-cockpit/tree", publicUuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").exists())
                .andExpect(jsonPath("$.focusProjects").isArray())
                .andExpect(jsonPath("$.focusNodes").isArray());
    }

    @Test
    void publishedOnlyScreenDirectoryShouldExcludeDraftsAndServeCurrentPublishedVersion() throws Exception {
        Cookie sessionCookie = authenticate();

        MvcResult publishedCandidate = mockMvc.perform(post("/api/screens")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "质量运营大屏",
                                  "description": "门户发布态样本",
                                  "classification": "INTERNAL",
                                  "components": []
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long publishedScreenId = objectMapper.readTree(publishedCandidate.getResponse().getContentAsString()).path("id").asLong();

        mockMvc.perform(post("/api/screens")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "仍在编辑的大屏",
                                  "description": "不得进入门户目录",
                                  "classification": "INTERNAL",
                                  "components": []
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/screens").param("publishedOnly", "true").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/screens/{id}/publish", publishedScreenId).cookie(sessionCookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/screens").param("publishedOnly", "true").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(publishedScreenId))
                .andExpect(jsonPath("$[0].name").value("质量运营大屏"))
                .andExpect(jsonPath("$[0].publishedVersionNo").value(1));

        mockMvc.perform(get("/api/screens").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/screens/{id}", publishedScreenId)
                        .param("mode", "published")
                        .param("fallbackDraft", "false")
                        .cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceMode").value("published"))
                .andExpect(jsonPath("$.publishedVersionNo").value(1));
    }

    @Test
    void forwardedPlatformIdentityShouldOverrideStaleMetabaseSessionForScreenIsolation() throws Exception {
        MvcResult aliceCreate = mockMvc.perform(withPlatformHeaders(
                        post("/api/screens")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Alice Screen",
                                          "description": "alice-owned",
                                          "components": []
                                        }
                                        """),
                        "alice",
                        "Alice",
                        "alice-id",
                        "ROLE_ANALYST"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie aliceSession = aliceCreate.getResponse().getCookie("metabase.SESSION");
        assertThat(aliceSession).isNotNull();

        mockMvc.perform(withPlatformHeaders(
                        post("/api/screens")
                                .cookie(aliceSession)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Bob Screen",
                                          "description": "bob-owned",
                                          "components": []
                                        }
                                        """),
                        "bob",
                        "Bob",
                        "bob-id",
                        "ROLE_ANALYST"))
                .andExpect(status().isOk());

        mockMvc.perform(withPlatformHeaders(get("/api/screens").cookie(aliceSession), "bob", "Bob", "bob-id", "ROLE_ANALYST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Bob Screen"))
                .andExpect(jsonPath("$[0].creatorId").isNumber())
                .andExpect(jsonPath("$[0].creatorName").value("bob"))
                .andExpect(jsonPath("$[0].isOwner").value(true));

        mockMvc.perform(withPlatformHeaders(get("/api/screens").cookie(aliceSession), "alice", "Alice", "alice-id", "ROLE_ANALYST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Alice Screen"))
                .andExpect(jsonPath("$[0].creatorId").isNumber())
                .andExpect(jsonPath("$[0].creatorName").value("alice"))
                .andExpect(jsonPath("$[0].isOwner").value(true));
    }

    private Cookie authenticate() {
        return authenticate("admin@example.com", true);
    }

    private Cookie authenticate(String email, boolean superuser) {
        AnalyticsUser admin = userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            AnalyticsUser row = new AnalyticsUser();
            row.setEmail(email);
            row.setFirstName("Admin");
            row.setLastName("User");
            row.setPasswordHash("test-hash");
            row.setSuperuser(superuser);
            row.setActive(true);
            return userRepository.save(row);
        });
        admin.setSuperuser(superuser);
        userRepository.save(admin);
        String sessionId = sessionService.createSession(admin.getId()).toString();
        return new Cookie("metabase.SESSION", sessionId);
    }

    private MockHttpServletRequestBuilder withPlatformHeaders(
            MockHttpServletRequestBuilder builder,
            String username,
            String displayName,
            String userId,
            String roles) {
        return builder
                .header("X-Forwarded-Proto", "https")
                .header("X-DTS-User", username)
                .header("X-DTS-Display-Name", displayName)
                .header("X-DTS-User-Id", userId)
                .header("X-DTS-Roles", roles);
    }

}
