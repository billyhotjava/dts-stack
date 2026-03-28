package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.analytics.domain.AnalyticsScreenAcl;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenAclRepository;
import com.yuzhi.dts.analytics.web.support.PlatformContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @deprecated Superseded by platform-centralized asset_grant table (Sprint-19).
 * Screen permissions now managed via platform AssetGrantResource.
 */
@Deprecated(since = "2.2.2", forRemoval = true)
@Service
@Transactional
public class ScreenAclService {

    public enum Permission {
        READ,
        EDIT,
        PUBLISH,
        MANAGE
    }

    public static final String SUBJECT_TYPE_USER = "USER";
    public static final String SUBJECT_TYPE_ROLE = "ROLE";
    public static final List<String> DEFAULT_READ_ROLES = List.of(
            "ROLE_DEPT_LEADER",
            "ROLE_DEPT_DATA_OWNER",
            "ROLE_INST_DATA_OWNER",
            "ROLE_INST_LEADER");

    private static final Set<String> VALID_SUBJECT_TYPES = Set.of(SUBJECT_TYPE_USER, SUBJECT_TYPE_ROLE);
    private static final Set<String> VALID_PERMS = Set.of("READ", "EDIT", "PUBLISH", "MANAGE");

    private final AnalyticsScreenAclRepository screenAclRepository;

    public ScreenAclService(AnalyticsScreenAclRepository screenAclRepository) {
        this.screenAclRepository = screenAclRepository;
    }

    public PermissionSnapshot snapshot(AnalyticsScreen screen, AnalyticsUser user, PlatformContext context) {
        ensureDefaultReadRoles(screen);
        if (user == null) {
            return PermissionSnapshot.none();
        }
        if (user.isSuperuser() || isCreator(screen, user)) {
            return PermissionSnapshot.all();
        }

        Set<String> granted = resolveGrantedPerms(
                screenAclRepository.findAllByScreenIdOrderByIdAsc(screen.getId()),
                user.getId(),
                context == null ? null : context.roles());
        boolean canManage = granted.contains("MANAGE");
        boolean canPublish = canManage || granted.contains("PUBLISH");
        boolean canEdit = canPublish || granted.contains("EDIT");
        boolean canRead = canEdit || granted.contains("READ");
        return new PermissionSnapshot(canRead, canEdit, canPublish, canManage);
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(AnalyticsScreen screen, AnalyticsUser user, PlatformContext context, Permission permission) {
        PermissionSnapshot snapshot = snapshot(screen, user, context);
        return switch (permission) {
            case READ -> snapshot.canRead();
            case EDIT -> snapshot.canEdit();
            case PUBLISH -> snapshot.canPublish();
            case MANAGE -> snapshot.canManage();
        };
    }

    public void ensureCreatorManage(AnalyticsScreen screen) {
        if (screen == null || screen.getId() == null || screen.getCreatorId() == null) {
            return;
        }
        String creatorId = String.valueOf(screen.getCreatorId());
        if (!screenAclRepository.existsByScreenIdAndSubjectTypeAndSubjectIdAndPerm(
                screen.getId(), SUBJECT_TYPE_USER, creatorId, "MANAGE")) {
            AnalyticsScreenAcl acl = new AnalyticsScreenAcl();
            acl.setScreenId(screen.getId());
            acl.setSubjectType(SUBJECT_TYPE_USER);
            acl.setSubjectId(creatorId);
            acl.setPerm("MANAGE");
            acl.setCreatorId(screen.getCreatorId());
            screenAclRepository.save(acl);
        }
    }

    public void ensureDefaultReadRoles(AnalyticsScreen screen) {
        if (screen == null || screen.getId() == null || screen.getCreatorId() == null) {
            return;
        }
        List<AnalyticsScreenAcl> existingEntries = screenAclRepository.findAllByScreenIdOrderByIdAsc(screen.getId());
        Set<String> existingRoleReads = new LinkedHashSet<>();
        for (AnalyticsScreenAcl entry : existingEntries) {
            if (SUBJECT_TYPE_ROLE.equals(normalize(entry.getSubjectType()))
                    && "READ".equals(normalize(entry.getPerm()))
                    && entry.getSubjectId() != null
                    && !entry.getSubjectId().isBlank()) {
                existingRoleReads.add(entry.getSubjectId().trim());
            }
        }

        List<AnalyticsScreenAcl> missingEntries = new ArrayList<>();
        for (String role : DEFAULT_READ_ROLES) {
            if (existingRoleReads.contains(role)) {
                continue;
            }
            AnalyticsScreenAcl acl = new AnalyticsScreenAcl();
            acl.setScreenId(screen.getId());
            acl.setSubjectType(SUBJECT_TYPE_ROLE);
            acl.setSubjectId(role);
            acl.setPerm("READ");
            acl.setCreatorId(screen.getCreatorId());
            missingEntries.add(acl);
        }
        if (!missingEntries.isEmpty()) {
            screenAclRepository.saveAll(missingEntries);
        }
    }

    @Transactional(readOnly = true)
    public List<AnalyticsScreenAcl> listEntries(Long screenId) {
        return screenAclRepository.findAllByScreenIdOrderByIdAsc(screenId);
    }

    public void replaceEntries(AnalyticsScreen screen, Long operatorId, List<AnalyticsScreenAcl> entries) {
        screenAclRepository.deleteAllByScreenId(screen.getId());
        if (entries != null && !entries.isEmpty()) {
            for (AnalyticsScreenAcl entry : entries) {
                entry.setId(null);
                entry.setScreenId(screen.getId());
                if (entry.getCreatorId() == null) {
                    entry.setCreatorId(operatorId);
                }
            }
            screenAclRepository.saveAll(entries);
        }
        ensureCreatorManage(screen);
        ensureDefaultReadRoles(screen);
    }

    public boolean isValidSubjectType(String subjectType) {
        return VALID_SUBJECT_TYPES.contains(normalize(subjectType));
    }

    public boolean isValidPerm(String perm) {
        return VALID_PERMS.contains(normalize(perm));
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private Set<String> resolveGrantedPerms(Collection<AnalyticsScreenAcl> entries, Long userId, String rolesHeader) {
        if (entries.isEmpty()) {
            return Set.of();
        }

        Set<String> roleSet = parseRoles(rolesHeader);
        String uid = String.valueOf(userId);
        Set<String> granted = new LinkedHashSet<>();
        for (AnalyticsScreenAcl entry : entries) {
            String subjectType = normalize(entry.getSubjectType());
            String subjectId = entry.getSubjectId() == null ? "" : entry.getSubjectId().trim();
            String perm = normalize(entry.getPerm());
            if (perm == null || !VALID_PERMS.contains(perm)) {
                continue;
            }
            if (SUBJECT_TYPE_USER.equals(subjectType) && uid.equals(subjectId)) {
                granted.add(perm);
            }
            if (SUBJECT_TYPE_ROLE.equals(subjectType) && roleSet.contains(subjectId)) {
                granted.add(perm);
            }
        }
        return granted;
    }

    private Set<String> parseRoles(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return Set.of();
        }
        String[] parts = rolesHeader.split(",");
        Set<String> roles = new LinkedHashSet<>();
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String normalized = part.trim();
            if (!normalized.isBlank()) {
                roles.add(normalized);
            }
        }
        return roles;
    }

