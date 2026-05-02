package com.yuzhi.dts.platform.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * 入站服务鉴权配置:描述"谁能以 service-to-service 身份调 platform,以及需要带什么 token"。
 * <p>
 * Sprint-28 F1 仅承载字段(从 {@link DtsAdminProperties} 拆出),保持运行时行为与 Sprint-27 等价;
 * Sprint-28 F2 将把单一 sharedSecret 替换为 {@link #trustedServices} Map,实现每对调用独立 secret;
 * Sprint-28 F3 将启用 {@link #legacyHeaderOnlyMode}=false 默认行为,关闭"白名单即权限"越权面。
 */
@ConfigurationProperties(prefix = "dts.platform.inbound.service-auth")
public class PlatformInboundServiceAuthProperties {

    /** 是否启用入站服务鉴权 filter,关闭时所有内部服务调用均匿名。 */
    private boolean enabled = true;

    /** 入站白名单服务名列表。F2 会被 {@link #trustedServices} 的 keySet 取代。 */
    private List<String> trustedServiceNames = new ArrayList<>();

    /**
     * 共享 token,所有受信任服务统一校验。F2 会被 {@link #trustedServices} 替代;
     * 在 F2 之前,filter 仍以单值方式校验,与 Sprint-27 等价。
     */
    private String sharedSecret;

    /**
     * 每对调用独立 token 配置,key=service name,value=expected token。
     * F2 启用后,filter 优先从此 Map 读取;若 key 缺失则 fallback 到 {@link #sharedSecret}。
     */
    private Map<String, String> trustedServices = new LinkedHashMap<>();

    /**
     * 兼容开关:开启时回退到 Sprint-27 行为(仅校验 X-DTS-Service header 在白名单内即注入 OP_ADMIN)。
     * 默认 false。production 严禁开启,启动时会输出 WARN。
     */
    private boolean legacyHeaderOnlyMode = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getTrustedServiceNames() {
        return trustedServiceNames;
    }

    public void setTrustedServiceNames(List<String> trustedServiceNames) {
        this.trustedServiceNames = trustedServiceNames == null ? new ArrayList<>() : trustedServiceNames;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    public Map<String, String> getTrustedServices() {
        return trustedServices;
    }

    public void setTrustedServices(Map<String, String> trustedServices) {
        this.trustedServices = trustedServices == null ? new LinkedHashMap<>() : trustedServices;
    }

    public boolean isLegacyHeaderOnlyMode() {
        return legacyHeaderOnlyMode;
    }

    public void setLegacyHeaderOnlyMode(boolean legacyHeaderOnlyMode) {
        this.legacyHeaderOnlyMode = legacyHeaderOnlyMode;
    }

    /**
     * 判断给定 serviceName 是否在白名单内(优先看 trustedServices Map keys,再看 trustedServiceNames List)。
     */
    public boolean isTrustedServiceName(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return false;
        }
        String normalized = candidate.trim();
        if (trustedServices != null && !trustedServices.isEmpty()) {
            for (String key : trustedServices.keySet()) {
                if (StringUtils.hasText(key) && key.trim().equalsIgnoreCase(normalized)) {
                    return true;
                }
            }
        }
        if (trustedServiceNames != null) {
            for (String name : trustedServiceNames) {
                if (StringUtils.hasText(name) && name.trim().equalsIgnoreCase(normalized)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 取给定 serviceName 期望携带的 token。
     * 优先 trustedServices.get;若 Map 命中但 value 为空字符串(运维仅设了 DTS_ADMIN_SERVICE_TOKEN 这种 fallback 场景),
     * 仍然 fallback 到 sharedSecret,避免 Sprint-28 启用 Map 后老部署链路立即失败。
     */
    public String resolveExpectedToken(String serviceName) {
        if (!StringUtils.hasText(serviceName)) {
            return null;
        }
        String normalized = serviceName.trim();
        if (trustedServices != null) {
            for (Map.Entry<String, String> entry : trustedServices.entrySet()) {
                if (StringUtils.hasText(entry.getKey()) && entry.getKey().trim().equalsIgnoreCase(normalized)) {
                    String value = entry.getValue();
                    if (StringUtils.hasText(value)) {
                        return value;
                    }
                    break;
                }
            }
        }
        return sharedSecret;
    }

    /**
     * 返回与给定 serviceName 等价的标准白名单条目(保留原始大小写,便于日志输出)。
     */
    public String canonicalServiceName(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        String normalized = candidate.trim();
        if (trustedServices != null) {
            for (String key : trustedServices.keySet()) {
                if (StringUtils.hasText(key) && key.trim().equalsIgnoreCase(normalized)) {
                    return key.trim();
                }
            }
        }
        if (trustedServiceNames != null) {
            for (String name : trustedServiceNames) {
                if (StringUtils.hasText(name) && name.trim().equalsIgnoreCase(normalized)) {
                    return name.trim();
                }
            }
        }
        return normalized.toLowerCase(Locale.ROOT);
    }
}
