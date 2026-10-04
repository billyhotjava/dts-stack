# F11 权限现状梳理与重构方案（2026-09-19）

**状态**：IN_PROGRESS（源码复核已修订，现场证据与部分政策待补）
**范围**：dts-admin、dts-platform 的三员权限与普通用户权限
**原报告基线**：`9bc3ec27a`；**本轮源码复核基线**：`af84d7190cebea3a1621a215f13c472d0542cf8c`。
**证据口径**：本轮只读核对源码，未重查运行库或 realm。下列账号数量、realm 清单与现场故障为原报告的历史记录，不能视为当前实测。原报告的 S4/S6/S7/S9/S10/S12 等结论已按源码修正；T01 不再标记全部冻结完成。
**当前设计**：[F11 权限重构架构与实施契约](F11-permission-architecture.md) 为设计主文档，本报告保留现状及 R/Q 编号追溯。

---

## 一、角色与身份现状

### S1　角色分域存放，解析口径未统一

| 角色 | 存放位置 | 是否在 Keycloak |
|---|---|---|
| ROLE_SYS_ADMIN、ROLE_AUTH_ADMIN、ROLE_SECURITY_AUDITOR、ROLE_OP_ADMIN | Keycloak realm 角色 | 是 |
| ROLE_INST_DATA_OWNER、ROLE_DEPT_DATA_OWNER、ROLE_INST_LEADER、ROLE_DEPT_LEADER | `dts_admin.admin_role_member` / `admin_role_assignment` | **否** |
| EMPLOYEE | 两处都有，且命名不一致（realm 为 `EMPLOYEE`，库内为 `ROLE_EMPLOYEE`） | 部分 |

实测 S10 realm 的全部非客户端角色：`default-roles-s10`、`EMPLOYEE`、`offline_access`、`ROLE_AUTH_ADMIN`、`ROLE_OP_ADMIN`、`ROLE_SECURITY_AUDITOR`、`ROLE_SYS_ADMIN`、`uma_authorization`。
实测 `admin_role_member`：`ROLE_INST_DATA_OWNER` 3 人、`ROLE_EMPLOYEE` 1 人。

数据角色成员入口 `AdminApiResource.applyRoleMemberMutations` 只写本地表，不回写 Keycloak，走变更单审批后生效。可授予的内置数据角色见 `BUILTIN_DATA_ROLES`：DEPT_DATA_OWNER、INST_DATA_OWNER、INST_LEADER、DEPT_LEADER、EMPLOYEE。这不代表 admin 的其他账号/角色入口不写 Keycloak；分域存储本身可保留，问题是每类角色的唯一写入方及解析规则没有收口。

### S2　角色成员以用户名字符串为键

`admin_role_member(role, username)` 唯一约束建在用户名上，不是 `kc_id`。Keycloak 侧改名或删号重建后，授权行成为孤儿，且可能被新的同名账号继承。`admin_role_assignment` 同样按 username。

### S3　`admin_keycloak_user` 是快照，且未回填

该表镜像 Keycloak 的 `realm_roles`、`group_paths`、`person_security_level`，`20260916_02` 又加了 `dept_code`/`dept_name`。实测 `xiezm` 行：`realm_roles=[]`、`dept_code` 为空、`last_sync_at=2026-09-14`。`e1d2e5917` 已把部门读取改到这份快照，但回填依赖一次 MDM 全量同步或快照刷新，**未回填前页面部门显示为空**。

### S4　`person_profile` 是冻结的事实源

原报告记录自 `4d53821ba` 起停止业务写入。当前源码仍有 `ModelingDirectoryResource.currentProfiles/identity`，以及 `AdminUserService.refreshSnapshotsFromProfiles`、姓名解析、组路径派生等读取/回填，不止两处。旧数据回填还可能覆盖姓名、人员密级和 MDM 状态。

**修正**：当前 `OrganizationService.assertDepartmentHasNoMembers` 已读取 `admin_keycloak_user.dept_code` 与 Keycloak 组成员，不再直接依赖旧人员表；仍须验证目录覆盖完整性与失败关闭。旧表退出前必须覆盖全部消费者，不能只改原报告列出的两个点。

