# 权限矩阵与错误码（Platform/Admin）

本文档汇总当前实现中的：登录准入、菜单可见性、接口权限、数据访问控制（RBAC×ABAC）以及统一错误码（dts-sec-0001..0010）。

## 1. 登录准入（前端守卫 + 环境口径）
- Admin 应用（管理端 UI）
  - 允许：`ROLE_SYS_ADMIN`，`ROLE_AUTH_ADMIN`，`ROLE_SECURITY_AUDITOR`
  - 禁止：`ROLE_OP_ADMIN`
  - 参考：`source/dts-admin-webapp/src/routes/components/login-auth-guard.tsx`，`source/dts-admin-webapp/src/store/userStore.ts`
- Platform 应用（业务端 UI）
  - 允许（prod 默认）：`ROLE_OP_ADMIN`
  - dev overlay 可放宽：`ROLE_USER, ROLE_OP_ADMIN`（联调用，见 `docker-compose.dev.yml` 的 `VITE_ALLOWED_LOGIN_ROLES`）
  - 显式禁止：`ROLE_SYS_ADMIN`，`ROLE_AUTH_ADMIN`，`ROLE_SECURITY_AUDITOR`
  - 参考：`source/dts-platform-webapp/src/routes/components/login-auth-guard.tsx`，`source/dts-platform-webapp/src/store/userStore.ts`
- 角色同义词（前端守卫已统一兼容）
  - `SYSADMIN → ROLE_SYS_ADMIN`，`AUTHADMIN → ROLE_AUTH_ADMIN`，`AUDITADMIN|ROLE_AUDITOR_ADMIN|SECURITYAUDITOR → ROLE_SECURITY_AUDITOR`，`OPADMIN → ROLE_OP_ADMIN`

## 2. 菜单可见性（端到端链路）
- 前端：登录后若菜单为空，调用后端 `/api/menu/tree` 拉取菜单树，存入 store；展示时前端仅显示“被后端授权的路径/代码”的导航项。
  - 参考：
    - 拉取：`source/dts-platform-webapp/src/routes/components/login-auth-guard.tsx`
    - 存储：`source/dts-platform-webapp/src/store/menuStore.ts`
    - 过滤逻辑：`source/dts-platform-webapp/src/layouts/dashboard/nav/nav-data/index.ts`
- Platform 后端：
  - 接口：`GET /api/menu` 与 `GET /api/menu/tree`（`source/dts-platform/.../web/rest/BasicApiResource.java`）
  - 聚合：从 SecurityContext 提取当前用户角色与权限，调用 Admin 的菜单服务（带 audience hints）后再映射/扁平化（`PortalMenuService`）
- Admin 后端（权威菜单 + 受众过滤）：
  - 接口：`GET /api/menu`（`source/dts-admin/.../web/rest/BasicApiResource.java`）
  - 数据模型：`PortalMenu` + `PortalMenuVisibility(role, permission, dataLevel)`
  - 过滤：`findTreeForAudience(roleCodes, permissionCodes, dataLevel)`，`ROLE_OP_ADMIN` 全量可见；无显式可见性时用默认兜底角色（`ROLE_SYS_ADMIN/ROLE_AUTH_ADMIN/ROLE_SECURITY_AUDITOR/ROLE_OP_ADMIN/ROLE_USER`）
  - 菜单板块的默认映射（用于批量同步可见性）：
    - 基础只读：`catalog`/`explore`/`visualization`
    - 写/导出叠加：`modeling`/`governance`/`services`
    - 研究所范围叠加：`foundation`
    - 所有者叠加：`iam`
  - 注：治理三员（SYS/AUTH/AUDITOR）在服务侧具备可见性“旁路”能力，但被前端登录守卫禁止进入 Platform。

## 3. 接口权限（后端 RBAC）
- Platform 后端 `SecurityConfiguration`：
  - 放行：`/api/menu**`，`/api/keycloak/auth/**`，`/api/keycloak/localization/**`
  - 平台不再提供 `/api/admin/**` 管理接口；其余 `/api/**` 需已认证
  - 参考：`source/dts-platform/src/main/java/com/yuzhi/dts/platform/config/SecurityConfiguration.java`

