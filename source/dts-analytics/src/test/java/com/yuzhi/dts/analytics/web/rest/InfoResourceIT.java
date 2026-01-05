package com.yuzhi.dts.analytics.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.analytics.DtsAnalyticsApp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = DtsAnalyticsApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InfoResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void infoShouldReturnServiceMetadata() throws Exception {
        mockMvc.perform(get("/api/info").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("dts-analytics"))
                .andExpect(jsonPath("$.version").value("0.0.1-SNAPSHOT"))
                .andExpect(jsonPath("$.environment").value("dev"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(header().exists("X-Request-Id"));
    }
}
