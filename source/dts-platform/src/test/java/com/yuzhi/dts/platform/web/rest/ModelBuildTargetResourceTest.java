package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.etl.DbtConfigService;
import com.yuzhi.dts.platform.service.etl.DbtConfigService.ModelBuildTargetView;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ModelBuildTargetResourceTest.Config.class)
class ModelBuildTargetResourceTest {

    @Autowired
    private ModelBuildTargetResource resource;

    @Autowired
    private DbtConfigService config;

    @BeforeEach
    void setUp() {
        reset(config);
    }

    @Test
    void anonymousReadsAreRejectedBeforeAccessingConfiguration() {
        assertThatThrownBy(resource::get).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(config);
    }

    @Test
    @WithMockUser
    void authenticatedReadUsesOnlyTheReadonlyProjection() throws Exception {
        var target = new ModelBuildTargetView(
            false, UUID.randomUUID(), "目标数仓", "warehouse", "dwd",
            "DBT_TARGET_DEFAULT_LAKE_MISMATCH", "请联系系统管理员核对运行配置"
        );
        when(config.inspectModelBuildTarget()).thenReturn(target);

        var response = resource.get();

        assertThat(response.getData()).isEqualTo(target);
        assertThat(new ObjectMapper().writeValueAsString(response))
            .contains("DBT_TARGET_DEFAULT_LAKE_MISMATCH")
            .doesNotContain("jdbcUrl", "username", "password", "projectDir");
        verify(config).inspectModelBuildTarget();
        verifyNoMoreInteractions(config);
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        DbtConfigService config() {
            return mock(DbtConfigService.class);
        }

        @Bean
        ModelBuildTargetResource resource(DbtConfigService config) {
            return new ModelBuildTargetResource(config);
        }
    }
}
