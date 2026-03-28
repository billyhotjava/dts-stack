# Unified Asset Permission Design

> Status: Draft
> Date: 2026-03-28
> Scope: dts-admin, dts-platform, dts-analytics

## 1. Background & Problem

DTS 当前三个模块各自维护独立的权限体系：
- **dts-admin**: Keycloak RBAC + 自定义角色 + 角色分配
- **dts-platform**: `@PreAuthorize` + ABAC (AccessChecker) + 数据集显式授权
- **dts-analytics**: Metabase 遗留的 Group/Permissions Graph + Screen ACL + superuser 标志

数据从源系统进入 ODS 后，需要按部门隔离管理。当前缺乏统一的资产级权限控制，三模块权限模型不一致，analytics 的 `SecurityConfiguration` 甚至是 `.anyRequest().permitAll()`。

## 2. Requirements

### 2.1 Core Requirements

| # | 需求 | 说明 |
|---|------|------|
| R1 | 全资产部门隔离 | 表、卡片、仪表盘、大屏、模型全部按部门隔离 |
| R2 | 最小粒度授权 | 授权粒度到单个资产 |
| R3 | 资产归属管理 | 数据源默认部门 + 可手工调整单表归属；派生资产手工指定 |
| R4 | 部门管理员权限 | 完整管理本部门资产，但不含跨部门授权 |
| R5 | 跨部门授权 | 仅院级管理员可操作，当前纯线下 OA，未来预留内置审批 |
| R6 | 授权时效 | 支持永久或设有效期 |
| R7 | 统一权限源 | analytics 不再维护独立权限模型，统一到 platform |
| R8 | 普通员工显式授权 | 普通员工必须有显式授权才能访问资产（密级考虑） |

### 2.2 Role Permission Matrix

| 角色 | 本部门资产 | 其他部门资产 | 归属管理 | 跨部门授权 |
|------|-----------|-------------|---------|-----------|
| SYS_ADMIN / OP_ADMIN | MANAGE | MANAGE | Yes | Yes |
| INST_DATA_OWNER | MANAGE | MANAGE | Yes | Yes |
| INST_LEADER | READ | READ | No | No |
| DEPT_DATA_OWNER | MANAGE | 需显式授权 | 本部门 | No |
| DEPT_LEADER | READ | 需显式授权 | No | No |
| EMPLOYEE | 需显式授权 | 需显式授权 | No | No |

## 3. Solution: Platform-Centralized Asset Permission

### 3.1 Architecture

```
Browser → analytics 前端 → analytics 后端 ──→ platform 权限 API
                                                    │
Browser → platform 前端 → platform 后端 ────────────┘
                                                    │
                                              platform DB
                                          (asset_ownership,
                                           asset_grant,
                                           asset_permission_audit)
```

analytics 不再维护权限模型，所有权限判定回 platform 查询。analytics 侧加 Caffeine 本地缓存（30s TTL）减少调用频次。

### 3.2 Why This Approach

- 单一权限真相源，无同步不一致问题
- 单体部署（docker-compose），内部调用延迟极低
- analytics 遗留权限本来未生效，清理是还债
- 未来内置审批只需 platform 侧扩展

## 4. Data Model

### 4.1 `asset_ownership` — 资产归属

```sql
CREATE TABLE asset_ownership (
    id              BIGSERIAL PRIMARY KEY,
    asset_type      VARCHAR(32)  NOT NULL,  -- TABLE, CARD, DASHBOARD, SCREEN, MODEL
    asset_id        VARCHAR(128) NOT NULL,  -- 各模块中资产的原始 ID
    owner_dept_id   VARCHAR(64)  NOT NULL,  -- 归属部门 ID
    source_id       BIGINT,                 -- 数据源 ID（表类资产自动继承）
    assigned_by     VARCHAR(128),           -- 分配人（院级管理员 / SYSTEM）
    created_date    TIMESTAMP DEFAULT now(),
    modified_date   TIMESTAMP DEFAULT now(),
    UNIQUE (asset_type, asset_id)
);
```

**自动创建规则**：
- 数据源接入时绑定 `owner_dept_id`，Schema Sync 发现新表时自动创建记录（`ON CONFLICT DO NOTHING`，已手动调整过的不覆盖）
- 派生资产创建时由创建者手工指定归属部门（默认 = 创建者所在部门）

### 4.2 `asset_grant` — 资产授权

