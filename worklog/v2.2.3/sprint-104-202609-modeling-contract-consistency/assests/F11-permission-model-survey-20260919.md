# F11 权限现状梳理与重构方案（2026-09-19）

**状态**：SURVEY（现状已冻结，重构方案待用户确认）
**范围**：dts-admin、dts-platform 的三员权限与普通用户权限
**基线提交**：`9bc3ec27a`
**证据口径**：以下结论均来自源码与测试环境实测（`/data/dts-stack` 运行库、S10 realm），不含推测。推测部分单独标注"待核实"。

---

## 一、角色与身份现状

### S1　角色分两处存放，且互不同步

| 角色 | 存放位置 | 是否在 Keycloak |
|---|---|---|
| ROLE_SYS_ADMIN、ROLE_AUTH_ADMIN、ROLE_SECURITY_AUDITOR、ROLE_OP_ADMIN | Keycloak realm 角色 | 是 |
| ROLE_INST_DATA_OWNER、ROLE_DEPT_DATA_OWNER、ROLE_INST_LEADER、ROLE_DEPT_LEADER | `dts_admin.admin_role_member` / `admin_role_assignment` | **否** |
| EMPLOYEE | 两处都有，且命名不一致（realm 为 `EMPLOYEE`，库内为 `ROLE_EMPLOYEE`） | 部分 |

实测 S10 realm 的全部非客户端角色：`default-roles-s10`、`EMPLOYEE`、`offline_access`、`ROLE_AUTH_ADMIN`、`ROLE_OP_ADMIN`、`ROLE_SECURITY_AUDITOR`、`ROLE_SYS_ADMIN`、`uma_authorization`。
实测 `admin_role_member`：`ROLE_INST_DATA_OWNER` 3 人、`ROLE_EMPLOYEE` 1 人。

授予入口 `AdminApiResource.applyRoleMemberMutations` 只写本地表，**从不回写 Keycloak**，走变更单审批后生效。可授予的内置数据角色见 `BUILTIN_DATA_ROLES`：DEPT_DATA_OWNER、INST_DATA_OWNER、INST_LEADER、DEPT_LEADER、EMPLOYEE。

### S2　角色成员以用户名字符串为键

`admin_role_member(role, username)` 唯一约束建在用户名上，不是 `kc_id`。Keycloak 侧改名或删号重建后，授权行成为孤儿，且可能被新的同名账号继承。`admin_role_assignment` 同样按 username。

### S3　`admin_keycloak_user` 是快照，且未回填

该表镜像 Keycloak 的 `realm_roles`、`group_paths`、`person_security_level`，`20260916_02` 又加了 `dept_code`/`dept_name`。实测 `xiezm` 行：`realm_roles=[]`、`dept_code` 为空、`last_sync_at=2026-09-14`。`e1d2e5917` 已把部门读取改到这份快照，但回填依赖一次 MDM 全量同步或快照刷新，**未回填前页面部门显示为空**。

### S4　`person_profile` 是冻结的事实源

自 `4d53821ba` 起 `PersonProfileService.upsert` 调用点归零，该表不再有写入方。当前仍被两处读取：组织删除保护（`OrganizationService.existsByDeptCodeIgnoreCase`）、建模目录的 `identity()` 部门与姓名解析。读到的是重构时刻的旧数据。

### S5　角色合并口径分散，已出过线上故障

- 登录链路（`KeycloakApiResource`）合并三处：Keycloak realm 角色 + `admin_role_assignment` + `admin_role_member`。
- F9 建模目录（`ModelingDirectoryResource.resolve`）原先只读 Keycloak realm 角色，导致所有数据管理员在 `/api/modeling/**` 全部 403（`MODELING_IDENTITY_DENIED`）。2026-09-16 实测复现，已由 `9bc3ec27a` 补合并修复。

没有统一的身份解析入口，每处调用各写一遍合并逻辑，同类缺陷还会复发。

---

## 二、判权机制现状

### S6　dts-platform 的模块级角色门是装饰性的

`security/AuthoritiesConstants` 里：

```java
public static final String[] CATALOG_MAINTAINERS   = DATA_MAINTAINER_ROLES;
public static final String[] GOVERNANCE_MAINTAINERS= DATA_MAINTAINER_ROLES;
public static final String[] IAM_MAINTAINERS       = DATA_MAINTAINER_ROLES;
public static final String[] INFRA_MAINTAINERS     = DATA_MAINTAINER_ROLES;
public static final String[] SERVICE_MAINTAINERS   = DATA_MAINTAINER_ROLES;
```

五个模块维护者常量全部是同一个数组的别名。平台共 508 处 `@PreAuthorize`，其中 `INFRA_MAINTAINER_EXPRESSION` 96 处、`GOVERNANCE_MAINTAINER_EXPRESSION` 94 处、`MODELING_MAINTAINER_EXPRESSION` 79 处、`CATALOG_MAINTAINER_EXPRESSION` 62 处——**看上去按模块分权，实际任一数据角色都能写全部模块**。真正做出区分的只有发布职责（`MODEL_RELEASE_REVIEWERS` 等）和所级/部门级两档。

