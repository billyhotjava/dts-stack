package com.yuzhi.dts.analytics.web.ui;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MetabaseUiIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rootShouldRenderMetabaseIndexHtml() throws Exception {
        mockMvc.perform(get("/").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<base href=\"/\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"_metabaseBootstrap\"")));
    }

    @Test
    void rootShouldUseForwardedPrefixAsBaseHref() throws Exception {
        mockMvc.perform(get("/").header("X-Forwarded-Prefix", "/analytics").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<base href=\"/analytics/\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("meta name=\"base-href\" content=\"/analytics/\"")));
    }

    @Test
    void sessionPropertiesShouldExposeSetupTokenAndVersion() throws Exception {
        mockMvc.perform(get("/api/session/properties").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setup-token").isNotEmpty())
                .andExpect(jsonPath("$.version.tag").value("v0.45.6"));
    }
}
