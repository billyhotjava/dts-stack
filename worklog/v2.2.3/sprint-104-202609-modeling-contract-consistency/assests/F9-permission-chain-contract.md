# F9 权限链契约：部门公共层、建模授权与 ADS 共享

**日期**：2026-09-12
**状态**：实施基线已冻结，编码和统一自动化验证已完成；正式交付/真实验收未完成。D1–D9 保留用户已确认的产品决策；K73–K92 的实际字段适配及入口清单见 [实施基线](F9-implementation-baseline-20260912.md)。Q5 客户现场历史扫描仍为 NOT_RUN，不阻塞新增降级封堵。
**适用任务**：[F9-T01–T09](../features/F9-部门公共层与ADS共享权限链收敛/README.md)。
**来源**：2026-09-09 至 09-12 用户关于“研究所—部门—员工”层级下数据连接、数据模型共享与重复建设的讨论。

## 1. 产品决策（已确认，实施期不再重议）

| # | 决策 | 理由 |
|---|---|---|
| D1 | 每个部门一个公共层（一个可写数仓计划）；ODS/DWD/DWS 在部门内只建一份 | 避免重复存储、重复调度与口径不一致 |
| D2 | 建模角色只来自内置角色：部门数据管理员、部门领导、研究所数据管理员、研究所领导；普通员工与自定义角色不能建模 | 领导具备能力，日常由数据管理员操作 |
| D3 | 内置超级运维（opadmin，`ROLE_OP_ADMIN`）只在系统崩溃等应急场景启用，保留现有能力，不作为日常建模角色设计 | 现场无日常运维管理员 |
| D4 | 密级只升不降：下游模型、资产密级不低于上游最高密级；不允许脱敏降级 | 机密级合规硬约束 |
| D5 | 共享只开放到 ADS 层；ODS/DWD/DWS 是部门通用公共层，不做个人共享 | 公共层归部门，应用层才用个人归属判定编辑权 |
| D6 | ADS 默认只能看；创建人共享编辑权后才能改；本部门领导与所级角色保留管理权；创建人调岗或离职不转移归属 | 与大屏 OWNER/MANAGER 语义一致 |
| D7 | ADS 编辑权只能授给具备建模角色的人或建模角色 | 防止借共享给普通员工建模权 |
| D8 | 大屏越级查看保留，是大屏创建者的共享设置；ADS 共享不带越级 | 越级若在 ADS 层会沿大屏、导出、下游扩散，与 D4 冲突 |
| D9 | 跨部门引用公共层、跨部门共享 ADS 必须经授权审核；审核模块暂不建设，界面保留入口；暂停对所级角色同样生效 | 审核建成前不开放跨部门通道 |

大屏“个人新建 + 实例共享”范式不推广到数据连接与 ODS/DWD/DWS 模型。D1 是公共层复用目标：T04 保证部门上下文唯一，T08 提示相似模型；本期不声称能够自动判定业务语义完全重复或强制零重复。

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
| [F7 契约](F7-first-model-initialization.md) §1 | 建模权限由菜单控制，服务端只要求已登录，不再要求部门数据负责人等角色 | 服务端按 K73 角色集合与 K75/K81 资源范围强制判定；菜单只作导航边界并与 K73 对齐 | 租户隔离、actor 与登录身份一致、目录/来源可见性、密级、归档/只读、字段有效性、版本 CAS；后台执行改按 K89 区分 USER/SYSTEM，不沿用无主体即信任 owner 的授权依据 |
| F7 契约 §4/§5 | 同租户一个稳定默认上下文 `modeling-context:default:v1`，owner 取首个 actor | 每部门一个默认公共层 `modeling-context:dept:{deptCode}:v1`（K74）；租户默认记录按 T01 预检迁移到其所属部门 | 首次保存与上下文初始化同事务、事务锁、失败整体回滚、GET 不初始化 |
| F7 契约 §补齐 `creation-context` | 响应 `{planId}`，取租户默认 | 响应扩展为部门维度（K74） | 只读、不创建 plan/policy |
| F5 上游准入 | 跨计划引用要求上游已发布 | 追加跨部门暂停（K77），拒绝结构沿用 F5 结构化拒绝结果 | 发布、归档、版本 pin 规则 |

F7 已有源码、测试和证据保留，不删除、不改写历史记录；F9 交付后 IT-46“普通菜单用户可保存”的断言由 IT-57 替代。

## 3. 完整权限链

### 3.1 职责与组合判定

```text
Keycloak / dts-admin：身份、启停、组织、角色与人员密级的权威来源
  → 门户会话：保存登录时确认的稳定 directoryUserId，兼容既有 username/sub
  → 建模边界：以稳定 ID 取当前权威属性（K86/K87），生成请求内授权上下文
  → dts-platform：角色能力 ∧ 租户/部门范围 ∧ 对象权限 ∧ 动作职责 ∧ 状态/版本
       ├─ 定义与操作：K75/K81/K88；上游引用另过 K77
       ├─ 数据读取：既有资产权限、人员密级、查询/导出控制，不由 EDITOR 授权替代
       └─ 后台执行：USER 重新授权 / SYSTEM 限定已部署范围（K89）
  → 密级：既有发布封存与传播 + 目录人工下限只升（K78）
  → 大屏：既有 ACL、密级与 VIEWER 越级；例外仅在既有展示出口生效
  → 审计：主体、目标、动作、判定原因、关联请求/任务，登记 dts-admin 字典
```