### S7　平台存在不可授予角色的判权条件

`ROLE_GOV_ADMIN`、`ROLE_DATA_STEWARD`、`ROLE_INFRA_ADMIN` 出现在 catalog 的 5 个以上 Resource 的表达式中，但这三个角色既不在 Keycloak realm，也不在 `BUILTIN_DATA_ROLES`，**没有任何人能被授予**。这些条件恒假，实际放行靠同一表达式里的 `ROLE_ADMIN`/`ROLE_OP_ADMIN` 兜底。

### S8　dts-admin 保留历史角色别名

`SecurityConfiguration` 对 `/api/admin/**`、`/admin/**`、`/api/**` 放行时，除三员角色外还接受 `ROLE_AUDITOR_ADMIN`、`ROLE_AUDIT_ADMIN`、`ROLE_AUDITADMIN` 三个历史别名。别名来源是"遗留 realm/令牌"，当前 realm 已不存在，属于可收敛的放行面。

### S9　菜单不是权限边界

`/api/menu` 在 `SecurityConfiguration` 中 `permitAll`，且角色、权限码、密级三项都从查询参数取：

```java
Set<String> roleCodes = normalizeRoles(roleParams);
if (CollectionUtils.isEmpty(roleCodes)) roleCodes = rolesFromSecurityContext();
```

只有调用方不传 `roles` 时才回退安全上下文。也就是说任意调用方可以指定任意角色拉取菜单树。菜单只决定"看得见什么入口"，不构成访问控制；后端接口另有角色门，所以目前不是越权漏洞，但**"角色绑定菜单"完全不等于 RBAC**——这一点与用户的判断一致。

`portal_menu_visibility.permission_code` 字段存在且参与过滤（`matchesPermission`），但全仓库没有任何写入方，也没有权限码的定义来源，等于一个空壳维度。

### S10　对象级授权有 6 套并行实现，没有统一决策点

| 机制 | owner | 覆盖范围 |
|---|---|---|
| `AccessChecker`（部门 + 密级 + `CatalogDatasetGrant`） | `service/security` | 数据集读取/动作，43 个文件引用 |
| `AssetGrant` + `AssetPermissionService` | `service/permission` | 大屏/资产共享（F9 之前的共享模型） |
| `IamAssetActionPolicy` + `AssetActionPolicyEvaluator` | `service/security` | 资产动作策略 |
| `IamDatasetPolicy` + `PolicyService` | `service/iam` | 数据集策略、物理预览 |
| `CatalogDomainAccessPolicy` | catalog | 域级访问 |
| `ModelSpecAccessService`（F9） | `service/modeling` | 模型对象读写与共享 |

六套各有各的表、各有各的判定顺序，互相不知道对方的结论。新增模块时没有可复用的 PDP，只能再写一套。

### S11　密级判定分散

人员密级与数据密级是两套独立标准，只经 `maxDataLevelForPersonnel` 单向映射（见既有记忆）。当前实现分散在 `security/policy/DataLevel`、`ClassificationUtils`、`SecurityLevelCatalog`、`AccessChecker.resolveAllowedDataLevels` 等处，`SecurityLevelCatalog` 仍存在双向混用。

---

## 三、普通用户（员工）现状

### S12　员工只有读权限，且只读路径被真正校验

- `EMPLOYEE` 在 `DEPARTMENT_PRIVILEGED_ROLES` 内，但不在 `DATA_MAINTAINER_ROLES` 内，因此所有维护类接口对员工关闭。
- F9 已定：员工不能建模。
- 员工实际能看到的数据由 `AccessChecker` 的"部门 + 密级 + 授权行"决定，但该检查只覆盖 catalog 相关路径；其他模块对员工只有粗粒度角色门，没有行级/对象级范围控制。

### S13　跨部门授权入口保留但未实现

按 F9 决定，跨部门授权与审核仅保留入口，没有落地实现。

---

## 四、问题归类

| 编号 | 问题 | 影响 |
|---|---|---|
| P1 | 角色双事实源、不同步、按用户名关联 | 授权与实际生效不一致；改名断链；已致 F9 线上 403 |
| P2 | 无统一身份解析，各处自行合并角色 | 同类缺陷反复出现 |
| P3 | 模块级角色门是同一数组的别名 | 名义分权、实际不分权，最小权限无从谈起 |
| P4 | 存在恒假的不可授予角色条件 | 判权表达式失真，审计无法据此说明放行依据 |
| P5 | 只有"角色→菜单"绑定，没有权限点模型 | 无法表达"可看不可改""可导出不可删"等能力 |
| P6 | 对象级授权 6 套并行 | 无统一 PDP，无法统一审计与回归 |
| P7 | 菜单接口 permitAll 且角色由调用方传参 | 菜单结构可被任意枚举；角色绑定不具备安全含义 |
| P8 | `person_profile` 冻结仍被读 | 部门/姓名可能是旧值 |
| P9 | 历史角色别名仍在放行列表 | 放行面大于当前角色模型 |