### S5　角色合并口径分散，已出过线上故障

- 登录链路（`KeycloakApiResource`）合并三处：Keycloak realm 角色 + `admin_role_assignment` + `admin_role_member`。
- F9 建模目录（`ModelingDirectoryResource.resolve`）原先只读 Keycloak realm 角色，导致所有数据管理员在 `/api/modeling/**` 全部 403（`MODELING_IDENTITY_DENIED`）。2026-09-16 实测复现，已由 `9bc3ec27a` 补合并修复。

没有统一的身份解析入口，每处调用各写一遍合并逻辑，同类缺陷还会复发。

---

## 二、判权机制现状

### S6　dts-platform 多个模块共用粗粒度角色门

`security/AuthoritiesConstants` 里：

```java
public static final String[] CATALOG_MAINTAINERS   = DATA_MAINTAINER_ROLES;
public static final String[] GOVERNANCE_MAINTAINERS= DATA_MAINTAINER_ROLES;
public static final String[] IAM_MAINTAINERS       = DATA_MAINTAINER_ROLES;
public static final String[] INFRA_MAINTAINERS     = DATA_MAINTAINER_ROLES;
public static final String[] SERVICE_MAINTAINERS   = DATA_MAINTAINER_ROLES;
```

五个模块维护者常量是同一数组的别名，方法准入缺少模块动作区分。原报告统计平台 508 处 `@PreAuthorize`，其中 INFRA 96、GOVERNANCE 94、MODELING 79、CATALOG 62；本轮未重做全量计数，不能作为实施覆盖分母。服务层还可能有对象、范围、密级、状态等门禁，不能由常量别名推断“任一角色可写全部模块”。当前已有硬编码 RBAC，缺少统一可配置权限点和完整入口矩阵。

### S7　平台存在授予来源不明的历史角色条件

`ROLE_GOV_ADMIN`、`ROLE_DATA_STEWARD`、`ROLE_INFRA_ADMIN` 出现在 catalog 表达式中，原报告未在当时 realm 与内置数据角色表发现。该证据不能排除其他环境、自定义角色、历史令牌或外部映射，所以本轮改列“授予来源待核对”，不再断言全产品恒假。清理前输出当前持有者、来源和实际调用清单，不能机械换成更宽角色。

### S8　dts-admin 保留历史角色别名

`SecurityConfiguration` 对 `/api/admin/**`、`/admin/**`、`/api/**` 放行时，除三员角色外还接受 `ROLE_AUDITOR_ADMIN`、`ROLE_AUDIT_ADMIN`、`ROLE_AUDITADMIN` 三个历史别名。别名来源是"遗留 realm/令牌"，当前 realm 已不存在，属于可收敛的放行面。

### S9　菜单不是权限边界

`/api/menu` 在 `SecurityConfiguration` 中 `permitAll`，且角色、权限码、密级三项都从查询参数取：

```java
Set<String> roleCodes = normalizeRoles(roleParams);
if (CollectionUtils.isEmpty(roleCodes)) roleCodes = rolesFromSecurityContext();
```

只有调用方不传 `roles` 时才回退安全上下文，未认证调用存在菜单枚举面。不能据此单独证明业务接口越权，也不能用接口存在角色门证明全链路安全。platform 的同名接口已经要求认证，并通过 `PortalMenuClient.fetchMenuTreeForAudience` 将当前角色传到 admin；其网关默认服务认证、不转发用户认证。取消 admin 参数接口必须同时升级服务调用协议。

**修正**：`portal_menu_visibility.permission_code` 有写入方：`AdminApiResource.buildVisibilityEntities/newVisibility/applyPortalMenuChange`；`permissionCatalog` 已提供硬编码目录。当前缺口是权限字典、角色映射、身份输出与接口授权没有统一契约，不是“全仓无写入方/定义来源”。旧 schema 的 role_code 非空、写入要求角色，与新权限单独绑定模式也需一并迁移。

### S10　对象级授权有多类策略，缺少统一适用与组合契约

