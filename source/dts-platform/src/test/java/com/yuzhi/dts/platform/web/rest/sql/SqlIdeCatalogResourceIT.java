package com.yuzhi.dts.platform.web.rest.sql;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@WithMockUser
class SqlIdeCatalogResourceIT {

    @Autowired private MockMvc mvc;

    @Test
    void datasourcesReturnsSeededList() throws Exception {
        mvc.perform(get("/api/sql/v2/catalog/datasources"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }

    /**
     * Schemas endpoint requires an active JDBC connection to the seeded biadmin datasource.
     * In CI the biadmin host is not reachable, so we accept 200 (connected) or 502 (unreachable).
     * The primary assertion is that the endpoint does not crash with 500.
     */
    @Test
    void schemasReturnsArrayOrBadGatewayForKnownDatasource() throws Exception {
        ResultActions result = mvc.perform(get("/api/sql/v2/catalog/default/schemas"));
        int status = result.andReturn().getResponse().getStatus();
        // 200 = JDBC connected successfully; 502 = biadmin host unreachable in test env
        org.junit.jupiter.api.Assertions.assertTrue(
            status == 200 || status == 502,
            "Expected 200 or 502 but got " + status
        );
    }

    @Test
    void tablesEmptyOrBadGatewayForUnknownSchema() throws Exception {
        ResultActions result = mvc.perform(get("/api/sql/v2/catalog/default/schemas/nonexistent_xyz/tables"));
        int status = result.andReturn().getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertTrue(
            status == 200 || status == 502,
            "Expected 200 or 502 but got " + status
        );
    }

    @Test
    void columnsEmptyOrBadGatewayForUnknownTable() throws Exception {
        ResultActions result = mvc.perform(get("/api/sql/v2/catalog/default/tables/none.none/columns"));
        int status = result.andReturn().getResponse().getStatus();
        org.junit.jupiter.api.Assertions.assertTrue(
            status == 200 || status == 502,
            "Expected 200 or 502 but got " + status
        );
    }

    @Test
    void searchWithEmptyKeywordReturnsEmpty() throws Exception {
        // Empty keyword always returns empty list without JDBC — no connection needed
        mvc.perform(get("/api/sql/v2/catalog/default/search").param("q", ""))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data", hasSize(0)));
    }
}
