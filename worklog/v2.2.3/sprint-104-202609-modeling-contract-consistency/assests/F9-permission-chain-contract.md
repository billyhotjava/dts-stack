# F9 权限链契约：部门公共层、建模授权与 ADS 共享

**日期**：2026-09-12
**状态**：DRAFT。产品决策 D1–D9 与开放问题 Q1–Q3 已由用户确认（2026-09-12）；C71–C88 为 2026-09-11/12 只读勘察；K73–K85 为拟定契约，由 F9-T01 冻结后下游任务才可 READY。
**适用任务**：[F9-T01–T09](../features/F9-部门公共层与ADS共享权限链收敛/README.md)。
**来源**：2026-09-09 至 09-12 用户关于“研究所—部门—员工”层级下数据连接、数据模型共享与重复建设的讨论。

## 1. 产品决策（已确认，实施期不再重议）

| # | 决策 | 理由 |
|---|---|---|
| D1 | 每个部门一个公共层（一个可写数仓计划）；ODS/DWD/DWS 在部门内只建一份 | 避免重复存储、重复调度与口径不一致 |
| D2 | 建模角色只来自内置角色：部门数据管理员、部门领导、研究所数据管理员、研究所领导；普通员工与自定义角色不能建模 | 领导具备能力，日常由数据管理员操作 |
| D3 | 内置超级运维（opadmin，`ROLE_OP_ADMIN`）只在系统崩溃等应急场景启用，保留现有能力，不作为日常建模角色设计 | 现场无日常运维管理员 |
| D4 | 密级只升不降：下游模型、资产密级不低于上游最高密级；不允许脱敏降级 | 机密级合规硬约束 |
| D5 | 共享只开放到 ADS 层；ODS/DWD/DWS 是部门通用公共层，不做个人共享 | 公共层归部门，应用层才有个人归属 |
| D6 | ADS 默认只能看；创建人共享编辑权后才能改；本部门领导与所级角色保留管理权；创建人调岗或离职不转移归属 | 与大屏 OWNER/MANAGER 语义一致 |
| D7 | ADS 编辑权只能授给具备建模角色的人或建模角色 | 防止借共享给普通员工建模权 |
| D8 | 大屏越级查看保留，是大屏创建者的共享设置；ADS 共享不带越级 | 越级若在 ADS 层会沿大屏、导出、下游扩散，与 D4 冲突 |
| D9 | 跨部门引用公共层、跨部门共享 ADS 必须经授权审核；审核模块暂不建设，界面保留入口；暂停对所级角色同样生效 | 审核建成前不开放跨部门通道 |

大屏“个人新建 + 实例共享”范式不推广到数据连接与 ODS/DWD/DWS 模型。

## 1.1 功能新旧与存量边界（2026-09-12 用户确认）

| 能力 | 现场状态 | 对 F9 的影响 |
|---|---|---|
| 数据建模：数仓计划、模型、ADS、模型共享 | 本版本新增，客户现场无存量 | 迁移只建结构，不需要跨部门归并方案与现场预检；T04/T06 的 changeSet 在空表或测试环境极小数据上执行 |
| 大屏及其共享、越级、密级派生 | 既有功能，自 2026-04 起在现场运行并有存量 | F9 对大屏零改动，只做密级链核对（K83）与验收走查；任何改动都需另立任务并评估存量大屏重算影响 |
| 资产目录、资产授权、访问审批、角色与菜单 | 既有功能，现场有存量 | K78 密级只升不降按存量数据实施，Q5 历史降级扫描仍需执行；K73 角色收紧需先核对现场在用账号 |

因此 F9 的“存量兼容”只对既有功能成立；建模侧不承诺也不需要跨版本数据迁移。

## 2. 对既有契约的替代映射

