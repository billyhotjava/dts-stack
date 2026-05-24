package com.yuzhi.dts.admin.service.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AuditActionCatalogLiquibaseSeedTest {

    @Test
    void platformAuthLoginAndLogoutActionsAreSeededInDbCatalog() throws Exception {
        URL resource = Thread
            .currentThread()
            .getContextClassLoader()
            .getResource("config/liquibase/changelog/20260523-01_audit_action_catalog.xml");

        assertThat(resource).isNotNull();
        String changelog = java.nio.file.Files.readString(Path.of(resource.toURI()), StandardCharsets.UTF_8);
        assertThat(changelog).contains(
            "20260524-01-audit-action-catalog-platform-auth-seed",
            "'platform', 'platform.auth', '业务端认证'",
            "'platform', 'ADMIN_AUTH_PLATFORM_LOGIN', 'platform.auth', '业务端认证'",
            "'platform', 'ADMIN_AUTH_PLATFORM_LOGOUT', 'platform.auth', '业务端认证'",
            "button_code = 'ADMIN_AUTH_PLATFORM_LOGIN'",
            "button_code = 'ADMIN_AUTH_PLATFORM_LOGOUT'"
        );
    }
}
