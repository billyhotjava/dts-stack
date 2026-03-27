package com.yuzhi.dts.platform.web.rest.development;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.IntegrationTest;
import com.yuzhi.dts.platform.domain.development.DevScriptAsset;
import com.yuzhi.dts.platform.repository.development.DevScriptAssetRepository;
import jakarta.transaction.Transactional;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@IntegrationTest
@Transactional
class ScriptStudioResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DevScriptAssetRepository assetRepository;

    @BeforeEach
    void setUp() {
        assetRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "employee", authorities = {"ROLE_EMPLOYEE"})
    void listScriptsShouldRejectNonMaintainer() throws Exception {
        mockMvc.perform(get("/api/development/scripts")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "dept-owner", authorities = {"ROLE_DEPT_DATA_OWNER"})
    void listScriptsShouldOnlyReturnVisibleDepartmentAssets() throws Exception {
        assetRepository.save(script("Patent Sync", "D1"));
        assetRepository.save(script("Finance Sync", "D2"));

        mockMvc
            .perform(get("/api/development/scripts").header("X-Active-Dept", "D1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].name").value("Patent Sync"))
            .andExpect(jsonPath("$.data[0].ownerDept").value("D1"));
    }

    @Test
    @WithMockUser(username = "dept-owner", authorities = {"ROLE_DEPT_DATA_OWNER"})
    void createScriptShouldUseActiveDeptForDepartmentScopedUser() throws Exception {
        mockMvc
            .perform(
                post("/api/development/scripts")
                    .with(csrf())
                    .header("X-Active-Dept", "D1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsBytes(
                            Map.of(
                                "name",
                                "cdc_job.py",
                                "content",
                                "print('ok')",
                                "scriptType",
                                "PYTHON",
                                "ownerDept",
                                "D2"
                            )
                        )
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ownerDept").value("D1"));
    }

    private DevScriptAsset script(String name, String ownerDept) {
        DevScriptAsset asset = new DevScriptAsset();
        asset.setName(name);
        asset.setDescription(name + " description");
        asset.setScriptType("PYTHON");
        asset.setStatus("DRAFT");
        asset.setLatestVersionNo(1);
        asset.setOwnerDept(ownerDept);
        asset.setEnabled(Boolean.TRUE);
        return asset;
    }
}
