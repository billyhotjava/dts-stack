package com.yuzhi.dts.ingestion.web.rest;

import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.ingestion.service.audit.AuditService;
import com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InfraSettingsResource.class)
@AutoConfigureMockMvc(addFilters = false)
class InfraSettingsResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IngestionSettingsService settingsService;

    @MockBean
    private AuditService auditService;

    @TestConfiguration
    static class RestTemplateTestConfig {
        @Bean
        RestTemplateBuilder restTemplateBuilder() {
            return new RestTemplateBuilder();
        }
    }

    @Test
    void getSettingsMasksSecrets() throws Exception {
        when(settingsService.getSettings("airflow"))
            .thenReturn(new IngestionSettingsService.SettingsSnapshot(
                Map.of("baseUrl", "http://localhost:8080", "password", "secret", "username", "admin")
            ));

        mockMvc.perform(get("/api/infra/settings/airflow"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.service").value("airflow"))
            .andExpect(jsonPath("$.data.settings.baseUrl").value("http://localhost:8080"))
            .andExpect(jsonPath("$.data.settings.password").value("******"));
    }

    @Test
    void upsertSettingsStoresPayloadAndMasksSecrets() throws Exception {
        when(settingsService.getSettings("airflow"))
            .thenReturn(new IngestionSettingsService.SettingsSnapshot(Map.of("baseUrl", "http://old")));

        mockMvc.perform(post("/api/infra/settings/airflow")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"baseUrl\":\"http://new\",\"password\":\"secret\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data.settings.baseUrl").value("http://new"))
            .andExpect(jsonPath("$.data.settings.password").value("******"));

        verify(settingsService).upsertSettings(eq("airflow"), anyMap(), anyString());
    }
}