| 既有契约 | 原内容 | F9 替代 | 保留 |
|---|---|---|---|
| [F7 契约](F7-first-model-initialization.md) §1 | 建模权限由菜单控制，服务端只要求已登录，不再要求部门数据负责人等角色 | 服务端按 K73 角色集合与 K75/K81 资源范围强制判定；菜单只作导航边界并与 K73 对齐 | 租户隔离、actor 与登录身份一致、目录/来源可见性、密级、归档/只读、字段有效性、版本 CAS；后台无主体任务的 owner 回退 |
| F7 契约 §4/§5 | 同租户一个稳定默认上下文 `modeling-context:default:v1`，owner 取首个 actor | 每部门一个默认公共层 `modeling-context:dept:{deptCode}:v1`（K74）；租户默认记录按 T01 预检迁移到其所属部门 | 首次保存与上下文初始化同事务、事务锁、失败整体回滚、GET 不初始化 |
| F7 契约 §补齐 `creation-context` | 响应 `{planId}`，取租户默认 | 响应扩展为部门维度（K74） | 只读、不创建 plan/policy |
| F5 上游准入 | 跨计划引用要求上游已发布 | 追加跨部门暂停（K77），拒绝结构沿用 F5 结构化拒绝结果 | 发布、归档、版本 pin 规则 |

F7 已有源码、测试和证据保留，不删除、不改写历史记录；F9 交付后 IT-46“普通菜单用户可保存”的断言由 IT-57 替代。

## 3. 完整权限链

### 3.1 链路总览

```text
① 身份        Keycloak 登录 → JWT(sub/preferred_username, realm roles ROLE_*, dept_code, personnel_level)
                    │
② 角色授予    dts-admin 授权管理员在用户管理授予内置角色 + 部门归属（C72）
                    │
③ 菜单导航    portal_menu_visibility(role_code) → 菜单树 → 前端路由守卫（仅导航边界，C73）
                    │
④ 接口准入    @PreAuthorize 角色表达式：建模写 = K73 角色集合（C74）
                    │
⑤ 资源判定    计划部门范围 K75 → 模型归属/编辑授权 K81 → 引用准入 K77 → 发布职责（既有）
                    │
⑥ 密级传播    上游最高密级 + 字段密级 → 发布封存只升（C82）→ catalog_dataset.classification（K78 只升）
                    │
⑦ 数据可见    资产目录：人员密级 ≥ 资产密级 ∧ (显式授权 ∨ 所级角色 ∨ 同部门)（C84）
                    │
⑧ 消费出口    大屏 OWNER/MANAGER/VIEWER + VIEWER 越级（C86）；跨部门申请入口 K82
                    │
⑨ 审计        授予/撤销/拒绝/密级变更均写审计并在 dts-admin 审计字典登记（K84）
```

任何一层都不能替代下一层：菜单隐藏不是安全控制；接口角色通过不代表可改某个计划或某个 ADS；模型可改不代表人员能看到数据；大屏越级只在大屏出口生效。

### 3.2 各层判定表

**② 角色与能力（目标态）**

| 角色码 | 名称 | 建模写 | 公共层范围 | ADS 管理 | 资产数据可见 |
|---|---|---|---|---|---|
| `ROLE_DEPT_DATA_OWNER` | 部门数据管理员 | 是 | 本部门 | 自建；被授编辑权 | 本部门且密级不超 |
| `ROLE_DEPT_LEADER` | 部门领导 | 是 | 本部门 | 本部门全部 | 本部门且密级不超 |
| `ROLE_INST_DATA_OWNER` | 研究所数据管理员 | 是 | 全部部门 | 全部 | 全所且密级不超 |
| `ROLE_INST_LEADER` | 研究所领导 | 是 | 全部部门 | 全部 | 全所且密级不超 |
| `ROLE_EMPLOYEE` | 普通员工 | 否 | 无 | 否 | 本部门、显式授权且密级不超 |
| `ROLE_OP_ADMIN` / `ROLE_ADMIN` | 内置超级运维 | 保留现状 | 保留现状 | 保留现状 | 保留现状，仅应急 |

**⑤ 资源判定：模型写（K75 + K81）**

