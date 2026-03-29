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

@Service
@Transactional
public class ScreenAclService {

    public enum Permission {
        READ(10),
        MANAGE(30),
        OWNER(40);

        private final int level;
        Permission(int level) { this.level = level; }
        public int level() { return level; }
        public boolean implies(Permission other) { return this.level >= other.level; }
    }

    public static final String SUBJECT_TYPE_USER = "USER";
    public static final String SUBJECT_TYPE_ROLE = "ROLE";

    private static final Set<String> VALID_SUBJECT_TYPES = Set.of(SUBJECT_TYPE_USER, SUBJECT_TYPE_ROLE);
    private static final Set<String> VALID_PERMS = Set.of("READ", "MANAGE", "OWNER");

    private final AnalyticsScreenAclRepository screenAclRepository;

    public ScreenAclService(AnalyticsScreenAclRepository screenAclRepository) {
        this.screenAclRepository = screenAclRepository;
    }

    public PermissionSnapshot snapshot(AnalyticsScreen screen, AnalyticsUser user, PlatformContext context) {
        if (user == null) {
            return PermissionSnapshot.none();
        }
        if (user.isSuperuser()) {
            return PermissionSnapshot.all();
        }
        if (isCreator(screen, user)) {
            ensureCreatorOwner(screen);
            return PermissionSnapshot.all();
        }

        Permission highest = resolveHighestPerm(
                screenAclRepository.findAllByScreenIdOrderByIdAsc(screen.getId()),
                user.getId(),
                context == null ? null : context.roles());
        if (highest == null) {
            return PermissionSnapshot.none();
        }
        return PermissionSnapshot.forPerm(highest);
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(AnalyticsScreen screen, AnalyticsUser user, PlatformContext context, Permission permission) {
        PermissionSnapshot snap = snapshot(screen, user, context);
        return switch (permission) {
            case READ -> snap.canRead();
            case MANAGE -> snap.canManage();
            case OWNER -> snap.isOwner();
        };
    }

    public void ensureCreatorOwner(AnalyticsScreen screen) {
        if (screen == null || screen.getId() == null || screen.getCreatorId() == null) {
            return;
        }
        String creatorId = String.valueOf(screen.getCreatorId());
        boolean hasOwner = screenAclRepository.existsByScreenIdAndSubjectTypeAndSubjectIdAndPerm(
                screen.getId(), SUBJECT_TYPE_USER, creatorId, "OWNER");
        if (!hasOwner) {
            // Also check for legacy MANAGE entry from creator
            boolean hasManage = screenAclRepository.existsByScreenIdAndSubjectTypeAndSubjectIdAndPerm(
                    screen.getId(), SUBJECT_TYPE_USER, creatorId, "MANAGE");
            if (hasManage) {
                // Upgrade MANAGE to OWNER
                List<AnalyticsScreenAcl> entries = screenAclRepository.findAllByScreenIdOrderByIdAsc(screen.getId());
                for (AnalyticsScreenAcl entry : entries) {
                    if (SUBJECT_TYPE_USER.equals(normalize(entry.getSubjectType()))
                            && creatorId.equals(entry.getSubjectId())
                            && "MANAGE".equals(normalize(entry.getPerm()))) {
                        entry.setPerm("OWNER");
                        screenAclRepository.save(entry);
                        return;
                    }
                }
            }
            AnalyticsScreenAcl acl = new AnalyticsScreenAcl();
            acl.setScreenId(screen.getId());
            acl.setSubjectType(SUBJECT_TYPE_USER);
            acl.setSubjectId(creatorId);
            acl.setPerm("OWNER");
            acl.setCreatorId(screen.getCreatorId());
            screenAclRepository.save(acl);
        }
    }

    @Transactional(readOnly = true)
    public List<AnalyticsScreenAcl> listEntries(Long screenId) {
        return screenAclRepository.findAllByScreenIdOrderByIdAsc(screenId);
    }

    public void replaceEntries(AnalyticsScreen screen, Long operatorId, List<AnalyticsScreenAcl> entries, boolean isOwner) {
        // Preserve the OWNER entry — never delete it
        List<AnalyticsScreenAcl> existingEntries = screenAclRepository.findAllByScreenIdOrderByIdAsc(screen.getId());
        AnalyticsScreenAcl ownerEntry = null;
        for (AnalyticsScreenAcl existing : existingEntries) {
            if ("OWNER".equals(normalize(existing.getPerm()))) {
                ownerEntry = existing;
                break;
            }
        }

        // Validation: non-owner cannot grant MANAGE or OWNER
        if (!isOwner && entries != null) {
            for (AnalyticsScreenAcl entry : entries) {
                String perm = normalize(entry.getPerm());
                if ("OWNER".equals(perm) || "MANAGE".equals(perm)) {
                    throw new IllegalArgumentException("Only the owner can assign MANAGE permission");
                }
            }
        }

        // Remove incoming OWNER entries (OWNER is system-managed)
        List<AnalyticsScreenAcl> filtered = new ArrayList<>();
        if (entries != null) {
            for (AnalyticsScreenAcl entry : entries) {
                if (!"OWNER".equals(normalize(entry.getPerm()))) {
                    filtered.add(entry);
                }
            }
        }

        screenAclRepository.deleteAllByScreenId(screen.getId());

        // Re-insert OWNER
        if (ownerEntry != null) {
            ownerEntry.setId(null);
            screenAclRepository.save(ownerEntry);
        } else {
            ensureCreatorOwner(screen);
        }

        // Insert non-OWNER entries
        for (AnalyticsScreenAcl entry : filtered) {
            entry.setId(null);
            entry.setScreenId(screen.getId());
            if (entry.getCreatorId() == null) {
                entry.setCreatorId(operatorId);
            }
        }
        if (!filtered.isEmpty()) {
            screenAclRepository.saveAll(filtered);
        }
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

    private Permission resolveHighestPerm(Collection<AnalyticsScreenAcl> entries, Long userId, String rolesHeader) {
        if (entries.isEmpty()) {
            return null;
        }
        Set<String> roleSet = parseRoles(rolesHeader);
        String uid = String.valueOf(userId);
        Permission highest = null;
        for (AnalyticsScreenAcl entry : entries) {
            String subjectType = normalize(entry.getSubjectType());
            String subjectId = entry.getSubjectId() == null ? "" : entry.getSubjectId().trim();
            String perm = normalize(entry.getPerm());
            if (perm == null || !VALID_PERMS.contains(perm)) {
                continue;
            }
            boolean matches = (SUBJECT_TYPE_USER.equals(subjectType) && uid.equals(subjectId))
                    || (SUBJECT_TYPE_ROLE.equals(subjectType) && roleSet.contains(subjectId));
            if (!matches) {
                continue;
            }
            try {
                Permission p = Permission.valueOf(perm);
                if (highest == null || p.level() > highest.level()) {
                    highest = p;
                }
            } catch (IllegalArgumentException ignored) {
                // skip unknown perm values (e.g. legacy EDIT/PUBLISH)
            }
        }
        return highest;
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

    public record PermissionSnapshot(
            boolean canRead, boolean canEdit, boolean canPublish,
            boolean canManage, boolean canDelete, boolean isOwner) {
        static PermissionSnapshot all() {
            return new PermissionSnapshot(true, true, true, true, true, true);
        }

        static PermissionSnapshot none() {
            return new PermissionSnapshot(false, false, false, false, false, false);
        }

        static PermissionSnapshot forPerm(Permission perm) {
            boolean owner = perm == Permission.OWNER;
            boolean manage = perm.implies(Permission.MANAGE);
            boolean read = perm.implies(Permission.READ);
            return new PermissionSnapshot(read, manage, manage, manage, owner, owner);
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