以上是相互独立的判定维度，不是前一层通过就能放行后一层的单线授权。菜单只负责导航；对象编辑权不授予数据预览、查询、导出或审核权。普通员工消费已发布资产仍执行现有资产规则。

### 3.2 各层判定表

**② 角色与能力（目标态）**

| 角色码 | 名称 | 建模写 | 公共层范围 | ADS 管理 | 资产数据可见 |
|---|---|---|---|---|---|
| `ROLE_DEPT_DATA_OWNER` | 部门数据管理员 | 是 | 本部门 | 自建；被授编辑权 | 本部门且密级不超 |
| `ROLE_DEPT_LEADER` | 部门领导 | 是 | 本部门 | 本部门全部 | 本部门且密级不超 |
| `ROLE_INST_DATA_OWNER` | 研究所数据管理员 | 是 | 全部部门 | 全部 | 全所且密级不超 |
| `ROLE_INST_LEADER` | 研究所领导 | 是 | 全部部门 | 全部 | 全所且密级不超 |
| `ROLE_EMPLOYEE` | 普通员工 | 否 | 无 | 否 | 密级允许且满足既有部门或显式授权规则 |
| `ROLE_OP_ADMIN` | 内置超级运维 | 应急维护 | 全部门 | 应急管理 | 保留既有例外，不作为日常账号 |
| `ROLE_ADMIN` | 兼容管理角色 | 定义维护 | 全部门 | 管理 | 保留既有行为；不等同于 opadmin 发布职责 |

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
| 已发布 | 允许（沿用 F5 准入） | 拒绝；可见上游为 422，不可见上游为通用 404；选择器仅保留通用待开放入口 |
| 未发布 | 沿用 F5 同计划规则 | 同上拒绝 |
| 存量已 pin 的跨部门引用 | — | 只对 T01 清单内同一模型、同一修订集合保留无变化重保存；不可见信息不返回；执行仍按 K77/K89 复核 |

**⑥ 密级（K78）**

| 操作 | 结果 |
|---|---|
| 模型发布 | 输出密级 = max(全部上游已封存密级, 字段/标准绑定密级)；缺证据阻断（既有） |
| 目录调高资产密级 | 经密级服务提升人工下限并审计 |
| 目录调低资产密级 | 409 `CLASSIFICATION_DOWNGRADE_FORBIDDEN`，原值不变，写拒绝审计 |
| ADS 编辑共享 | 不携带越级字段；被授权人查看数据仍受⑦约束 |
| 大屏 VIEWER 越级 | 保留，仅作用于该大屏展示 |

## 4. 使用场景

以下场景是 IT-55–IT-63 的业务输入，新增反向分支保留原 IT 编号。部门甲、乙为同租户两个部门；人员密级除特别说明外均不低于样例资产密级。