| 机制 | owner | 覆盖范围 |
|---|---|---|
| `AccessChecker`（部门 + 密级 + `CatalogDatasetGrant`） | `service/security` | 数据集读取/动作，43 个文件引用 |
| `AssetGrant` + `AssetPermissionService` | `service/permission` | 大屏/资产共享（F9 之前的共享模型） |
| `IamAssetActionPolicy` + `AssetActionPolicyEvaluator` | `service/security` | 资产动作策略 |
| `IamDatasetPolicy` + `PolicyService` | `service/iam` | 数据集策略、物理预览 |
| `CatalogDomainAccessPolicy` | catalog | 域级访问 |
| `ModelSpecAccessService`（F9） | `service/modeling` | 模型对象读写与共享 |

**修正**：这些机制分别处理资源/动作/范围，并非六套完全独立实现。`AccessChecker.canPerform` 已调用 `AssetActionPolicyEvaluator`；`canRead` 与部门检查是不同方法，不能把它视为天然覆盖所有条件的单一门禁。应先冻结实际组合图，再收敛决策入口、错误语义与审计，不要求所有表/领域策略合并。

### S11　密级判定分散

人员密级与数据密级必须分开；现有实现涉及 `security/policy/DataLevel`、`ClassificationUtils`、`SecurityLevelCatalog`、`AccessChecker`。本轮不宣布其映射政策正确或错误已被穷尽。新密级政策、维护入口和例外角色尚待 Q22 冻结；先定义单一政策端口及缺失拒绝规则，不按旧记忆补写客户政策。

---

## 三、普通用户（员工）现状

### S12　员工只有读权限，且只读路径被真正校验

- `EMPLOYEE` 在 `DEPARTMENT_PRIVILEGED_ROLES` 内，不在 `DATA_MAINTAINER_ROLES` 内；该数组控制的维护入口不向员工放行，其他入口需逐项核对。
- F9 已定：员工不能建模。
- 员工实际可见数据需沿调用链检查动作、部门、密级和显式授权；AccessChecker 也被治理/查询等路径引用。不能用“仅 catalog 有校验、其他模块都无范围”代替入口证据清单。

### S13　跨部门授权入口保留但未实现

按 F9 决定，跨部门授权与审核仅保留入口，没有落地实现。

---

## 四、问题归类

| 编号 | 问题 | 影响 |
|---|---|---|
| P1 | 角色分域解析不统一、按用户名关联 | 授权与实际生效不一致；改名断链；原报告记录 F9 现场 403 |
| P2 | 无统一身份解析，各处自行合并角色 | 同类缺陷反复出现 |
| P3 | 模块级角色门是同一数组的别名 | 名义分权、实际不分权，最小权限无从谈起 |
| P4 | 历史角色的授予来源和使用范围不清 | 判权表达式难以解释；需现场核验再清理 |
| P5 | 硬编码权限目录、角色映射、菜单与接口未形成统一模型 | 无法一致配置和解释细粒度动作 |
| P6 | 多类对象策略缺少统一适用/组合契约 | 重复判定、遗漏条件及审计/回归不一致风险 |
| P7 | 菜单接口 permitAll 且角色由调用方传参 | 菜单结构可被任意枚举；角色绑定不具备安全含义 |
| P8 | `person_profile` 冻结仍被读 | 部门/姓名可能是旧值 |
| P9 | 历史角色别名仍在放行列表 | 放行面大于当前角色模型 |

---

## 五、重构方案（由架构契约细化）

### R1　单一身份解析（对应 P1、P2、P8）

dts-admin 提供唯一的身份解析接口，返回账号状态、角色全集、部门、人员密级；dts-platform 只消费它，不再各自合并。

- 账号是否存在与是否启用：以 Keycloak 为准（实时）。
- 角色全集：按角色类别从唯一权威源读取，保留范围/动作绑定，不无条件合并所有同名角色。
- 部门与密级：按架构 §3 字段表读取；MDM 生命周期及新密级政策分别受 Q21/Q22 约束。
- 仅授权边界内复用；不跨请求缓存允许，撤权后的下次判定使用新值，延续 F9。
- 登录/profile、目录、`ModelingIdentityService` 及 `PortalOpaqueTokenIntrospector`/认证扩展共同接入；旧会话/后台也纳入。