| 目标 | 所级角色 | 部门领导 | 部门数据管理员 | 普通员工 |
|---|---|---|---|---|
| 本部门 ODS/DWD/DWS | 允许 | 允许 | 允许 | 403 |
| 本部门 ADS，本人创建 | 允许 | 允许 | 允许 | 403 |
| 本部门 ADS，他人创建，有编辑授权 | 允许 | 允许 | 允许 | 403（不可被授权） |
| 本部门 ADS，他人创建，无编辑授权 | 允许 | 允许 | 只读 | 403 |
| 其他部门任意模型 | 允许 | 不可见 | 不可见 | 不可见 |

**⑤ 资源判定：引用（K77）**

| 被引用上游 | 同部门 | 其他部门 |
|---|---|---|
| 已发布 | 允许（沿用 F5 准入） | 拒绝：`MODEL_SPEC_CROSS_DEPARTMENT_REF_PENDING_APPROVAL`；界面显示“申请跨部门引用（待开放）” |
| 未发布 | 沿用 F5 同计划规则 | 同上拒绝 |
| 存量已 pin 的跨部门引用 | — | 读取与原 pin 重保存保留；改 pin 到新修订或新增引用拒绝 |

**⑥ 密级（K78）**

| 操作 | 结果 |
|---|---|
| 模型发布 | 输出密级 = max(全部上游已封存密级, 字段/标准绑定密级)；缺证据阻断（既有） |
| 目录调高资产密级 | 经密级服务提升人工下限并审计 |
| 目录调低资产密级 | 409 `CLASSIFICATION_DOWNGRADE_FORBIDDEN`，原值不变，写拒绝审计 |
| ADS 编辑共享 | 不携带越级字段；被授权人查看数据仍受⑦约束 |
| 大屏 VIEWER 越级 | 保留，仅作用于该大屏展示 |

## 4. 使用场景

以下场景是 IT-55–IT-63 的业务输入。部门甲、乙为同租户两个部门；人员密级除特别说明外均不低于样例资产密级。

| # | 角色与前置 | 操作 | 期望结果 | 链路层 | 验收 |
|---|---|---|---|---|---|
| S01 | 部门甲数据管理员 A，部门甲尚无公共层 | 打开模型工作台新建 DWD 并保存设计 | 自动建立部门甲公共层并保存；模型归属部门甲；不出现规划初始化提示 | ①②③④⑤ | IT-58 |
| S02 | 部门甲已有公共层；部门甲数据管理员 B | 修改 A 创建的 DWD 字段说明并保存 | 允许；审计记录 B 为操作人 | ⑤⑨ | IT-59 |
| S03 | A 在部门甲创建 ADS“项目月报” | B 打开该 ADS | 可查看定义、实现与交付状态；编辑控件只读，提示“编辑需创建人授权” | ⑤ | IT-61 |
| S04 | 接 S03 | A 在 ADS 上点“共享”，授予 B 可编辑 | B 刷新后可编辑并保存；授权列表显示 B、授予人 A、时间 | ⑤⑨ | IT-61 |
| S05 | 接 S04 | A 撤销 B 的编辑权；B 未刷新页面直接保存 | 保存被拒绝 403 `MODEL_EDIT_GRANT_REQUIRED`，B 输入保留；刷新后只读 | ⑤ | IT-61 |
| S06 | 部门甲普通员工 C | A 尝试把 ADS 编辑权授给 C | 拒绝 422 `MODEL_ACCESS_GRANTEE_NOT_AUTHOR`；授权列表不变 | ⑤ | IT-61 |
| S07 | 部门甲领导 L | L 修改 A 创建且未共享的 ADS | 允许；审计标注管理权操作 | ⑤⑨ | IT-61 |
| S08 | 部门乙数据管理员 D | D 在部门乙 DWS 上游选择器中查找部门甲已发布 DWD | 部门甲模型不可选，显示“申请跨部门引用（待开放）”；直接调用接口返回 422 `MODEL_SPEC_CROSS_DEPARTMENT_REF_PENDING_APPROVAL` | ⑤⑧ | IT-60、IT-62 |
| S09 | 研究所数据管理员 I | I 切换到部门乙公共层建模 | 允许在部门乙建模和维护；跨部门引用同样暂停，与 S08 结果一致（Q1 已确认） | ④⑤ | IT-59、IT-60 |
| S10 | 普通员工 C | C 访问建模菜单；再直接调用 `POST /api/modeling/model-specs/draft-operations` | 菜单不可见；接口 403 `MODELING_ROLE_REQUIRED`；可按部门与密级查看本部门已发布资产和被授权大屏 | ③④⑦ | IT-57 |
| S11 | 目录维护者，资产密级为机密 | 在资产编辑中把密级改为内部 | 409 `CLASSIFICATION_DOWNGRADE_FORBIDDEN`，页面保留原密级并提示只能调高；调为绝密允许并审计 | ⑥⑨ | IT-56 |
| S12 | A 创建 ADS 引用部门甲机密 DWS | 发布 ADS；人员密级为内部的员工 E 查看资产；大屏创建者 P 用该 ADS 做大屏并对 E 设越级查看 | ADS 密级为机密；E 在目录看不到该资产；大屏对 E 越级展示生效，E 仍无法在目录或导出中访问 ADS 数据 | ⑥⑦⑧ | IT-62 |
| S13 | 部门甲数据管理员 A 调岗到部门乙（dept_code 变更后重新登录） | A 打开部门甲公共层与自己在部门甲创建的 ADS | 部门甲公共层不可见不可改；ADS 归属保留、A 失去编辑权；部门甲领导仍可管理；归属不转移（Q2 已确认） | ①⑤ | IT-59、IT-61 |
| S14 | 系统崩溃，启用 opadmin | opadmin 修复任意部门模型 | 保留全部能力；每次写入审计标注应急账号 | ②⑨ | IT-57 |
| S15 | 部门甲已有 DWD“项目任务快照明细”（同业务过程、同来源表、同粒度键） | B 新建 DWD 并保存设计 | 提示“部门甲已有相似模型”并列出可打开的模型；B 可忽略继续保存 | ⑤ | IT-63 |
| S16 | 建模为本版本新增，现场无存量；测试环境有一个租户默认上下文与少量模型 | 执行 F9 迁移 | 部分唯一索引直接建立；测试环境默认上下文归属其 owner 部门；预检仍执行，遇到无法判定部门的记录中止并输出清单，但不设计跨部门归并方案 | 数据 | IT-58 |

