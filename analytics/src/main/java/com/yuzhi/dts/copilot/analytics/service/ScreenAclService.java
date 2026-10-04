package com.yuzhi.dts.copilot.analytics.service;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreenAcl;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsScreenAclRepository;
import com.yuzhi.dts.copilot.analytics.web.support.PlatformContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private static final Set<String> VALID_SUBJECT_TYPES = Set.of(SUBJECT_TYPE_USER, SUBJECT_TYPE_ROLE);
    private static final Set<String> VALID_PERMS = Set.of("READ", "EDIT", "PUBLISH", "MANAGE");

    private final AnalyticsScreenAclRepository screenAclRepository;

    public ScreenAclService(AnalyticsScreenAclRepository screenAclRepository) {
        this.screenAclRepository = screenAclRepository;
    }

    @Transactional(readOnly = true)
    public PermissionSnapshot snapshot(AnalyticsScreen screen, AnalyticsUser user, PlatformContext context) {
        if (user == null) {
            return PermissionSnapshot.none();
        }
        if (user.isSuperuser() || isCreator(screen, user)) {
            return PermissionSnapshot.all();
        }

        Set<String> granted = resolveGrantedPerms(screen.getId(), user.getId(), context == null ? null : context.roles());
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

    private Set<String> resolveGrantedPerms(Long screenId, Long userId, String rolesHeader) {
        List<AnalyticsScreenAcl> entries = screenAclRepository.findAllByScreenIdOrderByIdAsc(screenId);
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
