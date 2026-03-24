package com.yuzhi.dts.analytics.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.analytics.domain.AnalyticsScreenAcl;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenAclRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsUserRepository;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import jakarta.servlet.http.Cookie;
import java.util.List;
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
    private AnalyticsScreenRepository screenRepository;

    @Autowired
    private AnalyticsScreenAclRepository screenAclRepository;

    @Autowired
    private AnalyticsSessionService sessionService;

    private static final List<String> DEFAULT_SCREEN_READ_ROLES = List.of(
            "ROLE_DEPT_LEADER",
            "ROLE_DEPT_DATA_OWNER",
            "ROLE_INST_DATA_OWNER",
            "ROLE_INST_LEADER");

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
    void createScreenShouldGrantDefaultReadAclToLeadershipAndDataOwnerRoles() throws Exception {
        Cookie sessionCookie = authenticate("creator@example.com", false);

        MvcResult createdResult = mockMvc.perform(post("/api/screens")
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schemaVersion": 2,
                                  "name": "默认可见性测试大屏",
                                  "components": []
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        long screenId = objectMapper.readTree(createdResult.getResponse().getContentAsString()).path("id").asLong();

        MvcResult aclResult = mockMvc.perform(get("/api/screens/{id}/acl", screenId).cookie(sessionCookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode acl = objectMapper.readTree(aclResult.getResponse().getContentAsString());
        for (String role : DEFAULT_SCREEN_READ_ROLES) {
            assertHasAclEntry(acl, "ROLE", role, "READ");
        }
    }

    @Test
    void listScreensShouldBackfillDefaultReadAclForHistoricalScreens() throws Exception {
        Cookie creatorCookie = authenticate("history-creator@example.com", false);
        Cookie leaderCookie = authenticate("dept-leader@example.com", false);
        AnalyticsUser creator = userRepository.findByEmailIgnoreCase("history-creator@example.com").orElseThrow();
        AnalyticsScreen screen = new AnalyticsScreen();
        screen.setName("历史大屏可见性修复");
        screen.setWidth(1920);
        screen.setHeight(1080);
        screen.setBackgroundColor("#0d1b2a");
        screen.setComponentsJson("[]");
        screen.setVariablesJson("[]");
        screen.setPagesJson("[]");
        screen.setCreatorId(creator.getId());
        screen.setArchived(false);
        screen = screenRepository.save(screen);
        long screenId = screen.getId();

        AnalyticsScreenAcl creatorManage = new AnalyticsScreenAcl();
        creatorManage.setScreenId(screenId);
        creatorManage.setSubjectType("USER");
        creatorManage.setSubjectId(String.valueOf(creator.getId()));
        creatorManage.setPerm("MANAGE");
        creatorManage.setCreatorId(creator.getId());
        screenAclRepository.save(creatorManage);

        MvcResult listResult = mockMvc.perform(get("/api/screens")
                        .cookie(leaderCookie)
                        .header("X-DTS-Roles", "ROLE_DEPT_LEADER"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode list = objectMapper.readTree(listResult.getResponse().getContentAsString());
        boolean found = false;
        for (JsonNode item : list) {
            if (item.path("id").asLong() == screenId) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new AssertionError("expected historical screen to become visible to ROLE_DEPT_LEADER after backfill");
        }

        MvcResult aclResult = mockMvc.perform(get("/api/screens/{id}/acl", screenId).cookie(creatorCookie))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode acl = objectMapper.readTree(aclResult.getResponse().getContentAsString());
        assertHasAclEntry(acl, "ROLE", "ROLE_DEPT_LEADER", "READ");
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

    private void assertHasAclEntry(JsonNode acl, String subjectType, String subjectId, String perm) {
        for (JsonNode item : acl) {
            if (subjectType.equals(item.path("subjectType").asText())
                    && subjectId.equals(item.path("subjectId").asText())
                    && perm.equals(item.path("perm").asText())) {
                return;
            }
        }
        throw new AssertionError("expected acl entry " + subjectType + "/" + subjectId + "/" + perm);
    }
}
