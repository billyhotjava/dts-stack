package com.yuzhi.dts.common.net;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClientIpTraceTest {

    @Test
    void capturesFullHeaderChainWithForwardedPrecedence() {
        ClientIpTrace trace = ClientIpTrace.from(
            "for=\"192.168.8.66\";proto=https;host=bi.example.com",
            "198.51.100.25, 172.19.0.15",
            "203.0.113.9",
            "172.19.0.14"
        );

        assertThat(trace.resolved()).isEqualTo("192.168.8.66");
        assertThat(trace.forwarded()).isEqualTo("for=\"192.168.8.66\";proto=https;host=bi.example.com");
        assertThat(trace.forwardedFor()).isEqualTo("198.51.100.25, 172.19.0.15");
        assertThat(trace.realIp()).isEqualTo("203.0.113.9");
        assertThat(trace.remoteAddr()).isEqualTo("172.19.0.14");
        assertThat(trace.candidates()).containsExactly("192.168.8.66", "198.51.100.25", "172.19.0.15", "203.0.113.9", "172.19.0.14");
        assertThat(trace.fallbackToRemote()).isFalse();
        assertThat(trace.missingForwarded()).isFalse();
    }

    @Test
    void marksFallbackWhenOnlyRemoteAddressIsAvailable() {
        ClientIpTrace trace = ClientIpTrace.from(null, null, null, "172.19.0.14");

        assertThat(trace.resolved()).isEqualTo("172.19.0.14");
        assertThat(trace.candidates()).containsExactly("172.19.0.14");
        assertThat(trace.fallbackToRemote()).isTrue();
        assertThat(trace.missingForwarded()).isTrue();
    }
}
