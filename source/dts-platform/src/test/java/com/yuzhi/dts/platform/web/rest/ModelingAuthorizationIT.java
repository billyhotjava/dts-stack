package com.yuzhi.dts.platform.web.rest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@IntegrationTest
@Transactional
class ModelingAuthorizationIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "sprint67-reader", authorities = AuthoritiesConstants.EMPLOYEE)
    void readOnlyUserCanReadCanonicalModelsButCannotWriteOrExecuteMigration() throws Exception {
        mockMvc.perform(get("/api/modeling/model-specs")).andExpect(status().isOk());

        mockMvc
            .perform(
                post("/api/modeling/model-specs")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isForbidden());

        mockMvc
            .perform(get("/api/modeling/migrations/legacy-objects/dry-run"))
            .andExpect(status().isForbidden());
    }
}
