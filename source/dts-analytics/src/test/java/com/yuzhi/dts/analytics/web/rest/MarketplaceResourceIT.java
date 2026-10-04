package com.yuzhi.dts.analytics.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsScreenTemplate;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenTemplateRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsUserRepository;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import jakarta.servlet.http.Cookie;
import java.util.stream.StreamSupport;
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
class MarketplaceResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AnalyticsScreenTemplateRepository screenTemplateRepository;

    @Autowired
    private AnalyticsUserRepository userRepository;

    @Autowired
    private AnalyticsSessionService sessionService;

    @Test
    void marketplaceShouldExposeComponentsAndPersistInstallState() throws Exception {
        Cookie sessionCookie = authenticate();
        String componentId = "demo-stat-pack:kpi-card-pro";

        mockMvc.perform(get("/api/marketplace/components").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isString())
                .andExpect(jsonPath("$[0].installed").value(false));

        mockMvc.perform(post("/api/marketplace/components/{id}/install", componentId)
                        .cookie(sessionCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(componentId))
                .andExpect(jsonPath("$.installed").value(true));

        MvcResult listAfterInstall = mockMvc.perform(get("/api/marketplace/components").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode items = objectMapper.readTree(listAfterInstall.getResponse().getContentAsString());
        assertThat(StreamSupport.stream(items.spliterator(), false)
                        .anyMatch(item ->
                                componentId.equals(item.path("id").asText())
                                        && item.path("installed").asBoolean(false)))
                .isTrue();

        MvcResult pluginResponse = mockMvc.perform(get("/api/screen-plugins").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode plugins = objectMapper.readTree(pluginResponse.getResponse().getContentAsString());
        assertThat(StreamSupport.stream(plugins.spliterator(), false)
                        .flatMap(plugin -> StreamSupport.stream(plugin.path("components").spliterator(), false))
                        .anyMatch(component ->
                                "kpi-card-pro".equals(component.path("id").asText())
                                        && component.path("installed").asBoolean(false)))
                .isTrue();
    }

    @Test
    void marketplaceShouldListTemplatesAndCloneOnInstall() throws Exception {
        Cookie sessionCookie = authenticate();
        AnalyticsScreenTemplate template = new AnalyticsScreenTemplate();
        template.setName("Retail Ops");
        template.setDescription("Retail ops template");
        template.setCategory("retail");
        template.setThumbnail("R");
        template.setTagsJson("[\"retail\",\"ops\"]");
        template.setWidth(1920);
        template.setHeight(1080);
        template.setBackgroundColor("#0f172a");
        template.setTheme("midnight");
        template.setComponentsJson("[]");
        template.setVariablesJson("[]");
        template.setVisibilityScope("global");
        template.setListed(true);
        template.setCreatorId(1L);
        template.setArchived(false);
        template = screenTemplateRepository.save(template);
        Long sourceTemplateId = template.getId();

        mockMvc.perform(get("/api/marketplace/templates").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(String.valueOf(sourceTemplateId)));

        long beforeCount = screenTemplateRepository.count();

        mockMvc.perform(post("/api/marketplace/templates/{id}/install", sourceTemplateId)
                        .cookie(sessionCookie)
                        .header("X-DTS-Dept", "retail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installed").value(true))
                .andExpect(jsonPath("$.sourceTemplateId").value(sourceTemplateId))
                .andExpect(jsonPath("$.name").value("Retail Ops"));

        assertThat(screenTemplateRepository.count()).isEqualTo(beforeCount + 1);
        AnalyticsScreenTemplate installedTemplate = screenTemplateRepository.findAll().stream()
                .filter(row -> sourceTemplateId.equals(row.getSourceTemplateId()))
                .findFirst()
                .orElseThrow();
        assertThat(installedTemplate.getCreatorId()).isEqualTo(1L);
        assertThat(installedTemplate.getVisibilityScope()).isEqualTo("team");
        assertThat(installedTemplate.getOwnerDept()).isEqualTo("retail");
    }

    private Cookie authenticate() throws Exception {
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
