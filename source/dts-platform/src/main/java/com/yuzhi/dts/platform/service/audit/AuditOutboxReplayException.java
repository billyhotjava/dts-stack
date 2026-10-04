package com.yuzhi.dts.platform.service.audit;

public final class AuditOutboxReplayException extends RuntimeException {

    public enum Kind {
        BAD_REQUEST,
        NOT_FOUND,
        CONFLICT,
    }

    private final String code;
    private final Kind kind;

    private AuditOutboxReplayException(String code, String message, Kind kind) {
        super(message);
        this.code = code;
        this.kind = kind;
    }

    public static AuditOutboxReplayException invalidRequest(String message) {
        return new AuditOutboxReplayException(
            "AUDIT_OUTBOX_REPLAY_REQUEST_INVALID",
            message,
            Kind.BAD_REQUEST
        );
    }

    public static AuditOutboxReplayException notFound() {
        return new AuditOutboxReplayException(
            "AUDIT_OUTBOX_NOT_FOUND",
            "Audit outbox row was not found",
            Kind.NOT_FOUND
        );
    }

    public static AuditOutboxReplayException conflict(String code, String message) {
        return new AuditOutboxReplayException(code, message, Kind.CONFLICT);
    }

    public String code() {
        return code;
    }

    public Kind kind() {
        return kind;
    }
}
