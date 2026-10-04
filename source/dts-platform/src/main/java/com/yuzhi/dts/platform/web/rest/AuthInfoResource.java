package com.yuzhi.dts.platform.web.rest;

import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthInfoResource {

    @GetMapping(value = "/auth-info", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String, Object>> authInfo(
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        data.put("authenticated", auth != null && auth.isAuthenticated());
        if (auth != null) {
            data.put("principal", String.valueOf(auth.getPrincipal()));
            data.put("name", auth.getName());
            List<String> roles = new ArrayList<>();
            if (auth.getAuthorities() != null) {
                for (GrantedAuthority ga : auth.getAuthorities()) {
                    if (ga != null && ga.getAuthority() != null) roles.add(ga.getAuthority());
                }
            }
            data.put("roles", roles);
            // Attempt to read extra attributes from OAuth2 principal (opaque/jwt) for ABAC context
            try {
                if (auth.getPrincipal() instanceof org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal p) {
                    String deptCode = p.getAttribute("dept_code");
                    String personnelLevel = p.getAttribute("personnel_level");
                    if (deptCode == null) deptCode = p.getAttribute("deptCode");
                    if (personnelLevel == null) personnelLevel = p.getAttribute("person_security_level");
                    if (deptCode != null) data.put("dept_code", deptCode);
                    if (personnelLevel != null) data.put("personnel_level", personnelLevel);
                } else if (auth instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
                    Object deptCode = token.getToken().getClaims().get("dept_code");
                    Object personnelLevel = token.getToken().getClaims().get("personnel_level");
                    if (personnelLevel == null) personnelLevel = token.getToken().getClaims().get("person_security_level");
                    if (deptCode != null) data.put("dept_code", String.valueOf(deptCode));
                    if (personnelLevel != null) data.put("personnel_level", String.valueOf(personnelLevel));
                }
            } catch (Exception ignored) {}
        }
        Map<String, Object> context = new LinkedHashMap<>();
        if (activeDept != null) context.put("activeDept", activeDept);
        data.put("context", context);
        return ApiResponses.ok(data);
    }
}