## 4. 数据访问（RBAC×ABAC 判定骨架）
- 判定顺序：RBAC → Scope → Level → 例外/白名单
- 动作上限（RBAC Gate）：
  - `MANAGE` 需 OWNER；`WRITE` 需 EDITOR+；`READ` 需 VIEWER+
- 作用域门（Scope Gate）：
  - `active_scope=DEPT`：资源须为 DEPT 域，且 `owner_dept==active_dept`
  - `active_scope=INST`：资源须为 INST 域，且 `share_scope ∈ {SHARE_INST,PUBLIC_INST}`
- 密级门（Level Gate）：
  - `personnel_level_rank >= data_level_rank`
- 前端上下文注入：所有（非鉴权）请求自动添加 `X-Active-Scope`，`X-Active-Dept`（参考 `source/dts-platform-webapp/src/api/apiClient.ts`）
- Token/Claims（规划）：`dept_code`、`person_security_level`、`roles_scoped`、`dept_list`（见 `worklog/dts-system-20251007.log`）

### 4.1 常见情景决策表（ABAC 判定示例）

术语对应：
- 用户身份：`<ROLE>@<DEPT>` 与人员密级 `personnel`（映射到 `personnel_level`）
- active_scope/dept：由前端上下文传入 `X-Active-Scope`、`X-Active-Dept`
- 资源：`scope`（DEPT/INST）、`share`（PRIVATE_DEPT/SHARE_INST/PUBLIC_INST）、`owner_dept`、`level`（DATA_* 或 legacy）

| 场景 | 用户身份   | active_scope/dept | 资源(scope, share, owner_dept, level)  | 动作   | 结果     | 说明                                   |
| -- | ------------------------------ | ----------------- | ----------------------------------- | ------ | ----------- | -------------------------------------- |
| A  | DEPT_VIEWER@D001, personnel=IMPORTANT | DEPT/D001  | (DEPT, PRIVATE_DEPT, D001, INTERNAL) | READ   | ✅         | 同域、同部门、密级不超                         |
| B  | DEPT_EDITOR@D001                      | DEPT/D001  | (DEPT, PRIVATE_DEPT, D001, SECRET)   | WRITE  | ❌* 或 ✅* | 取决于 personnel_level ≥ SECRET            |
| C  | DEPT_OWNER@D001                       | DEPT/D001  | (DEPT, PRIVATE_DEPT, D002, INTERNAL) | MANAGE | ❌         | 非本部门资源（SCOPE_MISMATCH → dts-sec-0002） |
| D  | INST_VIEWER, personnel=CORE           | INST/-     | (INST, SHARE_INST, -, TOP_SECRET)    | READ   | ✅         | 所共享域、密级满足                            |
| E  | INST_EDITOR, personnel=IMPORTANT      | INST/-     | (INST, SHARE_INST, -, SECRET)        | WRITE  | ❌         | 人员密级不足（LEVEL_TOO_LOW → dts-sec-0003）  |
| F  | DEPT_EDITOR@D001 + INST_VIEWER        | INST/-     | (INST, SHARE_INST, -, INTERNAL)      | READ   | ✅          | 跨域并存，切到 INST 生效                      |

注释：
- 场景 B：若 `personnel_level < SECRET` 则返回 `dts-sec-0003`；若 RBAC 不具备 `WRITE`，则先以 `dts-sec-0001` 拒绝。
- 场景 C：DEPT 门失败，建议返回 `dts-sec-0002`（或对外表现为 404 以避免信息泄露）。
- 场景 D/E/F：INST 门仅允许 `share ∈ {SHARE_INST, PUBLIC_INST}` 的资源。
- Legacy 兼容：当资源缺少 `scope/owner_dept/share_scope/data_level` 或出现非法组合（如 `INST + PRIVATE_DEPT`）时，按“安全第一”返回 `dts-sec-0009`，并安排数据整改/迁移（新系统默认无存量数据）。

