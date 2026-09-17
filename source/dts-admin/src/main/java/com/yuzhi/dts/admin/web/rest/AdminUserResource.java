package com.yuzhi.dts.admin.web.rest;

import com.yuzhi.dts.admin.domain.AdminKeycloakUser;
import com.yuzhi.dts.admin.security.AuthoritiesConstants;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.service.dto.keycloak.ApprovalDTOs;
import com.yuzhi.dts.admin.service.user.AdminUserService;
import com.yuzhi.dts.admin.service.user.UserOperationRequest;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import com.yuzhi.dts.admin.web.rest.vm.AdminUserVM;
import com.yuzhi.dts.admin.web.rest.vm.PagedResultVM;
import com.yuzhi.dts.admin.web.rest.vm.UserRequestVM;
import com.yuzhi.dts.common.net.IpAddressUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.MultiValueMap;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserResource {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final AdminUserService adminUserService;

    public AdminUserResource(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResultVM<AdminUserVM>>> listUsers(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer status,
        @RequestParam(required = false) Boolean enabled
    ) {
        int pageSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, 200);
        Integer mdmStatus = null;
        if (status != null) {
            if (status.intValue() == 0 || status.intValue() == 1) {
                mdmStatus = status.intValue();
            } else {
                return ResponseEntity.badRequest().body(ApiResponse.error("status 仅支持 0（不可用）或 1（可用）"));
            }
        }
        Page<AdminKeycloakUser> result = adminUserService.listSnapshots(page, pageSize, keyword, mdmStatus, enabled);
        List<AdminKeycloakUser> snapshots = result.getContent();
        Map<String, AdminUserService.DepartmentInfo> deptMap = adminUserService.resolveDepartments(
            snapshots.stream().map(AdminKeycloakUser::getUsername).filter(StringUtils::isNotBlank).toList()
        );
        Map<String, List<String>> roleMap = adminUserService.aggregateRealmRolesByUser(snapshots);
        List<AdminUserVM> content = new ArrayList<>(snapshots.size());
        for (AdminKeycloakUser current : snapshots) {
            String username = current == null ? null : StringUtils.trimToNull(current.getUsername());
            List<String> aggregatedRoles = username == null
                ? List.of()
                : roleMap.getOrDefault(username.toLowerCase(Locale.ROOT), List.of());
            AdminUserVM vm = toVm(current, aggregatedRoles);
            if (current != null && StringUtils.isNotBlank(current.getUsername())) {
                AdminUserService.DepartmentInfo dept = deptMap.get(current.getUsername());
                if (dept != null) {
                    vm.setDeptCode(dept.deptCode());
                    vm.setDeptName(dept.deptName());
                }
            }
            content.add(vm);
        }
        PagedResultVM<AdminUserVM> body = new PagedResultVM<>(content, result.getTotalElements(), result.getNumber(), result.getSize());
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    @GetMapping("/display-names")
    public ResponseEntity<ApiResponse<Map<String, String>>> resolveDisplayNames(
        @RequestParam MultiValueMap<String, String> query
    ) {
        List<String> inputs = normalizeUsernames(query);
        Map<String, String> resolved = adminUserService.resolveDisplayNames(inputs);
        return ResponseEntity.ok(ApiResponse.ok(resolved));
    }

    @GetMapping("/mdm-enabled")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> resolveMdmEnabled(
        @RequestParam MultiValueMap<String, String> query
    ) {
        List<String> inputs = normalizeUsernames(query);
        Map<String, Integer> resolved = adminUserService.resolveMdmEnabled(inputs);
        return ResponseEntity.ok(ApiResponse.ok(resolved));
    }

    @PostMapping("/mdm-enabled")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> resolveMdmEnabledPost(
        @RequestBody(required = false) UsernamesRequestVM request
    ) {
        List<String> inputs = normalizeUsernames(request == null ? null : request.usernames);
        Map<String, Integer> resolved = adminUserService.resolveMdmEnabled(inputs);
        return ResponseEntity.ok(ApiResponse.ok(resolved));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.SYS_ADMIN + "')")
    public ResponseEntity<ApiResponse<ApprovalDTOs.ApprovalRequestDetail>> createUser(
        @Valid @RequestBody UserRequestVM request,
        HttpServletRequest servletRequest
    ) {
        UserOperationRequest command = toCommand(request);
        ApprovalDTOs.ApprovalRequestDetail detail = adminUserService.submitCreate(
            command,
            SecurityUtils.getCurrentUserLogin().orElse("sysadmin"),
            clientIp(servletRequest)
        );
        return ResponseEntity.ok(ApiResponse.ok(detail));
    }

    @PutMapping("/{username}")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.SYS_ADMIN + "')")
    public ResponseEntity<ApiResponse<ApprovalDTOs.ApprovalRequestDetail>> updateUser(
        @PathVariable String username,
        @Valid @RequestBody UserRequestVM request,
        HttpServletRequest servletRequest
    ) {
        UserOperationRequest command = toCommand(request);
        ApprovalDTOs.ApprovalRequestDetail detail = adminUserService.submitUpdate(
            username,
            command,
            SecurityUtils.getCurrentUserLogin().orElse("sysadmin"),
            clientIp(servletRequest)
        );
        return ResponseEntity.ok(ApiResponse.ok(detail));
    }

    @DeleteMapping("/{username}")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.SYS_ADMIN + "')")
    public ResponseEntity<ApiResponse<ApprovalDTOs.ApprovalRequestDetail>> deleteUser(
        @PathVariable String username,
        @RequestBody(required = false) DeleteUserRequestVM request
    ) {
        String message = "用户删除功能已禁用，请改用停用操作";
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(message));
    }

    public static final class UsernamesRequestVM {
        public List<String> usernames;
    }

    private static List<String> normalizeUsernames(List<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return List.of();
        }
        List<String> inputs = new ArrayList<>(usernames.size());
        for (String raw : usernames) {
            if (!StringUtils.isNotBlank(raw)) {
                continue;
            }
            String normalized = raw.trim();
            if (!normalized.isEmpty()) {
                inputs.add(normalized);
            }
        }
        return inputs;
    }

    private List<String> normalizeUsernames(MultiValueMap<String, String> query) {
        if (query == null || query.isEmpty()) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        List<String> rawValues = query.get("usernames");
        if (rawValues == null || rawValues.isEmpty()) {
            return List.of();
        }
        for (String raw : rawValues) {
            if (StringUtils.isBlank(raw)) {
                continue;
            }
            for (String token : raw.split("[,;]")) {
                String cleaned = StringUtils.trimToNull(token);
                if (cleaned != null) {
                    result.add(cleaned);
                }
            }
        }
        return result.isEmpty() ? List.of() : new ArrayList<>(result);
    }

    private UserOperationRequest toCommand(UserRequestVM request) {
        UserOperationRequest command = new UserOperationRequest();
        command.setUsername(request.getUsername());
        command.setFullName(request.getFullName());
        command.setEmail(request.getEmail());
        command.setPhone(request.getPhone());
        command.setPersonSecurityLevel(request.getPersonSecurityLevel());
        if (request.isRealmRolesSpecified()) {
            command.setRealmRoles(request.getRealmRoles());
        } else {
            command.markRealmRolesUnspecified();
        }
        if (request.isGroupPathsSpecified()) {
            command.setGroupPaths(request.getGroupPaths());
        } else {
            command.markGroupPathsUnspecified();
        }
        command.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        command.setReason(request.getReason());
        command.setAttributes(request.getAttributes());
        return command;
    }

    private AdminUserVM toVm(AdminKeycloakUser entity, List<String> aggregatedRoles) {
        AdminUserVM vm = new AdminUserVM();
        vm.setId(entity.getId());
        vm.setKeycloakId(entity.getKeycloakId());
        vm.setUsername(entity.getUsername());
        vm.setFullName(entity.getFullName());
        vm.setEmail(entity.getEmail());
        vm.setPhone(entity.getPhone());
        vm.setPersonSecurityLevel(entity.getPersonSecurityLevel());
        vm.setRealmRoles(aggregatedRoles == null ? List.of() : aggregatedRoles);
        vm.setGroupPaths(entity.getGroupPaths());
        vm.setEnabled(entity.isEnabled());
        vm.setMdmEnabled(entity.getMdmEnabled());
        vm.setLastSyncAt(entity.getLastSyncAt());
        return vm;
    }

    private String clientIp(HttpServletRequest request) {
        return IpAddressUtils.resolveClientIp(request::getHeader, request.getRemoteAddr());
    }

    public static class DeleteUserRequestVM {
        private String reason;

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