    private boolean isCreator(AnalyticsScreen screen, AnalyticsUser user) {
        return screen != null
                && user != null
                && screen.getCreatorId() != null
                && screen.getCreatorId().equals(user.getId());
    }

    public record PermissionSnapshot(boolean canRead, boolean canEdit, boolean canPublish, boolean canManage) {
        static PermissionSnapshot all() {
            return new PermissionSnapshot(true, true, true, true);
        }

        static PermissionSnapshot none() {
            return new PermissionSnapshot(false, false, false, false);
        }
    }

    public List<AnalyticsScreenAcl> parseEntriesFromBody(
            Long screenId,
            Long operatorId,
            com.fasterxml.jackson.databind.JsonNode body) {
        List<AnalyticsScreenAcl> result = new ArrayList<>();
        if (body == null || !body.has("entries") || !body.path("entries").isArray()) {
            return result;
        }

        for (com.fasterxml.jackson.databind.JsonNode item : body.path("entries")) {
            String subjectType = normalize(item.path("subjectType").asText(null));
            String subjectId = item.path("subjectId").asText(null);
            String perm = normalize(item.path("perm").asText(null));
            if (!isValidSubjectType(subjectType) || subjectId == null || subjectId.isBlank() || !isValidPerm(perm)) {
                continue;
            }
            AnalyticsScreenAcl acl = new AnalyticsScreenAcl();
            acl.setScreenId(screenId);
            acl.setSubjectType(subjectType);
            acl.setSubjectId(subjectId.trim());
            acl.setPerm(perm);
            acl.setCreatorId(operatorId);
            result.add(acl);
        }
        return result;
    }
}
