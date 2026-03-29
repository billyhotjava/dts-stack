# Screen Permission System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Screens are private to their creator by default. Creator (OWNER) can assign managers (MANAGE) and share read access (READ). Public links require login + authorization.

**Architecture:** Modify existing `ScreenAclService` — update Permission enum (add OWNER, remove EDIT/PUBLISH), remove default role grants, add validation rules. Update `ScreenResource` endpoints for OWNER-only delete and ACL validation. Update frontend ACL panel and public screen page.

**Tech Stack:** Java 21, Spring Boot, JPA, React, TypeScript, Liquibase (data migration only)

---

### Task 1: Update ScreenAclService Permission Model

**Files:**
- Modify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ScreenAclService.java`

- [ ] **Step 1: Update Permission enum and constants**

Replace lines 26-42:

```java
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
```

- [ ] **Step 2: Update PermissionSnapshot to include canDelete and isOwner**

Replace the record at line 217:

```java
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
```

- [ ] **Step 3: Rewrite snapshot() method**

Replace lines 50-68:

```java
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
```

- [ ] **Step 4: Update hasPermission() to use new enum**

Replace lines 70-79:

```java
@Transactional(readOnly = true)
public boolean hasPermission(AnalyticsScreen screen, AnalyticsUser user, PlatformContext context, Permission permission) {
    PermissionSnapshot snap = snapshot(screen, user, context);
    return switch (permission) {
        case READ -> snap.canRead();
        case MANAGE -> snap.canManage();
        case OWNER -> snap.isOwner();
    };
}
```

- [ ] **Step 5: Replace ensureCreatorManage with ensureCreatorOwner, delete ensureDefaultReadRoles**

Remove `ensureCreatorManage()` (lines 81-96) and `ensureDefaultReadRoles()` (lines 98-129). Remove `DEFAULT_READ_ROLES` constant (lines 35-39). Add:

```java
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
```

- [ ] **Step 6: Replace resolveGrantedPerms with resolveHighestPerm**

Replace the private method (lines 167-190):

```java
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
```

- [ ] **Step 7: Update replaceEntries with OWNER validation**

Replace lines 136-150:

```java
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
```

- [ ] **Step 8: Update isValidPerm**

```java
public boolean isValidPerm(String perm) {
    return VALID_PERMS.contains(normalize(perm));
}
```

- [ ] **Step 9: Remove @Deprecated annotation from class**

Remove lines 17-21 (`@Deprecated` annotation and javadoc). This service is no longer deprecated — it's the primary permission system.

- [ ] **Step 10: Compile**

Run: `cd /opt/prod/s10/s10-stack/source/dts-analytics && ./mvnw compile -q -T 1C`

Fix any compile errors from callers that reference removed methods/fields.

---

### Task 2: Update ScreenResource Endpoints

**Files:**
- Modify: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ScreenResource.java`

- [ ] **Step 1: Update screen creation to use ensureCreatorOwner**

In the `POST /api/screens` handler (around line 772), find calls to `ensureCreatorManage` and `ensureDefaultReadRoles`. Replace with:

```java
screenAclService.ensureCreatorOwner(screen);
```

Remove any call to `ensureDefaultReadRoles`.

- [ ] **Step 2: Update screen deletion to check OWNER**

In the `DELETE /api/screens/{id}` handler (around line 1015), change the permission check from MANAGE to OWNER:

```java
if (!screenAclService.hasPermission(screen, user.get(), platformContext, ScreenAclService.Permission.OWNER)) {
    return ResponseEntity.status(403).contentType(MediaType.APPLICATION_JSON).body(
        objectMapper.createObjectNode().put("error", "Only the owner can delete this screen"));
}
```

- [ ] **Step 3: Update ACL update endpoint with OWNER validation**

In `PUT /api/screens/{id}/acl` (around line 736), pass `isOwner` to `replaceEntries`:

```java
ScreenAclService.PermissionSnapshot perms = screenAclService.snapshot(screen, user.get(), platformContext);
if (!perms.canManage()) {
    return ResponseEntity.status(403).build();
}
screenAclService.replaceEntries(screen, user.get().getId(), entries, perms.isOwner());
```

- [ ] **Step 4: Update permission snapshot response in list/detail endpoints**