## 5. Context Ledger C71–C88

路径相对仓库根；Java 省略 `source/dts-platform/src/main/java/com/yuzhi/dts/platform/` 前缀，除非另注模块。勘察基线为开发目录 2026-09-12 HEAD `08a46ed5b`。

| 编号 | 已确认事实 | 证据 |
|---|---|---|
| C71 | 部门取自 JWT `dept_code` 声明，无请求头覆盖 | `security/SecurityUtils.java:142-157` |
| C72 | 内置角色 5 个：部门数据管理员、研究所数据管理员、研究所领导、部门领导、普通员工；另有自定义角色仓库 | `source/dts-admin/.../web/rest/platform/PlatformDirectoryResource.java:47-83`；`AdminCustomRoleRepository` |
| C73 | 菜单按 `portal_menu_visibility.role_code` 授权；种子为六个数据角色（含 DEPT/INST_DATA_VIEWER/DEV，与 C72 不一致）；测试环境建模菜单仅绑定 `ROLE_INST_DATA_OWNER`（19 行）；前端 `useDataModelingMenuGrant` 仅作导航边界 | `source/dts-admin/src/main/resources/config/liquibase/changelog/20251012-01_portal_menu_bind_all_roles.xml`；`source/dts-platform-webapp/src/pages/data-modeling/prototype/useDataModelingMenuGrant.ts` |
| C74 | 建模写接口角色表达式不一致：`isAuthenticated()` 用于 ModelSpecResource:50、ModelDraftOperationResource:31、ModelBuildIntentResource:37、ModelLifecycleResource:49、WarehousePlanResource:68、DimensionDefinitionResource:51；`CATALOG_MAINTAINERS` 用于 ModelingResource:57、ModelSpecImportResource:47、ModelingAuxResource:75、ModelingWordRootResource:31、ModelSpecMetricReferenceResource:30；`MODEL_MAINTAINERS` 用于 ModelPublicationIntentResource:31；`MODEL_RELEASE_DUTIES` 用于 ModelReleaseCandidateResource:48、ModelMaterializationPlanResource:28 | `web/rest/` 各文件 |
| C75 | 计划维护判定已被 F7 改为“已认证且 actor 与主体一致即允许”，无角色、无部门判定；无主体后台任务保留 owner 回退 | `service/modeling/ModelSpecPlanWriteAccessAdapter.java`（`f3dd879e5`、`13261975a`） |
| C76 | `ModelSpecPlanWriteAccessPort.canMaintain` 调用约 23 处，分布于草稿、创作、构建、生命周期、发布、候选、执行绑定、运行健康、serving 同步、质量补跑等服务 | `service/modeling/` 下 15 个文件 |
| C77 | 默认上下文为租户级：幂等键 `modeling-context:default:v1`，租户粒度 advisory 锁，owner 与部门取首个 actor | `service/modeling/ModelingContextInitializationService.java:22,57-61` |
| C78 | 计划表无“租户 + 所属部门”唯一约束，现有唯一约束为 tenant_code、tenant_id、幂等键 | `changelog/20260718_01_warehouse_plan_canonical.xml`、`20260719_01_warehouse_plan_create_idempotency.xml` |
| C79 | 计划读取守卫只比较“当前用户与由当前用户构造的 actor”，对已认证用户恒为真，计划列表不按部门过滤 | `service/modeling/warehouse/WarehousePlanAuthorizationGuard.java:25-30` |
| C80 | 跨计划引用只要求上游 PUBLISHED；模型读取按业务域可读判定，不看部门 | `service/modeling/ModelSpecApplicationService.java` `MODEL_SPEC_CROSS_PLAN_REF_INVALID` 分支与 `canRead` |
| C81 | 模型主表无归属人列；创建人记录在 `modeling_model_spec_revision.created_by`，测试环境 r1 全部有值 | `changelog/20260719_03_model_spec_v2_expand.xml`；只读查询 |
| C82 | 模型发布密级取上游与字段最高值、缺证据阻断、封存只升；密级服务显式拒绝降级 | `service/modeling/ModelClassificationPublishGate.java`；`service/catalog/CatalogClassificationService.java:173` |
| C83 | 资产目录全量保存直接写入请求密级，不经过只升校验；访问判定读取该列 | `web/rest/catalog/CatalogDatasetResource.java` `updateDataset`；`service/security/AccessChecker.java` `levelAllowed` |
| C84 | 资产授权仅支持个人（grantee_id/username/dept），`grant_type` 为 SHARE/DATA_ACCESS，含 can_query/can_preview/valid_to；部门门槛顺序为显式授权 → 所级角色 → 部门角色同部门 | `domain/catalog/CatalogDatasetGrant.java`；`AccessChecker.java:162` 起 |
| C85 | 资产访问申请与多级审批已存在，审批任务可按部门绑定部门领导 | `web/rest/CatalogDatasetAccessApprovalResource.java`（`/api/catalog/access`）；`service/security/DatasetDataAccessApprovalService.java:321-336` |
| C86 | 大屏 ACL：`analytics_screen_access` 授予 USER/ROLE，权限 OWNER/MANAGER/VIEWER，越级仅对 VIEWER 生效；大屏密级门槛为人员密级 ≥ 大屏密级 | `source/dts-analytics/.../domain/AnalyticsScreenAccess.java`；`service/ScreenPermissionService.java` |
| C87 | 数据连接维护角色为 DATA_MAINTAINER_ROLES（含部门领导）；记录 ownerDept，写入不校验操作人部门 | `web/rest/infra/InfraDataSourceResource.java:46,203` |
| C88 | 测试环境：1 个计划（部门 1153，DRAFT）、DWD 3 个与 DWS 1 个、无 ADS、1 个模型含依赖；客户现场未核对 | 2026-09-09/11 只读查询，T01 复核 |
| C89 | 大屏密级由上游派生：收集指标/表/库上游（ASSET key 形如 `source:{平台源}/schema:{schema}/table:{table}`），调用 platform `/api/catalog/classifications/consumers/derive`，取上游与人工下限的最大值；创建大屏强制选密级并写入人工下限；上游密级缺失时存为 BLOCKED_UPSTREAM 草稿 | `source/dts-analytics/.../service/AnalyticsConsumerClassificationService.java:140-240`；`service/AnalyticsClassificationClient.java:48-63`；`web/rest/ScreenResource.java:812-824` |
| C90 | 目录授权创建的被授权人信息（userId/username/deptCode）全部来自请求体，服务端不解析、不校验其部门与角色 | `web/rest/catalog/CatalogSecurityResource.java:186-212` |
| C91 | 平台侧目录只能取到用户 `(id, username, displayName, deptCode, deptName)` 与全量角色字典，没有“某用户拥有哪些角色”的查询；dts-admin `/api/platform/directory/users`、`/users/resolve` 同样不返回角色 | `service/directory/AdminUserDirectoryClient.java:16-32`；`service/admin/gateway/directory/AdminDirectoryGateway.java:108-174,442`；`source/dts-admin/.../PlatformDirectoryResource.java:116-133,487` |
| C92 | 建模（计划、模型、ADS）为本版本新增，现场无存量；大屏自 2026-04 起为既有功能，资产目录与角色菜单同为既有功能 | 用户确认 2026-09-12 |

