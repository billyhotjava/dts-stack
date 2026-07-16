package com.yuzhi.dts.platform.service.modeling;

import java.util.ArrayList;
import java.util.List;

/** Stable request/error contract shared by the /api/modeling controllers and clients. */
public final class ModelingApiContract {

    private ModelingApiContract() {}

    public enum ErrorCode {
        MODEL_REVISION_CONFLICT,
        MODEL_GRAIN_REQUIRED,
        MODEL_LAYER_INVALID,
        MODEL_OBJECT_NOT_FOUND,
        MODEL_PROCESS_MISMATCH,
    }

    public record WriteEnvelope<T>(T payload, int revision, String idempotencyKey) {}

    public record ApiError(ErrorCode code, String message, List<String> issues) {}

    public static <T> List<String> validateWriteRequest(WriteEnvelope<T> request) {
        List<String> issues = new ArrayList<>();
        if (request == null || request.revision() < 1) issues.add("revision must be greater than zero");
        if (request == null || request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            issues.add("idempotencyKey must not be blank");
        }
        return List.copyOf(issues);
    }

    public static <T> WriteEnvelope<T> requireValidWriteRequest(WriteEnvelope<T> request) {
        List<String> issues = validateWriteRequest(request);
        if (!issues.isEmpty()) throw new ModelingApiException(ErrorCode.MODEL_REVISION_CONFLICT, issues);
        return request;
    }

    public static final class ModelingApiException extends IllegalArgumentException {

        private final ErrorCode code;
        private final List<String> issues;

        public ModelingApiException(ErrorCode code, List<String> issues) {
            super(code.name());
            this.code = code;
            this.issues = List.copyOf(issues);
        }

        public ErrorCode code() {
            return code;
        }

        public List<String> issues() {
            return issues;
        }
    }
}
