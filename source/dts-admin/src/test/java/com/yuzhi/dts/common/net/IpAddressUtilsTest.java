package com.yuzhi.dts.common.net;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Coverage for the IP-resolution rules motivated by BUG-B (production audit was recording
 * container IPs because the previous implementation skipped private-network candidates).
 */
class IpAddressUtilsTest {

    @Test
    @DisplayName("XFF 链 → 取最左 (real client)")
    void xffPicksLeftmost() {
        String ip = IpAddressUtils.resolveClientIp("203.0.113.5, 10.0.0.1, 10.0.0.2", null, "10.0.0.99");
        assertThat(ip).isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("内网部署：所有候选都是私网时仍返回 XFF 头部，而不是 fallback 到 remoteAddr")
    void privateNetworkDeploymentReturnsRealClient() {
        // Production DTS is internal: real client = 192.168.1.5, ingress = 10.0.0.10, container sees 172.16.0.1
        String ip = IpAddressUtils.resolveClientIp("192.168.1.5", "192.168.1.5", "172.16.0.1");
        assertThat(ip).isEqualTo("192.168.1.5");
    }

    @Test
    @DisplayName("XFF 不存在时回退到 X-Real-IP")
    void fallbackToRealIp() {
        String ip = IpAddressUtils.resolveClientIp(null, "192.168.1.5", "172.16.0.1");
        assertThat(ip).isEqualTo("192.168.1.5");
    }

    @Test
    @DisplayName("XFF 和 X-Real-IP 都不存在时回退到 remoteAddr")
    void fallbackToRemoteAddr() {
        String ip = IpAddressUtils.resolveClientIp(null, null, "203.0.113.7");
        assertThat(ip).isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("空字符串 / 'unknown' / null 全部跳过")
    void blankAndUnknownSkipped() {
        assertThat(IpAddressUtils.resolveClientIp("", "unknown", " ", "203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(IpAddressUtils.resolveClientIp(null, null, null)).isNull();
    }

    @Test
    @DisplayName("IPv4 + 端口 → 端口剥离")
    void ipv4WithPortStrippedToHost() {
        assertThat(IpAddressUtils.resolveClientIp("203.0.113.5:55001", null, null)).isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("IPv6 [bracketed] + 端口 → 仅留地址")
    void ipv6BracketsStripped() {
        assertThat(IpAddressUtils.resolveClientIp("[2001:db8::1]:55001", null, null)).isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    @Test
    @DisplayName("::ffff: 前缀的 IPv4-mapped IPv6 → 转为 dotted IPv4")
    void ipv4MappedIpv6Normalised() {
        assertThat(IpAddressUtils.resolveClientIp("::ffff:203.0.113.5", null, null)).isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("RFC 7239 Forwarded header `for=` 前缀剥离")
    void forwardedHeaderForPrefixHandled() {
        assertThat(IpAddressUtils.resolveClientIp("for=192.0.2.43", null, null)).isEqualTo("192.0.2.43");
        assertThat(IpAddressUtils.resolveClientIp("for=\"192.0.2.43\"", null, null)).isEqualTo("192.0.2.43");
        assertThat(IpAddressUtils.resolveClientIp("for=\"[2001:db8::1]:47011\"", null, null)).isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    @Test
    @DisplayName("RFC 7239 Forwarded header 带 proto/host 参数时只保留 for 值")
    void forwardedHeaderParametersAreStripped() {
        assertThat(IpAddressUtils.resolveClientIp("for=172.168.0.1;proto=https;host=biadmin.example.com", null, null))
            .isEqualTo("172.168.0.1");
        assertThat(IpAddressUtils.resolveClientIp("for=\"172.168.0.1:55001\";proto=https", null, null))
            .isEqualTo("172.168.0.1");
    }

    @Test
    @DisplayName("XFF 中前置空字段（非法 proxy 配置）→ 跳过空字段取下一个")
    void xffSkipsEmptyLeadingSegments() {
        String ip = IpAddressUtils.resolveClientIp(", 203.0.113.5, 10.0.0.1", null, null);
        assertThat(ip).isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("XFF 中只有 'unknown' 字段 → 跳过 fall through")
    void xffAllUnknownFallsThrough() {
        String ip = IpAddressUtils.resolveClientIp("unknown", null, "10.0.0.1");
        assertThat(ip).isEqualTo("10.0.0.1");
    }

    @Test
    @DisplayName("空 candidates 数组 → null")
    void emptyCandidatesReturnNull() {
        assertThat(IpAddressUtils.resolveClientIp()).isNull();
    }

    @Test
    @DisplayName("无法解析的字面量 → 原样返回（保留审计证据）")
    void unparsableLiteralReturnedVerbatim() {
        // 'foo.bar.invalid' 本来不是合法的 IP 字符串，但作为审计证据仍然记录原始值
        assertThat(IpAddressUtils.resolveClientIp("foo.bar.invalid", null, null)).isEqualTo("foo.bar.invalid");
    }

    // ---- resolveClientIp(HeaderLookup, remoteAddr) 收口重载：所有审计/安全调用点现在共享此逻辑 ----

    private static IpAddressUtils.HeaderLookup headers(Map<String, String> map) {
        return map::get;
    }

    @Test
    @DisplayName("收口重载：RFC 7239 Forwarded 头优先于 XFF/XRealIP（现场只下发 Forwarded 的核心场景）")
    void headerLookupReadsForwardedFirst() {
        Map<String, String> map = new HashMap<>();
        map.put("Forwarded", "for=203.0.113.9;proto=https;host=biadmin.example.com");
        map.put("X-Forwarded-For", "198.51.100.7");
        map.put("X-Real-IP", "198.51.100.7");
        String ip = IpAddressUtils.resolveClientIp(headers(map), "172.168.0.1");
        assertThat(ip).isEqualTo("203.0.113.9");
    }

    @Test
    @DisplayName("收口重载：无 Forwarded 时按 XFF → X-Real-IP → remoteAddr 链式回退")
    void headerLookupFallsBackThroughChain() {
        Map<String, String> onlyXff = new HashMap<>();
        onlyXff.put("X-Forwarded-For", "203.0.113.10");
        assertThat(IpAddressUtils.resolveClientIp(headers(onlyXff), "172.168.0.1")).isEqualTo("203.0.113.10");

        Map<String, String> onlyRealIp = new HashMap<>();
        onlyRealIp.put("X-Real-IP", "203.0.113.11");
        assertThat(IpAddressUtils.resolveClientIp(headers(onlyRealIp), "172.168.0.1")).isEqualTo("203.0.113.11");

        // 现场无任何代理头 → 回退到 remoteAddr（容器/网关 IP），仍保留审计证据而非清空
        assertThat(IpAddressUtils.resolveClientIp(headers(new HashMap<>()), "172.168.0.1")).isEqualTo("172.168.0.1");
    }

    @Test
    @DisplayName("收口重载：headers 为 null 时退化为仅用 remoteAddr")
    void headerLookupNullUsesRemoteAddr() {
        assertThat(IpAddressUtils.resolveClientIp(null, "203.0.113.12")).isEqualTo("203.0.113.12");
    }
}
