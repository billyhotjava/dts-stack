package com.yuzhi.dts.platform.web.rest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
class DimensionModelAuthorizationIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "reader", authorities = AuthoritiesConstants.EMPLOYEE)
    void readOnlyUserCannotCreateOrRecoverDimensionModelOperations() throws Exception {
        mockMvc
            .perform(
                post("/api/modeling/model-specs/dimension")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isForbidden());

        mockMvc
            .perform(
                get(
                    "/api/modeling/model-specs/dimension/operations/{operationId}",
                    "50000000-0000-0000-0000-000000000001"
                )
            )
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "maintainer", authorities = AuthoritiesConstants.INST_DATA_OWNER)
    void modelingMaintainerReachesTheStrictDecoderRatherThanBeingRejectedByAuthorization() throws Exception {
        mockMvc
            .perform(
                post("/api/modeling/model-specs/dimension")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("DIMENSION_MODEL_OPERATION_ID_INVALID"));
    }
}