```sql
CREATE TABLE asset_grant (
    id              BIGSERIAL PRIMARY KEY,
    asset_type      VARCHAR(32)  NOT NULL,  -- TABLE, CARD, DASHBOARD, SCREEN, MODEL
    asset_id        VARCHAR(128) NOT NULL,
    grantee_type    VARCHAR(16)  NOT NULL,  -- USER, ROLE, DEPT
    grantee_id      VARCHAR(128) NOT NULL,  -- 用户名 / 角色名 / 部门 ID
    permission      VARCHAR(16)  NOT NULL,  -- READ, EDIT, MANAGE
    valid_from      TIMESTAMP,              -- NULL = 立即生效
    valid_to        TIMESTAMP,              -- NULL = 永久
    granted_by      VARCHAR(128) NOT NULL,  -- 授权人
    grant_reason    VARCHAR(512),           -- 备注（如 OA 审批单号）
    created_date    TIMESTAMP DEFAULT now(),
    UNIQUE (asset_type, asset_id, grantee_type, grantee_id, permission)
);
```

### 4.3 `asset_permission_audit` — 审计日志

```sql
CREATE TABLE asset_permission_audit (
    id              BIGSERIAL PRIMARY KEY,
    action          VARCHAR(32)  NOT NULL,  -- GRANT, REVOKE, CHANGE_OWNERSHIP
    asset_type      VARCHAR(32),
    asset_id        VARCHAR(128),
    target_user     VARCHAR(128),           -- 被授权/撤销的用户
    permission      VARCHAR(16),
    operator        VARCHAR(128) NOT NULL,  -- 操作人
    oa_reference    VARCHAR(128),           -- OA 单号
    detail          TEXT,                   -- JSON 详情
    created_date    TIMESTAMP DEFAULT now()
);
```

### 4.4 `infra_data_source` 扩展

```sql
ALTER TABLE infra_data_source ADD COLUMN owner_dept_id VARCHAR(64);
```

## 5. Permission Resolution Logic

```
check(user, roles, deptId, asset):
  1. if roles ∩ {SYS_ADMIN, OP_ADMIN}      → MANAGE
  2. if roles ∩ {INST_LEADER}               → READ
  3. if roles ∩ {INST_DATA_OWNER}           → MANAGE
  4. ownership = findOwnership(asset)
     if ownership.deptId == deptId:
       if roles ∩ {DEPT_LEADER}             → READ
       if roles ∩ {DEPT_DATA_OWNER}         → MANAGE
  5. grant = findActiveGrant(asset, user)
     if grant exists and grant.isValid()    → grant.permission
  6. → DENY
```

Permission hierarchy: `MANAGE > EDIT > READ > DENY`

Multiple grants for the same asset: take the highest permission.

## 6. Platform API Design

### 6.1 Internal Permission API (analytics → platform)

```
POST /api/internal/asset-permission/check
Header: X-DTS-Service: dts-analytics

Request:
{
    "username": "zhangsan",
    "userRoles": ["ROLE_DEPT_LEADER"],
    "userDeptId": "DEPT_003",
    "asset": { "type": "DASHBOARD", "id": "42" }
}

Response:
{
    "allowed": true,
    "permission": "READ",
    "reason": "dept_ownership"
}
```

```
POST /api/internal/asset-permission/batch-check
Header: X-DTS-Service: dts-analytics

Request:
{
    "username": "zhangsan",
    "userRoles": ["ROLE_DEPT_LEADER"],
    "userDeptId": "DEPT_003",
    "assets": [
        { "type": "CARD", "id": "1" },
        { "type": "CARD", "id": "2" }
    ]
}

Response:
{
    "results": {
        "CARD:1": { "allowed": true, "permission": "READ" },
        "CARD:2": { "allowed": false }
    }
}
```

```
POST /api/internal/asset-permission/accessible-ids
Header: X-DTS-Service: dts-analytics

Request:
{
    "username": "zhangsan",
    "userRoles": ["ROLE_DEPT_LEADER"],
    "userDeptId": "DEPT_003",
    "assetType": "CARD",
    "page": 0,
    "size": 100
}

Response:
{
    "assetIds": ["3", "7", "12", "15"],
    "total": 4
}
```

### 6.2 Asset Ownership Management API

```
GET    /api/asset-ownership?deptId=&assetType=&page=&size=
PUT    /api/asset-ownership/{id}
POST   /api/asset-ownership/batch
```

Authorization: INST_DATA_OWNER, SYS_ADMIN, OP_ADMIN

### 6.3 Asset Grant Management API

```
GET    /api/asset-grants?assetType=&assetId=
POST   /api/asset-grants
DELETE /api/asset-grants/{id}
GET    /api/asset-grants/my
GET    /api/asset-grants/granted-by-me
```

Authorization:
- POST (本部门用户): DEPT_DATA_OWNER 及以上
- POST (跨部门用户): INST_DATA_OWNER, SYS_ADMIN, OP_ADMIN
- DELETE: 原授权人或更高权限角色

## 7. Analytics Side Changes

### 7.1 Remove Legacy Permission Code