| # | 角色与前置 | 操作 | 期望结果 | 链路层 | 验收 |
|---|---|---|---|---|---|
| S01 | 部门甲数据管理员 A，部门甲尚无公共层 | 打开模型工作台新建 DWD 并保存设计 | 自动建立部门甲公共层并保存；模型归属部门甲；不出现规划初始化提示 | ①②③④⑤ | IT-58 |
| S02 | 部门甲已有公共层；部门甲数据管理员 B | 修改 A 创建的 DWD 字段说明并保存 | 允许；审计记录 B 为操作人 | ⑤⑨ | IT-59 |
| S03 | A 在部门甲创建 ADS“项目月报” | B 打开该 ADS | 可查看定义、实现与交付状态；编辑控件只读，提示“编辑需创建人授权” | ⑤ | IT-61 |
| S04 | 接 S03 | A 在 ADS 上点“共享”，授予 B 可编辑 | B 刷新后可编辑并保存；授权列表显示 B、授予人 A、时间 | ⑤⑨ | IT-61 |
| S05 | 接 S04 | A 撤销 B 的编辑权；B 未刷新页面直接保存 | 保存被拒绝 403 `MODEL_EDIT_GRANT_REQUIRED`，B 输入保留；刷新后只读 | ⑤ | IT-61 |
| S06 | 部门甲普通员工 C | A 尝试把 ADS 编辑权授给 C | 拒绝 422 `MODEL_ACCESS_GRANTEE_NOT_AUTHOR`；授权列表不变 | ⑤ | IT-61 |
| S07 | 部门甲领导 L | L 修改 A 创建且未共享的 ADS | 允许；审计标注管理权操作 | ⑤⑨ | IT-61 |
| S08 | 部门乙数据管理员 D | D 在部门乙 DWS 上游选择器中查找部门甲已发布 DWD | 列表不返回部门甲模型名称、ID、部门或数量；显示通用“申请跨部门引用（待开放）”；猜测不可见 ID 与不存在 ID 均返回通用 404 | ⑤⑧ | IT-60、IT-62 |
| S09 | 研究所数据管理员 I | I 切换到部门乙公共层建模 | 允许在部门乙建模和维护；跨部门引用同样暂停；I 可见的其他部门上游返回 422 拒绝，不能借所级身份放行（Q1 已确认） | ④⑤ | IT-59、IT-60 |
| S10 | 普通员工 C | C 访问建模菜单；再直接调用 `POST /api/modeling/model-specs/draft-operations` | 菜单不可见；接口 403 `MODELING_ROLE_REQUIRED`；可按部门与密级查看本部门已发布资产和被授权大屏 | ③④⑦ | IT-57 |
| S11 | 目录维护者，资产密级为机密 | 在资产编辑中把密级改为内部 | 409 `CLASSIFICATION_DOWNGRADE_FORBIDDEN`，页面保留原密级并提示只能调高；调为绝密允许并审计 | ⑥⑨ | IT-56 |
| S12 | A 创建 ADS 引用部门甲机密 DWS | 发布 ADS；人员密级为内部的员工 E 查看资产；大屏创建者 P 用该 ADS 做大屏并对 E 设越级查看 | ADS 密级为机密；E 在目录看不到该资产；大屏对 E 越级展示生效，E 仍无法在目录或导出中访问 ADS 数据 | ⑥⑦⑧ | IT-62 |
| S13 | 部门甲数据管理员 A 在权威目录调岗到部门乙，保留旧门户会话 | A 打开部门甲公共层与自己在部门甲创建的 ADS | K87 下次判定使用新部门，部门甲公共层不可见不可改；ADS 归属保留、A 失去编辑及授权管理权；部门甲领导仍可管理；归属不转移（Q2 已确认） | ①⑤ | IT-59、IT-61 |
| S14 | 系统恢复场景，既有认证/目录依赖可用后启用 opadmin | opadmin 修复任意部门模型 | 保留应急模型维护能力；每次写入审计标注应急账号 | ②⑨ | IT-57 |
| S15 | 部门甲已有 DWD“项目任务快照明细”（同业务过程、同来源表、同粒度键） | B 新建 DWD 并保存设计 | 提示“部门甲已有相似模型”并列出可打开的模型；B 可忽略继续保存 | ⑤ | IT-63 |
| S16 | 建模为本版本新增，现场无存量；测试环境有一个租户默认上下文与少量模型 | 执行 F9 迁移 | 部分唯一索引直接建立；测试环境默认上下文归属其 owner 部门；预检仍执行，遇到无法判定部门的记录中止并输出清单，但不设计跨部门归并方案 | 数据 | IT-58 |
| S17 | 部门编码 1153、153，以及真实父子部门 | 读写、共享、首次初始化 | 不做尾号或祖先匹配；不同权威部门严格隔离 | ⑤ | IT-58、IT-59、IT-61 |
| S18 | 用户名与目录 ID 不同；旧会话无稳定 ID；同名账号被重建 | 登录、授权、改名后编辑、重放旧会话 | 新授权按稳定 ID 命中；改名不丢权；旧会话要求重新登录；重建账号不继承旧人授权 | ①⑤ | IT-57、IT-61 |
| S19 | 已有会话；管理员撤角色、调岗、停用或降低人员密级；目录故障 | 不刷新页面直接请求与排队任务执行 | 下次授权读取当前属性；停用拒绝、角色不足 403、失去范围 404；目录故障 503；不返回数据，不复用旧角色放行 | ①④⑤⑦ | IT-57、IT-59、IT-61 |
| S20 | 所级 I 在部门乙建模，部门甲产出可在目录读取 | 分别经模型引用、目录表、连接表、dbt 节点和受控导入引用该产出 | 同一规范资产/血缘识别同一跨部门来源并拒绝；不可见目标不回显；普通外部来源沿用原准入 | ⑤ | IT-60 |
| S21 | 同部门 A/B 各有 ADS；B 仅能编辑自身 ADS | 提交混合批量构建、候选发布、质量补跑、执行绑定修复、手动运行 | 按实际变更/执行范围逐模型授权，任一不足整批拒绝且零新副作用；仅作为只读上游不要求 EDITOR | ⑤ | IT-61 |
| S22 | B 获授 ADS 编辑权后排队；执行前撤权；另有既有系统调度 | worker 执行、重试、回调、继续发布 | USER 重新授权后拒绝；SYSTEM 只运行认证绑定内已部署版本；不靠计划首建人回退；已经运行的外部任务按 K89 记录处置 | ①⑤⑨ | IT-59、IT-61 |
| S23 | 四种建模角色、ADMIN、opadmin、普通员工、自定义角色 | 保存、构建、审核、发布、后台职责查询 | 与 K90 动作矩阵一致；平台/admin 一致；自定义角色不获得建模权；审核人分离仍成立 | ②④⑤ | IT-57 |
| S24 | 部门乙 D 与所级 I | 搜索、选择器、计数、关系图、错误详情、旧链接 | D 不获知部门甲模型；I 可见但不能跨部门引用；两类页面均有通用待开放入口 | ⑤⑧ | IT-58、IT-60、IT-62 |
| S25 | 已有部门公共层，包含不同创建人的 ADS | 直接建第二计划、改所属部门、发布/归档公共层、重试初始化 | 正常入口不能迁移/关闭公共层；新模型继续复用同一计划；异常已归档上下文阻断且不另造计划 | 数据⑤ | IT-58 |
| S26 | 低密级建模人员获得 ADS EDITOR | 改定义、预览、查询、导出、构建读取来源；目录提升密级 | 编辑权不放宽数据与来源密级；数据路径仍拒绝；大屏例外不扩散 | ⑤⑥⑦ | IT-61、IT-62 |

