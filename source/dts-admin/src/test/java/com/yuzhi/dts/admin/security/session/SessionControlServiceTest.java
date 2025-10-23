package com.yuzhi.dts.admin.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.admin.security.session.SessionControlService.ValidationResult;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SessionControlServiceTest {

    private final SessionControlService service = new SessionControlService(30);

    @Test
    void touchReturnsActiveWhenTokenIsKnown() {
        Instant now = Instant.now();
        service.register("sysadmin", "state-1", "token-1", now);

        ValidationResult result = service.touch("sysadmin", "state-1", "token-1", now.plusSeconds(5));

        assertThat(result).isEqualTo(ValidationResult.ACTIVE);
    }

    @Test
    void touchReturnsConcurrentWhenSessionStateDiffers() {
        Instant base = Instant.now();
        service.register("sysadmin", "state-1", "token-1", base);
        // New login from another browser issues a different session state/token
        service.register("sysadmin", "state-2", "token-2", base.plusSeconds(1));

        ValidationResult oldSession = service.touch("sysadmin", "state-1", "token-1", base.plusSeconds(2));
        ValidationResult newSession = service.touch("sysadmin", "state-2", "token-2", base.plusSeconds(3));

        assertThat(oldSession).isEqualTo(ValidationResult.CONCURRENT);
        assertThat(newSession).isEqualTo(ValidationResult.ACTIVE);
    }
}
