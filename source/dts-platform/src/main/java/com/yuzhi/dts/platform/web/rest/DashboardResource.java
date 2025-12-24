package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.ClassificationUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * BI 大屏/报表目录（当前默认用于河图分享链接），在平台侧按部门/角色/密级做“可见性控制”。
 *
 * 注意：仅做目录控制不足以防止用户“拿到他人分享链接后手工改 URL 访问”，
 * 需要配合关闭河图直连端口（仅允许反代访问）并用审计兜底。
 */
@RestController
@RequestMapping("/api")
@Transactional
public class DashboardResource {

    private final ClassificationUtils classificationUtils;
    private final AuditService audit;
    private final Environment env;
    private final ObjectMapper objectMapper;

    public DashboardResource(
        ClassificationUtils classificationUtils,
        AuditService audit,
        Environment env,
        ObjectMapper objectMapper
    ) {
        this.classificationUtils = classificationUtils;
        this.audit = audit;
        this.env = env;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/dashboards")
    public ApiResponse<List<Map<String, Object>>> list() {
        List<Map<String, Object>> all = loadScreens();
        String userDept = claim("dept_code");
        List<String> userAuthorities = currentAuthorities();

        List<Map<String, Object>> visible = all.stream()
            .filter(d -> classificationUtils.canAccess(String.valueOf(d.get("level"))))
            .filter(d -> matchDept(userDept, d.get("deptCodes")))
            .filter(d -> matchRoles(userAuthorities, d.get("roles")))
            .map(this::publicView)
            .toList();
        audit.audit("READ", "dashboard.list", "visible=" + visible.size());
        return ApiResponses.ok(visible);
    }

    @PostMapping("/dashboards/visit")
    public ApiResponse<Map<String, Object>> visit(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> payload = new LinkedHashMap<>();
        String code = null;
        String name = null;
        String url = null;
        if (body != null) {
            code = text(body.get("code"));
            name = text(body.get("name"));
            url = text(body.get("url"));
            putIfPresent(payload, "code", code);
            putIfPresent(payload, "name", name);
            putIfPresent(payload, "url", url);
            putIfPresent(payload, "level", text(body.get("level")));
            putIfPresent(payload, "engine", text(body.get("engine")));
        }
        payload.putIfAbsent("dept_code", claim("dept_code"));
        payload.putIfAbsent("userMaxLevel", classificationUtils.getCurrentUserMaxLevel());
        audit.recordAuxiliary("OPEN", "vis", "hetu", code != null ? code : (url != null ? url : "unknown"), payload);
        return ApiResponses.ok(Map.of("ok", true));
    }

    private Map<String, Object> dashboard(String code, String name, String level, String url) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("name", name);
        m.put("level", level);
        m.put("url", url);
        return m;
    }

    private Map<String, Object> publicView(Map<String, Object> raw) {
        Map<String, Object> m = new LinkedHashMap<>();
        putIfPresent(m, "code", text(raw.get("code")));
        putIfPresent(m, "name", text(raw.get("name")));
        putIfPresent(m, "level", text(raw.get("level")));
        putIfPresent(m, "url", text(raw.get("url")));
        putIfPresent(m, "engine", text(raw.get("engine")));
        return m;
    }

    private List<Map<String, Object>> loadScreens() {
        // Optional: read screens from a mounted JSON file (recommended for quick iteration without code changes).
        // Example env: DTS_BI_HETU_SCREENS_FILE=/opt/dts/config/bi/hetu-screens.json
        String file = env.getProperty("dts.bi.hetu.screens-file");
        if (StringUtils.hasText(file)) {
            try {
                Path path = Path.of(file.trim());
                if (Files.exists(path) && Files.isRegularFile(path)) {
                    byte[] bytes = Files.readAllBytes(path);
                    List<Map<String, Object>> parsed = objectMapper.readValue(
                        bytes,
                        new TypeReference<List<Map<String, Object>>>() {}
                    );
                    if (parsed != null && !parsed.isEmpty()) {
                        return parsed;
                    }
                }
            } catch (Exception ignored) {
                // fall back to defaults
            }
        }

        // Defaults: placeholders; override via config file for real TJ share links.
        List<Map<String, Object>> all = new ArrayList<>();
        all.add(defaultScreen("ceo", "领导驾驶舱", "CONFIDENTIAL", "/dashboards/screen/share/index.html#/TJxxxx", "hetu"));
        all.add(defaultScreen("biz", "经营总览", "INTERNAL", "/dashboards/screen/share/index.html#/TJxxxx", "hetu"));
        all.add(defaultScreen("risk", "风控看板", "SECRET", "/dashboards/screen/share/index.html#/TJxxxx", "hetu"));
        all.add(defaultScreen("finance", "财务大屏", "INTERNAL", "/dashboards/screen/share/index.html#/TJxxxx", "hetu"));
        return all;
    }

    private Map<String, Object> defaultScreen(String code, String name, String level, String url, String engine) {
        Map<String, Object> m = dashboard(code, name, level, url);
        m.put("engine", engine);
        // Optional constraints supported by loadScreens() inputs:
        // - roles: ["ROLE_ADMIN","ROLE_EMPLOYEE"]
        // - deptCodes: ["D001","D002"]
        return m;
    }

    private boolean matchDept(String userDept, Object rawDeptCodes) {
        List<String> deptCodes = asStringList(rawDeptCodes);
        if (deptCodes.isEmpty()) return true;
        if (!StringUtils.hasText(userDept)) return false;
        String u = userDept.trim();
        return deptCodes.stream().anyMatch(d -> u.equalsIgnoreCase(String.valueOf(d).trim()));
    }

    private boolean matchRoles(List<String> userAuthorities, Object rawRoles) {
        List<String> required = asStringList(rawRoles);
        if (required.isEmpty()) return true;
        if (userAuthorities.isEmpty()) return false;
        for (String role : required) {
            String r = normalizeRole(role);
            if (r != null && userAuthorities.contains(r)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) return null;
        String upper = role.trim().toUpperCase(Locale.ROOT);
        if (upper.startsWith("ROLE_")) return upper;
        return "ROLE_" + upper;
    }

    private List<String> currentAuthorities() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getAuthorities() == null) return List.of();
            return auth.getAuthorities()
                .stream()
                .filter(Objects::nonNull)
                .map(a -> a.getAuthority())
                .filter(Objects::nonNull)
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String claim(String name) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken token) {
                Object v = token.getToken().getClaims().get(name);
                // Be tolerant to legacy typo claim names
                if (v == null && "person_security_level".equals(name)) {
                    v = token.getToken().getClaims().get("person_ssecurity_level");
                }
                return text(v);
            }
            if (auth != null && auth.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal) {
                Object v = principal.getAttribute(name);
                if (v == null && "person_security_level".equals(name)) {
                    v = principal.getAttribute("person_ssecurity_level");
                }
                return text(v);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private List<String> asStringList(Object raw) {
        if (raw == null) return List.of();
        if (raw instanceof List<?> list) {
            return list.stream().map(this::text).filter(StringUtils::hasText).toList();
        }
        if (raw instanceof String s) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) return List.of();
            // Allow a comma-separated string in configs
            return List.of(trimmed.split(",")).stream().map(String::trim).filter(StringUtils::hasText).toList();
        }
        return List.of();
    }

    private void putIfPresent(Map<String, Object> map, String key, String value) {
        if (StringUtils.hasText(value)) {
            map.put(key, value);
        }
    }

    private String text(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw).trim();
        return s.isEmpty() ? null : s;
    }
}
