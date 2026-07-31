package com.yuzhi.dts.platform.service.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class RollbackConfirmationTokenServiceTest {

    @Test
    void issuedTokenIsRequestBoundAndCanBeConsumedOnlyOnce() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-31T00:00:00Z"));
        RollbackConfirmationTokenService service = service(clock);
        Map<String, Object> request = Map.of(
            "level", 2,
            "scope", "task",
            "taskId", 7,
            "tables", List.of("ods_b", "ods_a"),
            "rebuildDbt", true
        );

        RollbackConfirmationTokenService.IssuedConfirmation issued = service.issue(
            plan(request),
            "MODAL",
            "确认重建 ODS 表",
            "alice",
            null
        );

        assertThat(issued.token()).isNotBlank();
        assertThat(issued.confirmationType()).isEqualTo("MODAL");
        assertThatCode(() ->
            service.consume(plan(request), issued.token(), "MODAL", null, "alice")
        ).doesNotThrowAnyException();
        assertConflict(() ->
            service.consume(plan(request), issued.token(), "MODAL", null, "alice")
        );
    }

    @Test
    void changedRequestConsumesAndRejectsTheIssuedToken() {
        RollbackConfirmationTokenService service = service(
            new MutableClock(Instant.parse("2026-07-31T00:00:00Z"))
        );
        Map<String, Object> analyzed = Map.of("level", 1, "scope", "task", "taskId", 7);
        RollbackConfirmationTokenService.IssuedConfirmation issued = service.issue(
            plan(analyzed),
            "MODAL",
            "确认清空数据",
            "alice",
            null
        );

        assertConflict(() ->
            service.consume(
                plan(Map.of("level", 1, "scope", "task", "taskId", 8)),
                issued.token(),
                "MODAL",
                "确认清空数据",
                "alice"
            )
        );
        assertConflict(() ->
            service.consume(plan(analyzed), issued.token(), "MODAL", "确认清空数据", "alice")
        );
    }

    @Test
    void typedConfirmationRequiresExactTextAndKnownType() {
        RollbackConfirmationTokenService service = service(
            new MutableClock(Instant.parse("2026-07-31T00:00:00Z"))
        );
        Map<String, Object> request = Map.of(
            "level", 3,
            "scope", "datasource",
            "dataSourceId", "11111111-2222-3333-4444-555555555555"
        );
        RollbackConfirmationTokenService.IssuedConfirmation issued = service.issue(
            plan(request),
            "TYPE_TEXT",
            "ROLLBACK source-1",
            "alice",
            null
        );

        assertConflict(() ->
            service.consume(plan(request), issued.token(), "TYPE_TEXT", "ROLLBACK source-2", "alice")
        );
        assertThatThrownBy(() -> service.issue(plan(request), "CHECKBOX", "confirm", "alice", null))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY)
            );
        assertThatThrownBy(() -> service.issue(plan(request), "TYPE_TEXT", "", "alice", null))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY)
            );
    }

    @Test
    void expiredTokenFailsClosed() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-31T00:00:00Z"));
        RollbackConfirmationTokenService service = service(clock);
        Map<String, Object> request = Map.of("level", 1, "scope", "task", "taskId", 7);
        RollbackConfirmationTokenService.IssuedConfirmation issued = service.issue(
            plan(request),
            "MODAL",
            "确认清空数据",
            "alice",
            null
        );
        clock.advance(Duration.ofMinutes(6));

        assertConflict(() ->
            service.consume(plan(request), issued.token(), "MODAL", "确认清空数据", "alice")
        );
    }

    private RollbackCommand plan(Map<String, Object> request) {
        return RollbackCommand.executionPlan(request, false);
    }

    private RollbackConfirmationTokenService service(Clock clock) {
        return new RollbackConfirmationTokenService(
            clock,
            RollbackConfirmationTokenService.DEFAULT_TTL,
            new SecureRandom()
        );
    }

    private void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action)
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
            );
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