## 6. 拟定契约 K73–K85

| 编号 | 契约 | 输入/输出与错误 | 任务 |
|---|---|---|---|
| K73 | 建模角色集合 `MODEL_AUTHORS`（仅内置角色，自定义角色不纳入，Q3 已确认） | `ROLE_INST_DATA_OWNER`、`ROLE_INST_LEADER`、`ROLE_DEPT_DATA_OWNER`、`ROLE_DEPT_LEADER`；应急保留 `ROLE_ADMIN`、`ROLE_OP_ADMIN`。C74 列出的全部建模写接口统一使用该集合；读接口保持已认证但按 K76 限定范围。拒绝 403 `MODELING_ROLE_REQUIRED`。菜单可见性与该集合对齐；自定义角色即便被绑定到建模菜单也不获得建模权 | T03 |
| K74 | 部门公共层上下文 | 幂等键 `modeling-context:dept:{deptCode}:v1`；锁粒度 `modeling-context:{tenant}:{deptCode}`。`GET /api/modeling/model-specs/creation-context?departmentCode=` → `{planId:UUID|null, departmentCode:string, writable:boolean}`。部门角色忽略参数、取 JWT `dept_code`，参数与之不符 403 `MODELING_DEPARTMENT_SCOPE_DENIED`；所级角色必须传参，缺失 400 `MODELING_DEPARTMENT_REQUIRED`；JWT 无部门 403 `MODELING_DEPARTMENT_UNRESOLVED`。首次保存 `draft-operations` 请求增加可选 `create.departmentCode`，规则同上 | T04 |
| K75 | 公共层维护判定 `canMaintain(tenant, planId, actor)` | 所级角色 → 允许；部门数据管理员/部门领导 → `plan.owner_department_id` 与 JWT `dept_code` 匹配才允许；其他 → 拒绝；无主体后台任务 → 原 owner 回退不变 | T05 |
| K76 | 计划与模型定义可见范围 | 计划列表、计划详情、模型列表、模型详情：所级角色全部；部门角色仅本部门；不可见统一 404（防枚举）。资产目录可见性不受此影响 | T04、T05 |
| K77 | 跨部门引用暂停 | `dependsOn`、`dimensionRefs` 指向其他部门计划的模型 → 422 `MODEL_SPEC_CROSS_DEPARTMENT_REF_PENDING_APPROVAL`，details `{field, referencedModelSpecId, referencedDepartmentCode}`；存量已 pin 引用原样重保存允许，改 pin 或新增拒绝；上游可用性投影对跨部门候选返回 `selectable:false, reasonCode` 同上 | T05 |
| K78 | 资产密级只升不降 | `PUT /api/catalog/datasets/{id}` 与所有写 `catalog_dataset.classification` 的用户入口：新值低于当前有效密级 → 409 `CLASSIFICATION_DOWNGRADE_FORBIDDEN`，不写入；高于 → 经 `CatalogClassificationService` 提升人工下限；相等 → 不变。审计 `CATALOG_CLASSIFICATION_RAISE` / `CATALOG_CLASSIFICATION_DOWNGRADE_REJECTED` | T02 |
| K79 | ADS 归属 | `modeling_model_spec.owner_id varchar(128)` 前向增列；APPLICATION 模型由 r1 `created_by` 回填，其余层为 null 且不参与判定；新建 ADS 写入当前 actor；视图 `ModelSpecView.ownerId` 只读输出 | T06 |
| K80 | ADS 编辑授权 | 表 `modeling_model_access(id uuid pk, tenant_id, model_spec_id, grantee_type USER\|ROLE, grantee_id, permission EDITOR, granted_by, granted_at, revoked_by, revoked_at)`，部分唯一 `(tenant_id, model_spec_id, grantee_type, grantee_id) where revoked_at is null`。`GET /api/modeling/model-specs/{id}/access-grants` → `[{id,granteeType,granteeId,granteeName,permission,grantedBy,grantedAt}]`；`POST` body `{granteeType, granteeId, permission:"EDITOR"}` → 201；`DELETE .../access-grants/{grantId}` → 204。错误：非 ADS 422 `MODEL_ACCESS_LAYER_NOT_SHAREABLE`；授予人非创建人/本部门领导/所级角色 403 `MODEL_ACCESS_MANAGE_DENIED`；被授权人无建模角色 422 `MODEL_ACCESS_GRANTEE_NOT_AUTHOR`（角色与部门由 K86 的目录解析给出，不采信请求体，见 C90）；被授权人不在模型所属部门 422 `MODEL_ACCESS_CROSS_DEPARTMENT_PENDING_APPROVAL`；ROLE 只允许 K73 部门级角色；重复授予 200 返回已有记录 | T06 |
| K81 | 模型级编辑判定 `ModelSpecWriteAccessPort.canEdit(tenant, modelSpecId, actor)` | 非 APPLICATION → K75；APPLICATION → 所级角色 ∨ (部门领导 ∧ 同部门) ∨ (具备 K73 角色 ∧ 同部门 ∧ (owner ∨ 有效 USER 授权 ∨ 有效 ROLE 授权命中))。模型定义、创作草稿、实现、构建、发布等以模型为对象的写操作改用该端口；计划级操作（执行绑定、运行健康等）保留 K75。拒绝 403 `MODEL_EDIT_GRANT_REQUIRED`。`authoring-context` 与 `delivery-status` 的 allowedActions/wizard 只读原因同步输出该码 | T06 |
| K82 | 跨部门申请入口 | 仅界面：上游选择器与 ADS 共享抽屉显示禁用按钮“申请跨部门引用/共享（待开放）”，提示审核模块未开放；不新增接口。后续挂接 C85 `/api/catalog/access` | T07 |
| K83 | 大屏消费 ADS 的密级链 | 已核实成立（C89）：大屏密级 = max(上游资产密级, 人工下限)，上游缺密级则大屏进 BLOCKED 草稿。F9 对大屏零改动，T07 只做逐层记录，T09 用真实账号走查 S12 | T07、T09 |
| K84 | 审计 | `MODEL_ACCESS_GRANT`、`MODEL_ACCESS_REVOKE`、`MODELING_ROLE_DENIED`、`MODEL_EDIT_GRANT_DENIED`、`MODEL_SPEC_CROSS_DEPARTMENT_REF_REJECTED`、`CATALOG_CLASSIFICATION_RAISE`、`CATALOG_CLASSIFICATION_DOWNGRADE_REJECTED`、`MODELING_CONTEXT_DEPARTMENT_INITIALIZED`；全部在 dts-admin 审计资源字典登记（domain-dts D2），IP 走 `IpAddressUtils.resolveClientIp` | T02、T03、T05、T06 |
| K86 | 被授权人角色与部门的权威解析 | 现有目录接口不返回用户角色（C91）。dts-admin `GET /api/platform/directory/users/resolve` 响应增加 `roles:string[]`（realm 角色码），平台侧 `AdminUserDirectoryClient.UserSummary` 同步增加该字段；仅用于单次授权请求的服务端校验，不落库、不长期缓存。目录不可用时授权接口返回 503 `MODEL_ACCESS_DIRECTORY_UNAVAILABLE` 并拒绝授权，不得放行或采信请求体 | T01 冻结、T06 落地 |
| K85 | 同部门重复建设提示 | `GET /api/modeling/model-specs/similar?planId&modelType&businessProcessId&sourceKeys&grainKeys` 只读 → `[{modelSpecId,name,layer,status,matchReasons:[BUSINESS_PROCESS\|SOURCE_SET\|GRAIN_KEYS]}]`，仅限同部门计划、仅 ODS/DWD/DWS；不阻断保存 | T08 |

