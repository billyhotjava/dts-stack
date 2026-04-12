package com.yuzhi.dts.apitests.support;

import java.util.Objects;
import org.junit.jupiter.api.Assumptions;

/** Reads test env vars; skips the test when a required one is missing. */
public final class Env {

    private Env() {}

    public static String require(String name) {
        String v = System.getenv(name);
        Assumptions.assumeTrue(v != null && !v.isBlank(), "env " + name + " not set — skipping");
        return v;
    }

    public static String optional(String name, String defaultValue) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }

    public static boolean verifyTls() {
        String v = Objects.requireNonNullElse(System.getenv("VERIFY_TLS"), "false");
        return v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes");
    }
}