Find where `PermissionSnapshot` is serialized to JSON response (in list and detail endpoints). Add the new fields:

```java
node.put("canDelete", perms.canDelete());
node.put("isOwner", perms.isOwner());
```

- [ ] **Step 5: Update public screen endpoint to require auth + ACL**

In the public screen endpoint handler, add before returning screen data:

```java
// Public link now requires authentication
if (user.isEmpty()) {
    return ResponseEntity.status(401).contentType(MediaType.APPLICATION_JSON).body(
        objectMapper.createObjectNode().put("error", "Authentication required").put("loginUrl", "/"));
}
// Check READ permission
if (!screenAclService.hasPermission(screen, user.get(), platformContext, ScreenAclService.Permission.READ)) {
    return ResponseEntity.status(403).contentType(MediaType.APPLICATION_JSON).body(
        objectMapper.createObjectNode().put("error", "You do not have permission to view this screen"));
}
```

- [ ] **Step 6: Compile and verify**

Run: `cd /opt/prod/s10/s10-stack/source/dts-analytics && ./mvnw compile -q -T 1C`

---

### Task 3: Data Migration

**Files:**
- Create: `source/dts-analytics/src/main/resources/config/liquibase/changelog/0039_screen_acl_owner_migration.xml`
- Modify: `source/dts-analytics/src/main/resources/config/liquibase/master.xml`

- [ ] **Step 1: Create migration file**

```xml
<?xml version="1.0" encoding="utf-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <!-- Upgrade creator's MANAGE to OWNER -->
    <changeSet id="0039-01-creator-manage-to-owner" author="system">
        <sql>
            UPDATE analytics_screen_acl
            SET perm = 'OWNER', updated_at = NOW()
            WHERE subject_type = 'USER'
              AND perm = 'MANAGE'
              AND subject_id IN (
                  SELECT CAST(s.creator_id AS VARCHAR)
                  FROM analytics_screen s
                  WHERE s.id = analytics_screen_acl.screen_id
              );
        </sql>
    </changeSet>

    <!-- Migrate legacy EDIT/PUBLISH to MANAGE -->
    <changeSet id="0039-02-edit-publish-to-manage" author="system">
        <sql>
            UPDATE analytics_screen_acl
            SET perm = 'MANAGE', updated_at = NOW()
            WHERE perm IN ('EDIT', 'PUBLISH');
        </sql>
    </changeSet>

    <!-- Remove default role READ entries -->
    <changeSet id="0039-03-remove-default-role-reads" author="system">
        <sql>
            DELETE FROM analytics_screen_acl
            WHERE subject_type = 'ROLE'
              AND subject_id IN (
                  'ROLE_DEPT_LEADER',
                  'ROLE_DEPT_DATA_OWNER',
                  'ROLE_INST_DATA_OWNER',
                  'ROLE_INST_LEADER'
              );
        </sql>
    </changeSet>

</databaseChangeLog>
```

- [ ] **Step 2: Register in master.xml**

Add after the last include:

```xml
<include file="config/liquibase/changelog/0039_screen_acl_owner_migration.xml" relativeToChangelogFile="false"/>
```

- [ ] **Step 3: Compile**

Run: `cd /opt/prod/s10/s10-stack/source/dts-analytics && ./mvnw compile -q -T 1C`

---

### Task 4: Update Frontend ScreenAclPanel

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/components/ScreenAclPanel.tsx`
- Modify: `source/dts-analytics-webapp/modern/src/api/analyticsApi.ts` (ScreenAclEntry type)

- [ ] **Step 1: Update ScreenAclEntry type**

In `analyticsApi.ts`, update the `perm` type:

```typescript
export type ScreenAclEntry = {
    id?: number | string;
    screenId?: number | string;
    subjectType: "USER" | "ROLE";
    subjectId: string;
    perm: "READ" | "MANAGE" | "OWNER";
    creatorId?: number | string;
    createdAt?: string;
    updatedAt?: string;
};
```

- [ ] **Step 2: Rewrite ScreenAclPanel with OWNER handling**

Replace the full component to:
- Show OWNER entry as read-only row with "拥有者" badge (non-editable, non-deletable)
- Permission dropdown: only `MANAGE` and `READ` (OWNER not selectable)
- Accept `isOwner` prop to control whether MANAGE can be assigned
- Non-owner managers can only add/remove READ entries

```typescript
const ASSIGNABLE_PERMS: ScreenAclEntry['perm'][] = ['MANAGE', 'READ'];
const PERM_LABELS: Record<string, string> = {
    OWNER: '拥有者',
    MANAGE: '管理者',
    READ: '查看者',
};
```

OWNER rows rendered as:
```tsx
<div className="grid gap-2 mb-2" style={{ gridTemplateColumns: '120px 1fr 160px 68px' }}>
    <span className="text-xs text-text-muted px-2.5 py-1.5">USER</span>
    <span className="text-xs text-text-primary px-2.5 py-1.5">{row.subjectId}</span>
    <span className="text-xs font-semibold text-brand px-2.5 py-1.5">拥有者</span>
    <span />