## 5. Context Ledger C71–C100

路径相对仓库根；Java 省略 `source/dts-platform/src/main/java/com/yuzhi/dts/platform/` 前缀，除非另注模块。C71–C92 保留原勘察来源（`08a46ed5b` 及当时测试环境）；C93–C100 为本轮源码复核，基线 `6148a83af`。测试环境计数不宣称已刷新。

| 编号 | 已确认事实 | 证据 |
|---|---|---|
| C71 | 部门取自认证主体的 `dept_code`，无请求头覆盖；当前门户会话来源见 C93，不应等同于每请求读取 Keycloak JWT | `security/SecurityUtils.java:142-157` |
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
| C93 | 当前资源服务使用 opaque token + PortalSessionRegistry；角色/部门/人员密级取会话快照，访问续期不刷新这些属性 | `config/SecurityConfiguration.java:106`；`security/session/PortalOpaqueTokenIntrospector.java:28`；`PortalSessionRegistry.java:94,129` |
| C94 | 门户认证的 sub 写成 username，WarehousePlanActorProvider 取当前用户 ID；目录同时返回 id/username | `PortalOpaqueTokenIntrospector.java:39`；`service/modeling/warehouse/WarehousePlanActorProvider.java:10`；`AdminUserDirectoryClient.java:30` |
| C95 | DepartmentUtils.matches 接受后缀匹配；现有 AccessChecker.departmentAllowedExact 和计划 header 校验已经采用规范化后相等 | `security/DepartmentUtils.java:33`；`service/security/AccessChecker.java:225`；`WarehousePlanAuthorizationGuard.java:90` |
| C96 | 来源独立支持 CATALOG_TABLE/CONNECTION_TABLE/DBT_NODE，不能只检查 ModelSpec 引用数组；目录读取允许所级角色跨部门访问 | `SourceReferenceResolverAdapter.java:73`；`JpaCatalogSourceReferenceReadAdapter.java:212`；`AccessChecker.java:225` |
| C97 | 批量候选展开 scope；质量补跑、执行绑定修复、手动运行仍按计划授权，实际影响候选与绑定中的模型 | `ModelReleaseCandidateApplicationService.java:315`；`CandidateGovernanceQualityRerunService.java:63`；`PlanExecutionBindingCommandService.java:55`；`PlanOperationalRunService.java:114` |
| C98 | 平台和 admin 各有维护/审核/执行角色映射；部门领导缺失；ADMIN 不在发布职责数组中 | 两模块 `security/AuthoritiesConstants.java`；`ReleaseDutyResolver.java:20`；admin `ReleaseDutyInternalResource.java:37` |
| C99 | 无认证主体时计划维护退回 actor=plan.owner；该身份不能代表共享编辑人，也不能证明系统任务的可信来源 | `ModelSpecPlanWriteAccessAdapter.java:40`；`ModelPublicationReviewReconciler.java:114` |
| C100 | 计划 header 可改部门，计划可整体归档；默认上下文按固定幂等键查询并对只读状态拒绝 | `WarehousePlanApplicationService.java:258,290`；`ModelingContextInitializationService.java:75` |

## 6. 拟定契约 K73–K92

以下是本轮具体技术方案，不冒充已实现能力。T01 按字段适配与调用清单冻结；T02 只依赖 K78 和相关审计子集。

