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

    @Test
    void schemasReturnsArrayForKnownDatasource() throws Exception {
        mvc.perform(get("/api/sql/v2/catalog/default/schemas"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data").exists());
    }

    @Test
    void tablesEmptyForUnknownSchema() throws Exception {
        mvc.perform(get("/api/sql/v2/catalog/default/schemas/nonexistent_xyz/tables"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void columnsEmptyForUnknownTable() throws Exception {
        mvc.perform(get("/api/sql/v2/catalog/default/tables/none.none/columns"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void searchWithEmptyKeywordReturnsEmpty() throws Exception {
        mvc.perform(get("/api/sql/v2/catalog/default/search").param("q", ""))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.data", hasSize(0)));
    }
}