</div>
```

Non-OWNER rows: editable select + delete button, but if `!isOwner`, permission dropdown only shows `READ`.

- [ ] **Step 3: Compile frontend**

Run: `cd /opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern && npx tsc --noEmit`

---

### Task 5: Update Frontend PublicScreenPage

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/PublicScreenPage.tsx`

- [ ] **Step 1: Add auth check on load**

At the top of the component, before fetching screen data, check if user is authenticated:

```typescript
const [authError, setAuthError] = useState<'not-authenticated' | 'forbidden' | null>(null);

// In the fetch useEffect, handle 401/403:
.catch((e) => {
    if (e instanceof HttpError && e.status === 401) {
        setAuthError('not-authenticated');
    } else if (e instanceof HttpError && e.status === 403) {
        setAuthError('forbidden');
    } else {
        setState({ state: "error", error: e });
    }
});
```

- [ ] **Step 2: Render auth error states**

Before the normal render:

```tsx
if (authError === 'not-authenticated') {
    return (
        <div className="flex flex-col items-center justify-center min-h-screen gap-4 p-8 text-center">
            <div className="text-4xl opacity-30">🔒</div>
            <h2 className="text-xl font-semibold">需要登录</h2>
            <p className="text-text-secondary">请先登录后再查看此大屏</p>
            <a href="/" className="px-4 py-2 rounded-md bg-brand text-white">返回登录</a>
        </div>
    );
}
if (authError === 'forbidden') {
    return (
        <div className="flex flex-col items-center justify-center min-h-screen gap-4 p-8 text-center">
            <div className="text-4xl opacity-30">🚫</div>
            <h2 className="text-xl font-semibold">无访问权限</h2>
            <p className="text-text-secondary">您没有权限查看此大屏，请联系大屏拥有者授权</p>
            <a href="/" className="px-4 py-2 rounded-md bg-brand text-white">返回首页</a>
        </div>
    );
}
```

- [ ] **Step 3: Compile frontend**

Run: `cd /opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern && npx tsc --noEmit`

---

### Task 6: Update ScreenHeader Delete Button

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/components/ScreenHeader.tsx`

- [ ] **Step 1: Update permissions usage**

Find where `permissions` object is used. The `PermissionSnapshot` from the API now includes `canDelete` and `isOwner`. Find the delete action handler and guard with `canDelete`:

Search for the delete menu item or button. Replace `canManage` check with `canDelete`:

```typescript
// Before: disabled={!permissions.canManage}
// After:
disabled={!permissions.canDelete}
```

- [ ] **Step 2: Pass isOwner to ScreenAclPanel**

Where `<ScreenAclPanel>` is rendered, add `isOwner` prop:

```tsx
<ScreenAclPanel
    open={showAclPanel}
    screenId={id}
    onClose={() => setShowAclPanel(false)}
    isOwner={permissions.isOwner}
/>
```

- [ ] **Step 3: Compile frontend**

Run: `cd /opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern && npx tsc --noEmit`

---

### Task 7: Final Verification

- [ ] **Step 1: Full backend compile**

Run: `cd /opt/prod/s10/s10-stack/source/dts-analytics && ./mvnw compile -q -T 1C`
Expected: no errors

- [ ] **Step 2: Full frontend compile**

Run: `cd /opt/prod/s10/s10-stack/source/dts-analytics-webapp/modern && npx tsc --noEmit`
Expected: no errors

- [ ] **Step 3: Update sprint worklog**

Update sprint-queue.md and create sprint-26 worklog entry for this feature.