| 编号 | 契约 | 输入/输出与错误 | 任务 |
|---|---|---|---|
| K73 | 建模能力准入 | 四种内置建模角色，加兼容 ADMIN、应急 OP_ADMIN；实际授权取 K87 当前身份。普通员工/纯自定义角色 403 `MODELING_ROLE_REQUIRED`；发布动作还需 K90，不能把入口集合当全部动作权限 | T03 |
| K74 | 部门上下文 | `GET /api/modeling/model-specs/creation-context?departmentCode=` → `{planId:UUID\|null,departmentCode:string,writable:boolean}`；部门角色取当前权威部门，传其他部门 403；所级/兼容管理/应急角色须选真实有效部门，无选择 400 `MODELING_DEPARTMENT_REQUIRED`，未知/停用部门 400 `MODELING_DEPARTMENT_INVALID`，不要求本人挂在所选部门。首次保存 `create.departmentCode` 同规则；显式 planId 必须与所选部门一致 | T04 |
| K75 | 公共层维护 | `canMaintain(tenant,planId,actor)` 内使用 K87 主体；所级/ADMIN/OP_ADMIN 可维护全部门；部门数据管理员/领导仅本部门（K92）；无可信主体拒绝。它仅判定公共层范围，不授予其中每个 ADS 的操作权 | T05 |
| K76 | 模型元数据读取 | 四种建模角色及兼容管理角色按部门/所级范围读取；普通员工不读建模定义。不可见与不存在同为通用 404，不返回目标 ID/名称/部门/计数。模型详情、列表、版本、草稿、交付、候选、日志、关系图与辅助查询均纳入；已发布资产消费保留原规则 | T04、T05 |
| K77 | 跨部门引用与来源 | 统一解析规范资产身份和受治理血缘：模型依赖、维度引用、sourceRefs 的目录表/连接表/dbt 节点及导入后的实际读集均检查。可见的跨部门目标 422 `MODEL_SPEC_CROSS_DEPARTMENT_REF_PENDING_APPROVAL`；不可见目标通用 404。上游归属无法确定 422 `MODEL_SOURCE_SCOPE_UNRESOLVED`，解析服务故障 503，均拒绝新引用/执行 | T05 |
| K78 | 密级只升不降 | 全部用户写 `catalog_dataset.classification` 的入口：降低 409 `CLASSIFICATION_DOWNGRADE_FORBIDDEN` 且整次修改不提交；提高走 CatalogClassificationService 人工下限；相等/未传不改密级；非法或显式空值 400。保留 If-Match/CAS，拒绝审计不能随事务回滚丢失 | T02 |
| K79 | ADS 稳定归属 | `owner_id varchar(128)` 存 K87 稳定 subjectId；APPLICATION 的 r1.created_by 必须结合创建审计证明原主体，再经权威目录解析回填；当前同名用户不等于历史创建人。无法证明原主体、解析歧义或无法解析时输出预检清单并中止；历史修订 created_by 不改写。从未进入 ADS 的模型为 null；已退出 ADS 的模型保留历史 owner 但不用于公共层编辑判定。改名不转移，调岗/离职不转移 | T06 |
| K80 | ADS 编辑授权 | 新表仅表示模型 EDITOR；USER 的 granteeId、grantedBy、revokedBy 为稳定 subjectId；ROLE 用内置角色码。POST/DELETE 仅同部门且有建模能力的 owner、本部门领导、所级/兼容管理角色可管理；所有模型与 grantId 联合校验 tenant/model，防跨对象撤销；具体协议见 §6.5 | T06 |
| K81 | 模型编辑 | `canEdit(tenant,modelSpecId,actor)`：先 K87/K76 与生命周期；非 APPLICATION 用 K75；ADS 为所级/ADMIN/OP_ADMIN，或本部门领导，或同部门建模人员且 owner/有效 USER/ROLE 授权命中。无编辑权 403 `MODEL_EDIT_GRANT_REQUIRED`；不可见先 404。allowedActions/wizard/列表批量使用同一判定 | T06 |
| K82 | 通用待开放入口 | 上游选择器和 ADS 共享抽屉保留禁用入口，无 targetModelSpecId/targetDepartmentCode，不请求、不列他部门名称；说明“跨部门引用/共享暂未开放”。未来审批必须区别模型引用/编辑与资产数据访问，不直接把 C85 数据授权当 EDITOR | T07 |
| K83 | 大屏消费密级链 | 复用 C89 的派生与阻断；大屏零改动。核对任务可记录 GAP 完成调查，但 S12 未通过时 IT-62/F9 不得 PASS/DONE；后续缺陷需独立修复与同版本复验 | T07、T09 |
| K84 | 审计 | 保留 GRANT/REVOKE/ROLE_DENIED/EDIT_DENIED/CROSS_DEPARTMENT_REJECTED/CLASSIFICATION_RAISE/DOWNGRADE_REJECTED/CONTEXT_INITIALIZED；补身份失效、批量拒绝、后台授权拒绝、公共层生命周期拒绝动作并在 dts-admin 登记。记录稳定主体、动作、原因、请求/任务关联、受控目标；不记录 token/凭据/数据行；IP 复用 IpAddressUtils | T02–T06 |
| K85 | 相似模型提示 | GET `.../model-specs/similar?planId&modelType&businessProcessId&sourceKeys&grainKeys&excludeModelSpecId`；同部门同计划，ODS/DWD/DWS，最多 10 条。只有双方非空的业务过程/来源集合/粒度集合才算命中；失败不阻断保存；不声称业务模型唯一 | T08 |
| K86 | 当前权威目录 | 复用 dts-admin users/resolve + AdminDirectoryGateway + AdminUserDirectoryClient，返回 `{id,username,displayName,deptCode,deptName,roles:string[],enabled:boolean,personnelLevel:string\|null}`；必须可按稳定 ID 精确解析，禁 ID 找不到再按同名用户回退。只在建模授权边界使用；角色搜索/候选服务同步适配，普通目录响应按既有权限最小暴露 | T03 主体能力、T06 授权消费 |
| K87 | 身份键与新鲜度 | 会话稳定 directoryUserId → K86 当前属性 → 请求内授权上下文；不从请求体/header 接受 actor/部门/角色。旧会话缺稳定 ID 401 `MODELING_REAUTHENTICATION_REQUIRED`；用户已删除/停用 401 `MODELING_IDENTITY_INACTIVE`；目录故障 503 `MODELING_IDENTITY_UNAVAILABLE`；不以快照回退放行；详见 §6.1 | T03、T06 |
| K88 | 实际作用范围 | 服务端展开命令实际读集/写集/执行集，按 §6.3 判定；批量任一目标无权整批拒绝。绑定修复、手动运行、质量补跑不能只查计划；只读上游不要求编辑授权；签名/绑定/版本变化重新解析，不信任客户端 scope | T05 基础、T06 全面接入 |
| K89 | 后台授权 | USER 记录稳定发起人、动作、tenant、scope/版本；执行及重试前复核当前身份和对象权限。SYSTEM 仅接受可信服务认证与既有已部署绑定，不借 plan.owner 模拟人；定义修改、共享和新发布不可走 SYSTEM 豁免；详见 §6.3 | T05、T06 |
| K90 | 发布职责 | 平台入口、ReleaseDutyResolver、admin ReleaseDutyInternalResource、后台和 allowedActions 使用 §6.4 同一矩阵及契约测试；维护、审核、执行不互相替代；保留提交人与审核人分离及原状态门禁 | T03 |
| K91 | 公共层稳定生命周期 | 每租户每权威部门固定上下文键；部门归属不可变，正常入口不整体发布/归档默认公共层，不把持久工作空间当模型发布版本。所有创建入口复用初始化锁/键；异常只读上下文 409，不绕开固定键另造计划；详见 §6.2 | T04 |
| K92 | 部门身份 | 权威组织编码作为 owner_department_id 和幂等键值；仅服务端目录规范化一次后精确相等。不得调用 DepartmentUtils.matches，不做后缀、大小部门继承或未验证的字符串别名折叠；未知/空部门拒绝；父子部门默认不同范围 | T04、T05、T06 |

