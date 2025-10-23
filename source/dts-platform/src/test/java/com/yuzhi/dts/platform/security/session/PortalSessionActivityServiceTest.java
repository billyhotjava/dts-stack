package com.yuzhi.dts.platform.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.security.session.PortalSessionActivityService.ValidationResult;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PortalSessionActivityServiceTest {

    @Test
    void touchReturnsConcurrentAfterNewLogin() {
        PortalSessionActivityService service = new PortalSessionActivityService(15);
        Instant base = Instant.now();

        service.register("sysadmin", "sess-1", "token-1", base);
        service.register("sysadmin", "sess-2", "token-2", base.plusSeconds(1));

        ValidationResult former = service.touch("token-1", base.plusSeconds(2));
        ValidationResult current = service.touch("token-2", base.plusSeconds(3));

        assertThat(former).isEqualTo(ValidationResult.CONCURRENT);
        assertThat(current).isEqualTo(ValidationResult.ACTIVE);
    }

    @Test
    void touchReturnsExpiredAfterManualInvalidate() {
        PortalSessionActivityService service = new PortalSessionActivityService(15);
        Instant base = Instant.now();

        service.register("authadmin", "sess-1", "token-1", base);
        service.invalidate("token-1", ValidationResult.EXPIRED);

        ValidationResult result = service.touch("token-1", base.plusSeconds(5));

        assertThat(result).isEqualTo(ValidationResult.EXPIRED);
    }

    @Test
    void newRegistrationClearsPreviousRevocationForSameToken() {
        PortalSessionActivityService service = new PortalSessionActivityService(15);
        Instant base = Instant.now();

        service.register("auditadmin", "sess-1", "token-1", base);
        service.invalidate("token-1", ValidationResult.CONCURRENT);

        // Re-register same token (e.g., extend flow)
        service.register("auditadmin", "sess-2", "token-1", base.plusSeconds(1));

        ValidationResult status = service.touch("token-1", base.plusSeconds(2));

        assertThat(status).isEqualTo(ValidationResult.ACTIVE);
    }
}
