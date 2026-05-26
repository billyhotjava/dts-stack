package com.yuzhi.dts.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.analytics.config.PlatformAuthProperties;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsUserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(OutputCaptureExtension.class)
class PlatformTrustedUserServiceTest {

    @Test
    void shouldIgnoreRequestsWithoutTrustedIdentityHeadersBeforeForwardedMarkerCheck(CapturedOutput output) {
        AnalyticsUserRepository userRepository = mock(AnalyticsUserRepository.class);
        PlatformTrustedUserService service = newService(userRepository);

        assertThat(service.resolveOrProvision(new MockHttpServletRequest())).isEmpty();

        assertThat(output).doesNotContain("Rejecting analytics auth request with X-DTS headers");
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRejectTrustedIdentityHeadersWithoutForwardedMarkers(CapturedOutput output) {
        AnalyticsUserRepository userRepository = mock(AnalyticsUserRepository.class);
        PlatformTrustedUserService service = newService(userRepository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-DTS-User", "opadmin");

        assertThat(service.resolveOrProvision(request)).isEmpty();

        assertThat(output).contains("Rejecting analytics auth request with X-DTS headers but missing forwarded proxy markers");
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldProvisionTrustedForwardedIdentity() {
        AnalyticsUserRepository userRepository = mock(AnalyticsUserRepository.class);
        GroupService groupService = mock(GroupService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        PlatformTrustedUserService service = newService(userRepository, groupService, passwordEncoder);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-DTS-User", "opadmin");
        request.addHeader("X-DTS-Display-Name", "Ops Admin");
        request.addHeader("X-DTS-User-Id", "platform-user-1");
        request.addHeader("X-DTS-Roles", "ROLE_OP_ADMIN");
        request.addHeader("X-Forwarded-Proto", "https");
        when(userRepository.findByEmailIgnoreCase("platform-user-1@platform.local")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userRepository.save(any(AnalyticsUser.class))).thenAnswer(invocation -> {
            AnalyticsUser user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        AnalyticsUser user = service.resolveOrProvision(request).orElseThrow();

        assertThat(user.getEmail()).isEqualTo("platform-user-1@platform.local");
        assertThat(user.getPlatformUsername()).isEqualTo("opadmin");
        assertThat(user.getFirstName()).isEqualTo("Ops Admin");
        assertThat(user.isSuperuser()).isTrue();
        verify(groupService).ensureUserInDefaultGroups(user);
    }

    private PlatformTrustedUserService newService(AnalyticsUserRepository userRepository) {
        return newService(userRepository, mock(GroupService.class), mock(PasswordEncoder.class));
    }

    private PlatformTrustedUserService newService(
            AnalyticsUserRepository userRepository, GroupService groupService, PasswordEncoder passwordEncoder) {
        return new PlatformTrustedUserService(
                new PlatformAuthProperties(
                        true,
                        true,
                        "platform.local",
                        List.of("ROLE_OP_ADMIN"),
                        false,
                        "http://dts-platform:8081/api/forward-auth",
                        2000),
                userRepository,
                groupService,
                passwordEncoder);
    }
}