## 7. 开放问题

Q1–Q3 已由用户于 2026-09-12 确认；Q4、Q6、Q7 同日按用户澄清与代码核实关闭；仅 Q5 仍由 F9-T01 执行。

| # | 问题 | 结论 / 默认处理 | 状态 |
|---|---|---|---|
| Q1 | 所级角色是否豁免 K77 跨部门引用暂停 | 不豁免；研究所数据管理员与所级领导同样暂停，跨部门一律走审核 | 已确认 2026-09-12 |
| Q2 | 创建人调岗或离职后 ADS 归属是否可转移，由谁转移 | 不转移；归属人保留原值，本部门领导与所级角色凭管理权维护，不提供转移接口 | 已确认 2026-09-12 |
| Q3 | 自定义角色如何映射到 K73（是否允许授权管理员把自定义角色声明为“建模角色”） | 自定义角色不算建模角色，不具备建模权；建模权只来自 K73 的内置角色 | 已确认 2026-09-12 |
| Q4 | 客户现场建模存量（每部门计划数、跨部门引用、ADS 创建人回填） | 已关闭：建模为本版本新增，现场无存量（C92），不存在迁移归并需求；T01 只采集测试环境计数，现场仍需核对在用角色账号以评估 K73 收紧影响 | 已确认 2026-09-12 |
| Q5 | 历史目录降密级：从 `CATALOG_ASSET_EDIT` 审计前后快照扫描 | 目录为既有功能且现场有存量，扫描仍需执行；仅出清单，是否恢复由用户确认 | 待 F9-T01 |
| Q6 | ADS 编辑授权如何权威校验被授权人角色与部门 | 已关闭：目录授权是请求体透传（C90），不可复用；平台目录客户端有部门无角色（C91）。结论为复用 `AdminUserDirectoryClient` 并按 K86 扩展角色字段，不新建用户目录副本 | 已确认 2026-09-12 |
| Q7 | 大屏密级与引用 ADS 资产密级关系（K83） | 已关闭：大屏密级取上游与人工下限的最大值，上游缺密级则大屏被阻断（C89），链路成立；F9 对大屏零改动，S12 仍须 T09 真实走查 | 已确认 2026-09-12 |

## 8. 非功能与交付约束

| 约束 | 检查方式 |
|---|---|
| 权限判定不增加列表 N+1：模型列表可编辑标记批量计算 | 20/200 模型列表的 SQL 次数记录，T09 |
| 读接口越权统一 404，不泄露其他部门模型存在性 | 跨部门 ID 直接访问用例，T09 |
| 权限矩阵仍为 read/write/export 硬编码（Sprint-36 M05） | 本 Feature 在服务层按角色判定，不假设按钮级动作集 |
| 迁移只做前向 changeSet，含预检与清洁库/升级库验证 | T04、T06、T09 |
| 审计动作全部登记字典，不落“未分类” | T09 审计查询 |
| 构建与编译型测试只在 `/data/dts-stack` 经 Git 同步后执行 | T09 |
