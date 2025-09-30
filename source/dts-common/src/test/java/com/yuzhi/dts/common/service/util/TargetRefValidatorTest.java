package com.yuzhi.dts.common.service.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TargetRefValidatorTest {

    @Test
    void normalize_validatesAndNormalizes() {
        String n = TargetRefValidator.normalize("API://dts-admin/users/123/");
        assertEquals("api://dts-admin/users/123", n);

        String n2 = TargetRefValidator.normalize("http://example.com/path/");
        assertEquals("http://example.com/path", n2);
    }

    @Test
    void normalize_invalidScheme_throws() {
        assertThrows(IllegalArgumentException.class, () -> TargetRefValidator.normalize("foo://bar"));
    }

    @Test
    void normalize_spacesNotAllowed() {
        assertThrows(IllegalArgumentException.class, () -> TargetRefValidator.normalize("api://has space"));
    }
}