## 5. 统一错误码（dts-sec-0001..0010）
> 平台与公共模块已统一定义：
> - `source/dts-platform/src/main/java/com/yuzhi/dts/platform/security/policy/PolicyErrorCodes.java`
> - `source/dts-common/src/main/java/com/yuzhi/dts/common/security/policy/PolicyErrorCodes.java`

- dts-sec-0001 `RBAC_DENY`（403）
  - 含义：动作权限不足（需更高能力，例如 WRITE/MANAGE）
  - 建议提示：请联系管理员申请更高权限
- dts-sec-0002 `SCOPE_MISMATCH`（403）
  - 含义：作用域/部门不匹配
  - 建议提示：请在右上角切换“作用域/部门”后重试
- dts-sec-0003 `LEVEL_TOO_LOW`（403）
  - 含义：人员密级低于数据密级
  - 建议提示：您当前密级不足，无法访问该资源
- dts-sec-0004 `TEMP_PERMIT_REQUIRED`（403）
  - 含义：需临时授权/白名单
  - 建议提示：请发起临时授权申请，获批后访问
- dts-sec-0005 `CONTEXT_REQUIRED`（400）
  - 含义：缺少 `X-Active-Scope`/`X-Active-Dept`
  - 建议提示：请设置活跃作用域/部门后重试
- dts-sec-0006 `INVALID_CONTEXT`（400）
  - 含义：非法上下文（例如 active_dept 不在用户所属部门列表）
  - 建议提示：请检查上下文设置或联系管理员
- dts-sec-0007 `RESOURCE_NOT_VISIBLE`（404）
  - 含义：资源不存在或不可见（为避免信息泄露，推荐返回 404）
- dts-sec-0008 `ENGINE_FILTER_ERROR`（500）
  - 含义：行级过滤拼接/执行失败
- dts-sec-0009 `POLICY_CONFIG_MISSING`（500）
  - 含义：资源缺失必需策略属性（`scope/owner_dept/share_scope/data_level`）
- dts-sec-0010 `TOKEN_CLAIMS_MISSING`（401）
  - 含义：令牌缺少必要 claims（如 `dept_code/personnel_level`）或无效

前端默认对 0002/0003/0005/0006 提供友好提示（`source/dts-platform-webapp/src/api/apiClient.ts`）。

## 6. 生产 vs 开发 口径（统一收敛）
- 前端守卫：`VITE_ENABLE_FE_GUARD=true`（dev/prod 一致），`VITE_ALLOWED_LOGIN_ROLES=ROLE_OP_ADMIN`。
- 登录：Platform 仅允许 `ROLE_OP_ADMIN`；Admin 仅允许三员进入（与登录守卫一致）。
- `/api/menu**`：首屏放行仅限导航树加载；认证后由上下文进行可见性过滤。

## 7. 手工验证清单
1) 登录准入
   - Admin：`sysadmin/authadmin/auditadmin` ✓，`opadmin` ✗
   - Platform（prod 口径）：`opadmin` ✓，`sysadmin/authadmin/auditadmin` ✗
2) 菜单可见性
   - 以 `opadmin` 登录 Platform，侧边栏应含基础+写入板块（catalog/explore/visualization/modeling/governance/services/foundation/iam）
   - 以普通 `ROLE_USER`（dev overlay）登录，仅展示基础只读板块
3) 接口权限
   - 未登录访问 `/api/menu`✓；访问任一业务 `/api/**` → 401
   - 登录普通用户访问 `/api/admin/**` → 403（需 OP_ADMIN/ADMIN）
4) ABAC 门
   - 切换 `X-Active-Scope=DEPT` 且访问非本部门资源 → `dts-sec-0002`
   - 以 `personnel_level=GENERAL` 访问 `data_level=SECRET` → `dts-sec-0003`
   - 缺失上下文头访问 → `dts-sec-0005/0006`
5) 错误码提示
   - 触发 0002/0003/0005/0006 时，前端 toast 文案包含中文引导提示

---
如需扩展：可在 Admin 侧添加“角色→板块”可视化同步界面，驱动 `PortalMenuVisibility` 的批量计算与审计落库；并在 Platform 侧增加 ABAC 判定结果与上下文的审计日志查询入口。