---

## 五、重构方案（待确认）

### R1　单一身份解析（对应 P1、P2、P8）

dts-admin 提供唯一的身份解析接口，返回账号状态、角色全集、部门、人员密级；dts-platform 只消费它，不再各自合并。

- 账号是否存在与是否启用：以 Keycloak 为准（实时）。
- 角色全集：Keycloak 角色 ∪ DTS 数据角色，由该接口一次算清。
- 部门与密级：按 R2 确定的唯一事实源读取。
- 调用方按会话缓存，角色或账号状态变更时主动失效。
- 现有 `resolve`、登录合并、`ModelingIdentityService` 全部改为调用同一份实现。

### R2　角色事实源归一（对应 P1、P2）

两种选择，需要用户拍板：

- **方案 A：角色全部下沉 Keycloak。** DTS 授权页面改为写 Keycloak realm 角色，本地表退化为缓存。优点是单一事实源、令牌自带角色；代价是数据角色与部门/密级的耦合语义要在 Keycloak 里表达，且需要一次存量迁移。
- **方案 B（建议）：DTS 库为角色事实源，Keycloak 只负责认证与三员。** 三员角色必须留在 Keycloak（否则管理员自举依赖 DTS 自身，故障时无法恢复）；数据角色以 DTS 库为准，关联键从 username 改为 `kc_id`。改动面小，与现场现有授权页面一致。

无论哪种方案，都要求：角色成员按 `kc_id` 关联、`EMPLOYEE` 命名统一到带 `ROLE_` 前缀、存量数据一次性迁移并校验。

### R3　引入权限点模型（对应 P5、P7）

建立三层：**权限点（permission）→ 角色（role）→ 用户（user）**。

- 新增权限点字典与角色-权限映射表；权限点按 `模块:对象:动作` 命名，例如 `catalog:asset:write`、`modeling:model:publish`、`governance:rule:configure`。
- `portal_menu_visibility.permission_code` 从空壳变为真实引用，菜单绑定改为绑定权限点而不是角色。
- 后端判权从 `hasAnyAuthority(角色数组)` 改为权限点校验，按模块逐步替换 508 处 `@PreAuthorize`。
- `/api/menu` 取消 `permitAll`，角色与权限一律取自安全上下文，不再接受调用方传参。

### R4　统一对象级决策点（对应 P6）

定义单一决策接口 `AccessDecision(subject, action, resource)`，现有六套机制改为其下的 provider，逐个迁移并保留各自的既有语义；迁移完成前新代码一律走新接口，不再新增第七套。

### R5　数据范围按属性求值（对应 P3、P6）

部门归属与密级不再混在角色判断里，作为独立的 ABAC 条件参与决策：角色决定"能做什么动作"，部门与密级决定"能作用到哪些数据"。密级映射收口到单一入口，消除双向混用。

### R6　清理（对应 P4、P9）

- 删除 `ROLE_GOV_ADMIN`、`ROLE_DATA_STEWARD`、`ROLE_INFRA_ADMIN` 三个不可授予角色的判权条件，替换为真实角色或权限点。
- 删除三员历史别名 `ROLE_AUDITOR_ADMIN`、`ROLE_AUDIT_ADMIN`、`ROLE_AUDITADMIN`，迁移前先确认现场令牌不再携带。
- `person_profile` 的两个读取点改到新的事实源，该表停用或归档。

---

## 六、待用户确认

| 编号 | 问题 | 备选 |
|---|---|---|
| Q16 | 角色事实源选哪个方案 | A：下沉 Keycloak / B：DTS 库为准（建议 B） |
| Q17 | 权限点粒度 | 按模块+动作（约 40–60 个）/ 按页面按钮（数百个，与审计按钮码对齐） |
| Q18 | 是否本 Sprint 内落地 R3、R4 | 全量重构 / 先做 R1+R2+R6 收口，R3+R4 单独排期 |
| Q19 | 兼容期策略 | 双轨运行一段时间 / 一次性切换并迁移存量 |
| Q20 | 三员职责边界是否同步调整 | 保持现状（授权由 AUTH_ADMIN 走变更单）/ 一并梳理 |

---

## 七、本次梳理未覆盖

- dts-admin-webapp、dts-platform-webapp 前端按钮级显隐与后端判权的一致性，未逐页核对。
- 审计按钮码（`ButtonCodes`）与权限点的对应关系，待 Q17 定粒度后再对齐。
- 跨部门授权审核（F9 保留入口）的具体设计。
- Ranger（`dts_ranger` 库）在当前链路中的实际作用，未确认是否仍在使用。