| Remove | File/Table | Replacement |
|--------|-----------|-------------|
| Permissions Graph | `analytics_permissions_graph` + entity | platform `asset_grant` |
| Group permissions | `AnalyticsGroup` + `AnalyticsGroupMembership` + `PermissionsResource` | platform role system |
| QueryPermissionService | NONE/LIMITED/FULL 3-level | platform permission API |
| Screen ACL | `analytics_screen_acl` + `ScreenAclService` | platform `asset_grant` |
| MetabaseAuth.requireSuperuser() | manual checks in controllers | unified permission filter |

### 7.2 New Permission Filter

```java
@Component
public class PlatformPermissionFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(request, response, chain) {
        String username = request.getHeader("X-DTS-User");
        String roles    = request.getHeader("X-DTS-Roles");
        String deptId   = request.getHeader("X-DTS-Dept");

        AssetRef asset = AssetRefResolver.resolve(request);

        if (asset != null) {
            PermissionResult result = platformPermissionClient.check(
                username, roles, deptId, asset);
            if (!result.isAllowed()) {
                response.sendError(403, "No permission for this asset");
                return;
            }
            request.setAttribute("assetPermission", result);
        }

        chain.doFilter(request, response);
    }
}
```

### 7.3 Asset List Filtering

Analytics queries platform for accessible asset IDs first, then queries local DB with `WHERE id IN (...)`. Avoids fetching unauthorized data.

### 7.4 Caching

```
Caffeine Cache:
    Single check:       key = "{username}:{assetType}:{assetId}", TTL = 30s, max = 10,000
    Accessible ID list: key = "{username}:{assetType}", TTL = 60s

Cache invalidation: platform increments X-Permission-Version header on changes,
    analytics clears cache on version mismatch.
```

## 8. Frontend Changes

### 8.1 Platform Frontend — New Pages

| Page | Route | Visible To |
|------|-------|-----------|
| Asset Ownership Management | `/governance/asset-ownership` | INST_DATA_OWNER, SYS_ADMIN, OP_ADMIN |
| Asset Grant Management | `/governance/asset-grants` | DEPT_DATA_OWNER and above |
| My Grants | `/my/asset-grants` | All authenticated users |
| Permission Audit Log | `/governance/permission-audit` | SECURITY_AUDITOR, SYS_ADMIN |

### 8.2 Platform Frontend — Modifications

- Data source create/edit form: add required "Owner Department" field
- Derived asset create dialogs (card, dashboard, screen, model): add "Owner Department" selector

### 8.3 Analytics Frontend — Removals

- Group/Membership management page
- Permissions Graph editor
- Screen ACL management dialog (migrated to platform)

### 8.4 Analytics Frontend — Modifications

- Asset lists: no change needed (backend returns filtered results)
- Asset detail 403: show permission placeholder ("No access — contact administrator")
- Asset create dialogs: add "Owner Department" selector calling platform API
- Dashboard detail: unauthorized cards show locked placeholder

## 9. Database Ownership

```
platform DB:
    + asset_ownership
    + asset_grant
    + asset_permission_audit
    ~ infra_data_source (add owner_dept_id column)

analytics DB:
    - analytics_permissions_graph (remove)
    - analytics_screen_acl (remove)
    ~ analytics_group (keep for migration, deprecate)
    ~ analytics_group_membership (keep for migration, deprecate)
```

## 10. Migration Strategy

```
Phase 1: Platform — build tables, deploy new APIs
    - Create asset_ownership, asset_grant, asset_permission_audit tables
    - Add owner_dept_id to infra_data_source
    - Deploy AssetPermissionService + REST endpoints
    - Deploy asset ownership / grant management UI

Phase 2: Data migration
    - Migrate Screen ACL → asset_grant
    - Migrate Permissions Graph → asset_grant
    - Backfill owner_dept_id for existing data sources
    - Generate asset_ownership records for existing assets

Phase 3: Analytics — integrate permission filter
    - Deploy PlatformPermissionClient + PlatformPermissionFilter
    - Modify asset list queries to use accessible ID filtering
    - Add department selector to asset creation dialogs
    - Add permission placeholder for unauthorized cards/dashboards

Phase 4: Cleanup
    - Remove analytics legacy permission code
    - Remove Permissions Graph editor, Group management UI, Screen ACL UI
    - Mark analytics_group / analytics_group_membership as deprecated
```

## 11. Future Extensibility

- **Built-in approval workflow**: Add `asset_access_request` table + state machine (PENDING → APPROVED/REJECTED) in platform, replacing offline OA process
- **Row-level security**: Existing RLS infrastructure (P3-12) can layer on top of asset-level permissions
- **Column masking**: Existing masking policies continue to work independently of asset ownership