### R2　角色事实源归一（对应 P1、P2）

本轮人员/组织职责讨论采用方案 B 作为设计基线；方案 A 保留为比较依据，不再双线设计：

- **方案 A：角色全部下沉 Keycloak。** DTS 授权页面改为写 Keycloak realm 角色，本地表退化为缓存。优点是单一事实源、令牌自带角色；代价是数据角色与部门/密级的耦合语义要在 Keycloak 里表达，且需要一次存量迁移。
- **方案 B：分域权威源。** Keycloak 管认证与三员/保留运维身份，DTS 管数据角色和带范围授权，MDM 管人员/组织主数据。每类只认一个写入来源。保留三员在 Keycloak 不自动证明故障恢复可用，应急账号和服务故障路径由 Q23/T09 验证。

角色成员按稳定账号 ID 关联，username 仅展示。EMPLOYEE 在内部规范化为 ROLE_EMPLOYEE、数据角色权威归 DTS；旧 realm 成员需要迁移核验，不能因清理前缀先撤销有效授权。采用扩展、核验回填、对比、切换、收缩；当前同名账号不是历史主体证明。

人员/组织表保留方案：organization_node 是唯一本地组织目录；admin_keycloak_user 演进为唯一在用人员目录与当前账号映射，支持待开户；person_profile 核验合并后退出全部运行读取并只读归档；Keycloak 表继续由自身管理。字段归属、同步方向、幂等与失败恢复见架构 §3–4。

### R3　引入权限点模型（对应 P5、P7）

建立三层：**权限点（permission）→ 角色（role）→ 用户（user）**。

- 收口已有硬编码权限目录，建立角色-权限-范围映射。权限按 `模块:资源:业务动作` 定义，审计按钮码只作映射，不成为授权依据。
- 菜单规则升级为权限引用，迁移 role_code 必填约束和管理端写入，空权限不默认公开。
- 按“入口→动作→范围→资源/职责门禁”矩阵分批替换，不以历史 508 次注解计数宣称全覆盖。
- `/api/menu` 当前用户入口与 platform→admin 的服务委托入口一起改造。客户端不能指定角色/密级，内部服务按稳定主体解析，不把服务身份当成人员权限。

### R4　统一对象级决策点（对应 P6）

定义统一决策入口，按资源和动作选择现有领域策略，明确 ALLOW/DENY/NOT_APPLICABLE/INDETERMINATE、强制条件、失败关闭及审计规则。保留必要领域存储与嵌套关系；新旧判定只作对比，不能 OR 放行。详见架构 §7。

### R5　数据范围按属性求值（对应 P3、P6）

角色表达动作能力，授权绑定保留作用范围，部门/密级/对象 ACL/状态/职责分离按动作叠加。列表范围在分页及计数前生效，批量/后台/数据出口使用同一政策。密级入口收口受 Q22 约束，模型 EDITOR 与数据消费权继续分离。

### R6　清理（对应 P4、P9）

- 核验 `ROLE_GOV_ADMIN`、`ROLE_DATA_STEWARD`、`ROLE_INFRA_ADMIN` 的所有来源和消费者，完成对应权限映射后再删除旧条件，不能替换成宽泛管理员放行。
- 删除三员历史别名 `ROLE_AUDITOR_ADMIN`、`ROLE_AUDIT_ADMIN`、`ROLE_AUDITADMIN`，迁移前先确认现场令牌不再携带。
- `person_profile` 的全部目录/回填/姓名/组派生读取退出；组织删除保护同时证明新来源覆盖完整、查不准不删。清理在对应迁移切片完成后执行。

---

## 六、决策与未决项

| 编号 | 问题 | 当前处理 |
|---|---|---|
| Q16 | 权威来源 | 方案 B 分域、字段归属已写入设计；现场角色映射待核验 |
| Q17 | 权限点粒度 | 按业务资源+动作；默认角色矩阵与按钮映射由 T03/T04 冻结 |
| Q18 | 实施范围 | 身份目录先行，再首个完整权限切片；实际容量/排期待定，不授权编码 |
| Q19 | 兼容期策略 | 扩展、回填、对比、切换、收缩；迁移演练和旧版本矩阵由 T08 落地 |
| Q20 | 三员职责 | 保持现有已确认职责；保留权限、禁止自审和自授权必须显式实现 |
| Q21–Q24 | MDM 身份/状态/顺序、新密级政策、特殊账号、历史范围及外部策略 | 见架构 §11，逐项绑定 owner/证据/阻断切片 |

