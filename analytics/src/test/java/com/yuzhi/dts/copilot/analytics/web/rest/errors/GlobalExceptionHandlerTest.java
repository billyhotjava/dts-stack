package com.yuzhi.dts.copilot.analytics.web.rest.errors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void deleteDatabaseConflictReturnsPreciseError() throws Exception {
        mockMvc.perform(delete("/api/database/5").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DB_DELETE_CONFLICT"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.message").value("Database still has related metadata and cannot be deleted directly"))
                .andExpect(jsonPath("$.path").value("/api/database/5"));
    }

    @RestController
    private static class ThrowingController {

        @DeleteMapping("/api/database/{id}")
        void delete(@PathVariable("id") long id) {
            throw new DataIntegrityViolationException("violates foreign key constraint fk_table_database");
        }
    }
}
