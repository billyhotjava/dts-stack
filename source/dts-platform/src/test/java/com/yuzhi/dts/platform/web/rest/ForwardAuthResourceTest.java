package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

class ForwardAuthResourceTest {

    @Test
    void redirectsBiUiRequestsThroughPlatformLoginRelay() {
        ForwardAuthResource resource = new ForwardAuthResource("https://platform.example.com");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/forward-auth");
        request.addHeader("X-Forwarded-Uri", "/bi/screens/42/edit?tab=canvas");
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "platform.example.com");

        var response = resource.forwardAuth(null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo(
                "https://platform.example.com/auth/login?redirect=%2Fexternal-redirect%3Ftarget%3Dhttps%253A%252F%252Fplatform.example.com%252Fbi%252Fscreens%252F42%252Fedit%253Ftab%253Dcanvas"
                        + "#/auth/login?redirect=%2Fexternal-redirect%3Ftarget%3Dhttps%253A%252F%252Fplatform.example.com%252Fbi%252Fscreens%252F42%252Fedit%253Ftab%253Dcanvas"
        );
    }

    @Test
    void combinesBiPrefixAndUriWhenForwardAuthSeesStrippedUiPath() {
        ForwardAuthResource resource = new ForwardAuthResource("https://platform.example.com");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/forward-auth");
        request.addHeader("X-Forwarded-Prefix", "/bi");
        request.addHeader("X-Forwarded-Uri", "/screens/42/edit");
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "platform.example.com");

        var response = resource.forwardAuth(null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo(
                "https://platform.example.com/auth/login?redirect=%2Fexternal-redirect%3Ftarget%3Dhttps%253A%252F%252Fplatform.example.com%252Fbi%252Fscreens%252F42%252Fedit"
                        + "#/auth/login?redirect=%2Fexternal-redirect%3Ftarget%3Dhttps%253A%252F%252Fplatform.example.com%252Fbi%252Fscreens%252F42%252Fedit"
        );
    }

    @Test
    void keepsBiApiRequestsAsUnauthorizedResponses() {
        ForwardAuthResource resource = new ForwardAuthResource("https://platform.example.com");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/forward-auth");
        request.addHeader("X-Forwarded-Uri", "/bi/api/card/1/query");

        var response = resource.forwardAuth(null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION)).isNull();
    }

    @Test
    void redirectsCrossDomainUiRequestsBackToPlatformLogin() {
        ForwardAuthResource resource = new ForwardAuthResource("https://bi.example.com");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/forward-auth");
        request.addHeader("X-Forwarded-Uri", "/explore/tables");
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "meta.example.com");

        var response = resource.forwardAuth(null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo(
                "https://bi.example.com/auth/login?redirect=%2Fexternal-redirect%3Ftarget%3Dhttps%253A%252F%252Fmeta.example.com%252Fexplore%252Ftables"
                        + "#/auth/login?redirect=%2Fexternal-redirect%3Ftarget%3Dhttps%253A%252F%252Fmeta.example.com%252Fexplore%252Ftables"
        );
    }

    @Test
    void keepsCrossDomainApiRequestsAsUnauthorizedResponses() {
        ForwardAuthResource resource = new ForwardAuthResource("https://bi.example.com");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/forward-auth");
        request.addHeader("X-Forwarded-Uri", "/api/v1/health-check");
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "flow.example.com");

        var response = resource.forwardAuth(null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION)).isNull();
    }
}
