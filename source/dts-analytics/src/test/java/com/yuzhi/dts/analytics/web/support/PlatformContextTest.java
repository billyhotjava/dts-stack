package com.yuzhi.dts.analytics.web.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class PlatformContextTest {

    @Test
    void from_accepts_platform_forward_auth_header_names() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-DTS-Dept-Code", "1501");
        request.addHeader("X-DTS-Personnel-Level", "GENERAL");
        request.addHeader("X-DTS-Roles", "ROLE_PTR_DEMO, ROLE_OTHER");

        PlatformContext context = PlatformContext.from(request);

        assertThat(context.dept()).isEqualTo("1501");
        assertThat(context.classification()).isEqualTo("SECRET");
        assertThat(context.rolesList()).containsExactly("ROLE_PTR_DEMO", "ROLE_OTHER");
    }

    @Test
    void explicit_classification_header_takes_precedence_over_personnel_level() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-DTS-Classification", "INTERNAL");
        request.addHeader("X-DTS-Personnel-Level", "IMPORTANT");

        PlatformContext context = PlatformContext.from(request);

        assertThat(context.classification()).isEqualTo("INTERNAL");
    }
}