---

## 七、本次梳理未覆盖

- dts-admin-webapp、dts-platform-webapp 前端按钮级显隐与后端判权的一致性，未逐页核对。
- 审计按钮码（`ButtonCodes`）与权限点的实际对应关系，由 T03/T04 按业务动作清单核对。
- 跨部门授权审核（F9 保留入口）的具体设计。
- Ranger（`dts_ranger` 库）在当前链路中的实际作用，未确认是否仍在使用。

## 八、本轮源码复核账本

路径均相对仓库根；基线为本文头部提交。历史运行数据未复验。GitNexus 上轮刷新出现解析失败/超时并已停止，本轮复用当前源码证据，不将旧索引当作完整调用图。

| 编号 | 事实/修正 | 源码位置 |
|---|---|---|
| F11-C01 | 权限目录及菜单权限码写入实际存在 | source/dts-admin/src/main/java/com/yuzhi/dts/admin/web/rest/AdminApiResource.java：permissionCatalog、buildVisibilityEntities、newVisibility、applyPortalMenuChange |
| F11-C02 | platform 菜单走服务头及角色参数，默认不转发用户认证 | source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/menu/PortalMenuClient.java；service/admin/gateway/support/AdminGatewayRequestOptions.java、AdminGatewayHeaders.java |
| F11-C03 | 普通认证读会话角色，F9 另查当前目录 | source/dts-platform/src/main/java/com/yuzhi/dts/platform/security/session/PortalOpaqueTokenIntrospector.java；security/modeling/ModelingIdentityService.java |
| F11-C04 | 对象策略有嵌套，不能当六个平行布尔值 | source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/security/AccessChecker.java：canPerform；AssetActionPolicyEvaluator.java |
| F11-C05 | Keycloak 快照刷新会镜像部门，旧人员回填仍在 | source/dts-admin/src/main/java/com/yuzhi/dts/admin/service/user/AdminUserService.java：refreshSnapshotsFromKeycloak、refreshSnapshotsFromProfiles、resolveGroupPathsFromProfiles、姓名解析 |
| F11-C06 | 组织删除已查新快照/Keycloak；建模目录仍读旧 profile | source/dts-admin/src/main/java/com/yuzhi/dts/admin/service/OrganizationService.java：assertDepartmentHasNoMembers；web/rest/platform/ModelingDirectoryResource.java：currentProfiles |
| F11-C07 | 当前先 provision Keycloak 再写快照，账号状态与 mdm_enabled 已分开 | source/dts-admin/src/main/java/com/yuzhi/dts/admin/service/personnel/PersonnelImportService.java：applyPayload、upsertSnapshot；KeycloakUserProvisioningService.java：provision |
| F11-C08 | 人员表 kc_id 非空，组织已有 groupId 映射 | source/dts-admin/src/main/java/com/yuzhi/dts/admin/domain/AdminKeycloakUser.java；OrganizationNode.java |
| F11-C09 | 旧角色授权还携带组织/数据集/动作范围 | source/dts-admin/src/main/java/com/yuzhi/dts/admin/domain/AdminRoleAssignment.java；web/rest/AdminApiResource.java：applyRoleAssignmentChange |
| F11-C10 | F9 要求当前身份、分离编辑/消费及后台复核 | [F9 §6.1、K80/K89/K90](F9-permission-chain-contract.md) |
| F11-C11 | PolicyService 持久化 OBJECT/FIELD/ROW 策略；定义存在不证明所有出口已执行 | source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/iam/PolicyService.java：field policies / row policy；domain/iam/IamDatasetPolicy.java |

原六项 review 结论与修订落点见架构 §12。源码核验不替代 T01 的目标环境清单和政策冻结，也不证明人员快照已回填。