### 6.1 稳定主体、旧会话与撤权

- 门户登录成功后由服务端以已认证的 admin 身份确认目录 ID，扩展现有会话实体/记录一个 nullable `directoryUserId`。不改变已有 `sub=username` 的对外行为，不改大屏身份键，不把新 ID 直接塞入旧 owner 比较链。
- 既有会话不得按用户名静默补 ID；进入建模边界时要求重新登录。新登录 ID 一旦绑定不可因改名或账号重建更换；已删除 ID 不能回退匹配同名新用户。F9 新模型归属、授权、命令主体使用稳定 ID；历史修订文字保留。
- 每个建模受保护请求通过现有认证扩展点取一次 K86 当前属性，生成独立建模上下文，供方法准入、服务与数据访问判定共用；不能前端传参覆盖。读取也复核以保证调岗后旧链接隔离；不全局改写其他产品会话策略。
- 不用旧会话角色先作最终拒绝，避免新授角色必须重登录才能进入；建模准入消费当前上下文。目录请求总超时上限 2 秒、无自动重试，仅请求内复用，不跨请求缓存“允许”。列表 20/200 行不能逐模型请求目录。K86 当前人员密级缺失/非法时，不放宽需要读取数据的动作；密级判定失败明确拒绝，不能回落到较宽的旧会话值。
- 角色/部门/启停/人员密级变更提交后，下一次授权判定读取新值。ADS 授权撤销提交后，后续写请求重新查有效授权；与授权变更使用模型级锁/版本约束确定先后，不把“页面刷新”作为安全条件。
- “即时”指变更提交后启动的下一次判定；已经授权并进入执行的外部操作不能声称原子撤回，按 K89 在后续边界重新检查。跨服务快照不宣称分布式强事务。

### 6.2 部门上下文与生命周期

- 部门角色的作用部门来自 K87；所级/ADMIN/OP_ADMIN 通过合法部门选择确定目标，但其人员身份不伪装成该部门员工。来源校验同时传实际主体与目标计划部门：管理范围允许选择部门不等于可读任何来源。
- 幂等键 `modeling-context:dept:{canonicalDeptCode}:v1`；事务锁 `modeling-context:{tenant}:{canonicalDeptCode}`；首次初始化和模型保存同事务；GET 不初始化。手工 POST warehouse-plans、导入、显式 planId 保存必须进入同一不变量检查，不能自带任意幂等键绕开。
- 部分唯一 `(tenant_id,owner_department_id) where lifecycle_status <> 'ARCHIVED'` 配合非空/权威部门预检；数据库只防重复，不能替代服务端所属部门检查。测试环境旧键先按目录解析归属，再改部门键；冲突 HALT，现场无建模存量。
- 默认公共层不允许正常业务接口修改 owner_department_id，返回 409 `MODELING_CONTEXT_DEPARTMENT_IMMUTABLE`；发布/归档其容器返回 409 `MODELING_CONTEXT_LIFECYCLE_FORBIDDEN`，模型自身生命周期保留。一般用户不能借归档锁住全部同部门 ADS。
- 异常已有 PUBLISHED/ARCHIVED 默认上下文返回 409 `MODELING_CONTEXT_NOT_WRITABLE`；固定键不释放，初始化不新建替代计划。恢复沿用运维流程制定具体方案；不新增整体迁移/重建按钮。opadmin 的应急模型维护和既有恢复能力保留，但不能用日常 API 绕过新公共层的数据不变量。目录不可用时 F9 业务授权仍拒绝，先沿既有主机/服务恢复流程恢复依赖，不把会话内 OP_ADMIN 字符串作为故障放行开关；本 Feature 不宣称提供新的离线应急认证。T01 必须记录这一新增实时依赖的可用性影响。

### 6.3 引用、批量动作与后台

