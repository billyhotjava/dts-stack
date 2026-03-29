# Screen Permission System Design

**Date**: 2026-03-29
**Status**: Approved
**Module**: dts-analytics (backend + frontend)

## Overview

Implement screen-level permission control: screens are private to their creator by default. The creator can assign a manager (equal rights minus deletion/ownership transfer) and both can share read-only access to other users. Public links require login + authorization.

## Permission Model

### Roles

| Role | ACL perm value | How assigned |
|------|---------------|-------------|
| Owner | `OWNER` | Auto-assigned to creator on screen creation. One per screen. Cannot be transferred. |
| Manager | `MANAGE` | Assigned by Owner via ACL panel. Multiple allowed. |
| Viewer | `READ` | Assigned by Owner or Manager via ACL panel. Multiple allowed. |

### Permission Matrix

| Capability | OWNER | MANAGE | READ | Unauthorized |
|-----------|-------|--------|------|-------------|
| View screen | Y | Y | Y | N |
| Edit screen | Y | Y | N | N |
| Publish version | Y | Y | N | N |
| Manage ACL | Y | Y | N | N |
| Share (grant READ) | Y | Y | N | N |
| Generate public link | Y | Y | N | N |
| Delete screen | Y | N | N | N |
| Assign Manager | Y | N | N | N |
| View via public link | Y (login required) | Y | Y | N |

### Hierarchy

`OWNER` implies all of `MANAGE`. `MANAGE` implies `READ`. Permission checks use: `userPerm >= requiredPerm`.

### Changes from current system

1. **Remove** `EDIT` and `PUBLISH` permission levels (merged into `MANAGE`)
2. **Add** `OWNER` permission level (above `MANAGE`)
3. **Remove** `ensureDefaultReadRoles()` — no automatic role-based READ grants
4. **Public links** require login + READ permission (no more anonymous access)

## Data Model

### analytics_screen_acl (existing table, no schema change)

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | |
| screen_id | BIGINT FK | |
| subject_type | VARCHAR(16) | `USER` or `ROLE` |
| subject_id | VARCHAR(128) | user ID or role name |
| perm | VARCHAR(16) | `OWNER`, `MANAGE`, or `READ` |
| creator_id | BIGINT | who created this entry |
| created_at | TIMESTAMP | |
| updated_at | TIMESTAMP | |

No schema migration needed. Only the allowed `perm` values change.

## Backend Changes

### ScreenAclService.java

1. **Permission enum**: Add `OWNER` above `MANAGE`, remove `EDIT` and `PUBLISH`
   ```
   OWNER(40) > MANAGE(30) > READ(10)
   ```

2. **Remove** `ensureDefaultReadRoles()` — delete completely

3. **Modify** `ensureCreatorManage()` → rename to `ensureCreatorOwner()`
   - Insert `(screen_id, USER, creator_id, OWNER)` instead of `MANAGE`

4. **Modify** `snapshot()` return:
   - `canRead`: perm >= READ
   - `canEdit`: perm >= MANAGE
   - `canPublish`: perm >= MANAGE
   - `canManage`: perm >= MANAGE
   - `canDelete`: perm >= OWNER (NEW)
   - `isOwner`: perm == OWNER (NEW)

5. **Modify** `replaceEntries()` validation:
   - Non-OWNER cannot grant `MANAGE` or `OWNER`
   - Cannot remove the OWNER entry
   - Cannot have multiple OWNER entries

### ScreenResource.java

1. **Create** (`POST /api/screens`):
   - Call `ensureCreatorOwner(screen)` instead of `ensureCreatorManage()`
   - Do NOT call `ensureDefaultReadRoles()`

2. **Delete** (`DELETE /api/screens/{id}`):
   - Check OWNER permission (not just MANAGE)

3. **ACL update** (`PUT /api/screens/{id}/acl`):
   - Validate: only OWNER can assign MANAGE
   - Validate: OWNER entry cannot be removed or changed

4. **List** (`GET /api/screens`):
   - No change needed (already filtered by AssetListFilterService)

5. **Public link access** (public screen endpoint):
   - Add authentication check (must be logged in)
   - Add ACL permission check (must have READ)

### PublicScreenPage backend

The public screen endpoint currently allows anonymous access. Change to:
- Extract user from session/token
- If not authenticated → return 401 with login redirect URL
- If authenticated → check `screenAclService.hasPermission(screen, user, READ)`
- If no permission → return 403

## Frontend Changes

### ScreenAclPanel.tsx

1. Permission dropdown options: `MANAGE` and `READ` only (OWNER is system-assigned, not user-selectable)
2. Show OWNER entry as read-only (non-editable, non-deletable) with "Owner" badge
3. Only show "Add Manager" option if current user is OWNER
4. Non-OWNER managers can only add/remove READ entries

### PublicScreenPage.tsx

1. On load, check if user is authenticated (check session/token)
2. If not authenticated → redirect to login page with return URL
3. If authenticated but 403 → show "no permission" page
4. Remove any anonymous/unauthenticated viewing logic

### ScreenHeader.tsx

1. "Delete" action: only show if `permissions.canDelete` (new field)
2. "Security" panel: show OWNER badge next to creator name

### ScreensPage.tsx

1. No change needed (backend filtering already handles visibility)

## Migration

Existing screens with `MANAGE` permission for the creator need migration to `OWNER`:

```sql
UPDATE analytics_screen_acl
SET perm = 'OWNER'
WHERE subject_type = 'USER'
  AND perm = 'MANAGE'
  AND subject_id = (
    SELECT CAST(s.creator_id AS VARCHAR)
    FROM analytics_screen s
    WHERE s.id = analytics_screen_acl.screen_id
  );
```

Existing `EDIT` and `PUBLISH` entries migrate to `MANAGE`:

```sql
UPDATE analytics_screen_acl
SET perm = 'MANAGE'
WHERE perm IN ('EDIT', 'PUBLISH');
```

Existing default role READ entries (from `ensureDefaultReadRoles`) should be removed:

```sql
DELETE FROM analytics_screen_acl
WHERE subject_type = 'ROLE'
  AND subject_id IN ('ROLE_DEPT_LEADER', 'ROLE_DEPT_DATA_OWNER', 'ROLE_INST_DATA_OWNER', 'ROLE_INST_LEADER');
```

## Non-goals

- No changes to platform `asset_grant` table integration
- No changes to `ScreenEditLock` system
- No changes to audit logging (existing audit covers ACL operations)
- No changes to card/dashboard permissions (this design is screen-specific)
