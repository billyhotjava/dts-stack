package com.yuzhi.dts.copilot.analytics.web.rest.errors;

import java.util.List;

public class ScreenSpecValidationException extends RuntimeException {

    private final String code;
    private final List<String> errors;

    public ScreenSpecValidationException(String code, List<String> errors) {
        super(buildMessage(errors));
        this.code = code == null || code.isBlank() ? "SCREEN_SPEC_INVALID" : code;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public String getCode() {
        return code;
    }

    public List<String> getErrors() {
        return errors;
    }

    private static String buildMessage(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return "Screen spec validation failed";
        }
        int limit = Math.min(errors.size(), 6);
        String message = String.join("; ", errors.subList(0, limit));
        if (errors.size() > limit) {
            message = message + "; ...";
        }
        return message;
    }
}