1. K77 在保存/导入落地、候选展开和执行计划生成处复用规范资产身份、dbt 解析与既有血缘，禁止新建第二套解析器/资产台账。除显式引用，实际 SQL/dbt 读集也须覆盖。仅有目录 READ 不代表可把别部门受治理公共层复制为本部门模型。
2. 对受治理模型产出解析其所属部门；对有权访问的普通外部源继续原准入，不把所有跨部门共享连接一律禁用。受治理来源缺归属/血缘时拒绝继续，不能当普通外部源放行。T01 固定各来源类型的权威映射与未知分支清单。
3. 测试环境历史跨部门 pin 仅允许同一模型中 `{modelSpecId,revision}` 集合不变的无副作用重保存；不得增加引用、改 pin、复制到新模型。该例外不授权重新物化/发布/运行；已有数据读取仍走原资产权限，不自动改写历史记录。
4. 定义保存、构建、发布、质量补跑、手动执行、部署绑定修复的实际写/执行集均要求相应模型可编辑及 K90 动作职责；仅依赖读取的已发布上游检查可读、来源准入和密级，不强求上游 EDITOR。审核要求审核职责与目标范围，保留禁止自审，不从 EDITOR 推导审核权。
5. 先展开 scope 并批量授权，再创建候选/执行记录或触发外部副作用。任何对象不可见优先通用 404；均可见但某模型无操作权返回 403 `MODEL_OPERATION_SCOPE_DENIED`，不部分执行。重放幂等请求也检查当前权限，不能凭旧成功回执绕过。
6. USER 后台请求以稳定发起人和服务端封存的 scope/版本复核；非首建人只要当前被授权即可执行，不强制等于 plan.owner。撤权/调岗/停用导致 403/阻断状态 `MODEL_EXECUTION_AUTHORIZATION_REVOKED`；目录异常保持可重试的阻断，不伪装成功。
7. SYSTEM 仅用于已有可信调度/协调器，服务认证 + 已部署 binding/candidate/version/scope 校验齐全；只能运行或核对既有部署，不获得编辑定义、授予 ACL、跨部门新引用或新发布权限。系统任务缺身份/范围证据即拒绝，不能用空 SecurityContext 与任意 owner 字符串获得权限。
8. 外部任务已开始后撤权：支持取消则请求取消并记录；否则保留真实运行结果，禁止后续以失权用户继续发布/导出或新建执行。回调仅接收可信运行结果，不凭回调重新授予用户权限；既有系统调度不因个人离职自动变成任意人可运行。

### 6.4 角色与动作矩阵

本表是本轮技术设计，T01 以 C98 核对后冻结；没有把新设计描述成用户逐项确认。部门领导增加维护/执行职责以支撑 D2；未据此新增审核职责。

| 角色 | 定义维护 | MODEL_MAINTAINER | RELEASE_REVIEWER | RELEASE_OPERATOR | 范围 |
|---|---|---|---|---|---|
| ROLE_DEPT_DATA_OWNER | 是 | 是 | 否 | 是 | 本部门，再过 ADS ACL |
| ROLE_DEPT_LEADER | 是 | 是 | 否 | 是 | 本部门管理 |
| ROLE_INST_DATA_OWNER | 是 | 是 | 是 | 是 | 所级管理；仍禁跨部门引用 |
| ROLE_INST_LEADER | 是 | 是 | 是 | 是 | 所级管理；仍禁跨部门引用 |
| ROLE_OP_ADMIN | 应急 | 是 | 是 | 是 | 应急审计；其他既有门禁保留 |
| ROLE_ADMIN | 兼容定义维护 | 否 | 否 | 否 | 不借 F9 扩张原发布职责 |
| ROLE_EMPLOYEE / 仅自定义角色 | 否 | 否 | 否 | 否 | 仅既有消费权限 |

角色可并存，职责按持有角色取并集，资源范围和密级再独立相交；UNKNOWN 角色不补权。动作到所需职责沿用既有状态机，T01 固定 endpoint/service/async 到本表的映射。两模块常量、内部目录检查与前端 allowedActions 必须有同表契约测试。建模菜单保持人工配置，迁移只补缺失内置绑定；若历史自定义绑定仍可见，导航结果须与 K73 能力取交集，不能靠人工事后删除才满足普通员工无菜单。

### 6.5 ADS 授权协议与生命周期

- 表 `modeling_model_access(id uuid pk,tenant_id varchar(128),model_spec_id uuid fk,grantee_type USER或ROLE,grantee_id varchar(128),permission EDITOR,granted_by,granted_at,revoked_by,revoked_at)`；必要身份列非空；活动部分唯一 `(tenant_id,model_spec_id,grantee_type,grantee_id) where revoked_at is null`；用户反查活动索引。模型与授权必须同 tenant，服务端不可只凭 grantId 删除。
- `GET /api/modeling/model-specs/{id}/access-grants` → `ApiResponse<{canManage:boolean,items:[{id,granteeType,granteeId,granteeName,permission,grantedBy,grantedAt}]}>`；只允许模型可读人，非管理者返回 `canManage:false,items:[]`，不泄露成员名单。
- POST body `{granteeType,granteeId,permission:"EDITOR"}`；USER 只接受目录稳定 ID，重新解析 enabled/当前角色/部门；ROLE 只接受 ROLE_DEPT_DATA_OWNER、ROLE_DEPT_LEADER，命中时仍限定模型所属部门。新建 201，重复或并发重复 200 返回有效记录；DELETE `.../{grantId}` 204，目标内已撤销 204，其他模型/租户的 grantId 404。
- 非 ADS 422 `MODEL_ACCESS_LAYER_NOT_SHAREABLE`；无管理权 403 `MODEL_ACCESS_MANAGE_DENIED`；目标用户不合格 422 `MODEL_ACCESS_GRANTEE_NOT_AUTHOR`；目标不在本部门 422 `MODEL_ACCESS_CROSS_DEPARTMENT_PENDING_APPROVAL`；目录故障 503 `MODEL_ACCESS_DIRECTORY_UNAVAILABLE`，不落库。
- owner 调岗后即使 owner_id 不变也不能编辑或管理授权；角色授予可自动覆盖后来加入本部门且持有该角色的人，但不能让失去建模角色或调岗的人持续命中。归档模型保留授权历史，不可编辑或新增授权；管理者可撤销历史有效授权。
- 新建/导入/复制 ADS 的 owner 都是本次稳定创建人，不接受请求指定他人。涉及 ADS 的类型转换改变授权范围，除转换前可编辑外还须授权管理权；首次由公共层转 ADS 限本部门领导/所级/兼容管理角色，首次归属记录转换人。退出 ADS 保留历史 owner，撤销全部有效 EDITOR 并审计；再进入 ADS 保持原 owner，不能由转换人覆盖，不复活旧授权。公共层之间转换沿用原规则；历史版本回放不得回写归属和 ACL。

## 7. 未闭合项与冻结边界

Q1–Q4、Q6、Q7 的既有产品结论保留；Q6 的权威目录复用方向不变，但字段与使用范围扩为 K86/K87。Q7 只证明静态派生链，真实验收仍未执行。

| # | 项目 | 当前处理 | 影响 |
|---|---|---|---|
| Q1 | 所级跨部门豁免 | 已确认不豁免 | K77 |
| Q2 | ADS 归属转移 | 已确认不转移，调岗失权 | K79–K81 |
| Q3 | 自定义建模角色 | 已确认不开放 | K73 |
| Q4 | 现场建模存量 | 已确认无存量，只核对测试数据 | T04/T06 |
| Q5 | 现场历史降密级 | T01 只读扫描；恢复须有具体清单和用户决定；不可访问记 NOT_RUN | 不阻断 T02 新降级封堵；阻断历史处置结论，不静默宣称无遗留风险 |
| Q6 | 被授权人权威解析 | 复用目录；按稳定 ID、enabled、角色与部门检查 | T03/T06 |
| Q7 | 大屏密级链 | C89 静态证据有效，S12 真实走查仍待执行 | T07/T09 |
| Q8 | 身份链适配证据 | 已冻结并实现：稳定认证 ID、directory_user_id、旧会话重登录、当前账号/角色/人员档案/精确部门、2秒截止时间；见实施基线 | 身份/目录自动化通过；真实目录变更未验收 |
| Q9 | 动作覆盖清单 | 已按实施基线映射定义、草稿、来源、导入、发布、运行、重放和USER/SYSTEM入口；源码已接入 | 批量/后台专项通过，真实外部执行仍待验收 |
| Q10 | 规范部门与来源归属 | 已冻结：有效非根部门精确编码/节点ID，四类来源复用资产生产者、dbt实现与物理观察；无映射拒绝 | 本机预检、来源/AST专项通过；客户环境待采集 |

T01 可先冻结 K78 + 密级审计子集供 T02 实施，不等待其余契约、现场访问或 Q5 恢复决定。其他分组按各 Task 输入冻结，G1 不因文档写全自动 PASS。

## 8. 非功能、交付与证据

| 约束 | 检查与归属 |
|---|---|
| 权威身份及时判定 | 2 秒总超时、无自动重试、每授权边界主体查询至多 1 次（同一 HTTP 请求通常一个边界，worker 执行/重试为新边界）；20/200 行不逐行调用目录；撤权后旧会话直接请求，T03/T09 |
| 列表权限无 N+1 | 模型 ACL 按当前页批量查询；记录 20/200 行 SQL/目录请求次数，对比增长原因，T06/T09 |
| 不可见信息不枚举 | 列表、详情、关系图、辅助查询、总数、错误详情逐入口断言；不可见与不存在的状态/结构一致，T04/T05/T09 |
| 批量原子拒绝 | 混合 scope 任何目标失败前不产生候选、执行或授权变更；幂等重放与并发撤权覆盖，T06/T09 |
| 密级不可并发降级 | 旧 If-Match、并发人工提升/全量保存、空值、全部用户入口；原子回滚且拒绝审计可查询，T02/T09 |
| 权限矩阵不扩按钮集 | 内部动作判定复用现有服务/状态机，UI 只消费 allowedActions；不建设第二个权限配置平台 |
| 正式交付 | 经 Git 同步到 /data/dts-stack 后测试/构建/打包；涉及平台、admin、前端和会话/模型迁移，记录同 SHA、镜像和包校验和；部署另走正式目录 |
| 发布与回退 | T03–T06 为完整建模授权切片，不能以半套权限上线；T02 可独立发布。回退不得恢复目录降密级或菜单即授权漏洞；必要时关闭建模写入口，保留消费与数据，T09 写入 release-plan |
| 验收状态 | 所有 IT 仍未执行。T07 调查 GAP、T09 执行完毕不等于 F9 通过；安全硬约束未通过不得 DONE。Q5 未执行保留现场 GAP，不能当作无历史风险 |
