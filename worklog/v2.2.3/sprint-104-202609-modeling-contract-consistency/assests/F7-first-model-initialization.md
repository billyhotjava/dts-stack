# F7 首次建模初始化与菜单授权一致性

**日期**：2026-09-10　**来源**：[S10DC-80](https://jira.yuzhicloud.com/browse/S10DC-80)；用户确认纳入 Sprint-104。

## Review：现行规则 → 冲突 → 影响 → 修复 → 验收

| 已有 Feature/Task | 冲突与影响 | 本次修订与验收 |
|---|---|---|
| F1-T02 创建契约 | create/modelSpec 强制 planId，首次安装没有规划记录，模型入口无法满足前置条件 | 首次创建缺省 planId 由服务端解析；显式 ID 仍严格校验；IT-44 |
| F1-T05 草稿恢复 | 前端先建上下文再保存模型，第二步失败会留下半成品，重试按操作 ID 可产生多个默认上下文 | 同租户稳定默认上下文，与模型保存同事务；并发/失败回滚 IT-45 |
| F3-T04 三步页面 | 旧规划入口隐藏，初始化责任没有随入口调整；页面菜单可见仍被后端负责人/部门角色挡住 | 保存按钮只提交一次模型操作，菜单授权用户可建模；IT-46 |
| F3-T06 零接入验收 | 无来源不等于无建模上下文；旧库记录掩盖首次安装缺口 | 明确 warehouse_plan 为零的隔离初始化验证，及空库真实页面验收；IT-44/47 |
| F2/F4/F5/F6 | 交付、性能、依赖和指标是后续能力，不能替代第一条模型的创建前提 | 保留各自实现和未完成项；本次不重复计算这些 feature 的通过范围 |

## 冻结契约

1. 当前权限由菜单控制。建模请求必须登录；同租户内不再额外要求规划负责人、部门数据负责人或域维护角色。保留租户隔离、actor 与登录身份一致、目录/来源可见性、密级、归档/只读状态、字段有效性及版本 CAS。后台无登录任务保留原 owner 校验，不借此放宽服务身份。
2. 模型工作台新建“保存设计” → `POST /api/modeling/model-specs/draft-operations`，请求保持 `{create,modelSpec,implementation,saveMode}`；仅首次创建允许 create.planId 和 modelSpec.planId 同时缺省/null。显式非空 ID 不覆盖，单边缺省或不一致继续返回原字段错误。`saveMode=DEFINITION_ONLY` 仍不接受 implementation。成功 201，原幂等重放 200，响应模型含真实 planId 和 ETag；模型幂等键及内容冲突判断不变。
3. 同一服务端初始化入口供 `POST /api/modeling/model-specs` 和 `/dimension` 的首次创建复用；分别只补根 planId 或 modelSpec.planId。更新、读列表、刷新页面不初始化。严格 DTO 解码及领域校验继续在补齐后的请求上执行。
4. 默认上下文复用 `modeling_warehouse_plan` 与现有 `WarehousePlanApplicationService.create`，连同默认 policy 一并创建，不增加业务表/迁移/平行 owner。租户取服务端配置，owner 取已认证 actor，名称“数据建模”，模式 BUSINESS_FIRST，稳定幂等键 `modeling-context:default:v1`。优先稳定默认记录，其次按创建时间/ID 复用旧前端 `modeling-context:` 自动记录；不任意选中普通规划，不迁移或删除历史记录。
5. 首次无上下文时使用租户粒度 PostgreSQL 事务锁，锁内再次查找，再调用既有创建 owner。初始化与完整模型保存共享事务，任一步失败全部回滚；并发不同用户只产生一个默认上下文。已有上下文走无锁读取路径；默认记录只读时明确报错，不静默换一个上下文。模型和已有上下文生命周期校验保留。
6. 前端新建保存不再调用创建规划接口；内部草稿可保留空 planId，传输层省略空值；只对新建放开 planId 缺省校验，其他错误不跳过。失败保留当前输入和 creationOperationId，可原位重试；用户无需看到规划初始化入口或管理员初始化提示。

## Context Ledger

| 编号 | 现有落点与责任 |
|---|---|
| C-F7-01 | `modelingImportContextService.ts:29` 默认解析目前按首条列表并可调用 createWarehousePlan；仅保留读取用途 |
| C-F7-02 | `modelDefinitionCreation.ts:30`、`modelWorkbenchService.ts:1361` 是两条新建保存路径；`modelSpecApi.ts:359` 是统一原子请求 |
| C-F7-03 | `ModelDraftOperationResource.java:53` 解码必填 planId；`ModelDraftSaveApplicationService.save/saveDefinition` 已拥有模型创建/更新/实现事务和重放校验，继续复用 |
| C-F7-04 | `WarehousePlanApplicationService.create` 创建 plan、policy，唯一 `(tenant_id,idempotency_key)` 防重复；不同 actor 请求 hash 不同，需要锁后重读避免并发冲突 |
| C-F7-05 | `ModelSpecPlanWriteAccessAdapter.canMaintain`、`WarehousePlanAuthorizationGuard` 及建模 REST 角色表达式与当前菜单规则冲突；仅修改建模范围，来源权限不直接清空 |
| C-F7-06 | `ModelSpecDomainWriteAccessAdapter` 应以已有域可读性判断模型引用许可，不要求维护目录域本身；域 owner 不变 |

## 非功能与验证预算

- 热路径一次默认记录查询，无写入/初始化锁；冷路径只使用事务锁，不应用无限重试；事务结束释放锁。
- 隔离 PG 验证：空上下文首次成功、保存失败无残留、两个 actor 并发共用、重试重放、租户分离、历史自动上下文兼容、显式只读/非法 ID 不绕过。
- 前端专项验证单次 POST、字段校验和错误恢复；接口验证匿名拒绝、普通已登录用户通过建模权限门槛。
- 源码目录仅编辑/静态检查/review/commit/push。部署目录同 SHA 正式测试与构建；记录镜像/包 SHA，按正式交付流程部署。不得改业务数据库或容器补丁。
- 当前后端健康已为 UP；当前 CUA 未暴露浏览器，真实空库页面/Chrome95 验收仍是 GAP，由 F7-T04 跟踪，不用单测替代。

## Gate 与追溯

G0：源码/构建路径已有证据；真实空库浏览器 GAP。G1：以上服务端/前端/数据契约已冻结。G2：待实现与一次聚焦 review。G3：待正式构建交付。G4：IT-44–47 待执行。

| IT | Task | 必须留存的证据 |
|---|---|---|
| IT-44 首次保存 | F7-T01/F7-T03 | 零上下文初始计数、保存返回真实 planId、同租户上下文与模型关联 |
| IT-45 原子性/并发 | F7-T01 | 失败全部回滚；不同 actor 并发仅一上下文；重放身份不变 |
| IT-46 菜单权限一致 | F7-T02/F7-T03 | 普通菜单用户可保存/继续编辑；匿名/跨租户/无效来源拒绝 |
| IT-47 正式交付与页面 | F7-T04 | SHA、正式测试、包/镜像校验、部署、真实空库页面四状态/Chrome95 分项结果 |

## F7-T01/F7-T03 默认上下文读取补齐（2026-09-10）

现状复核发现，`loadModelWorkbenchContext` 的只读来源加载仍从规划列表取第一项，`emptyModelDraft` 又将其当成用户显式指定的 planId。即使后端支持默认初始化，新建也会跳过默认解析；旧 PUBLISHED 规划排在列表首项时会阻断保存。这是 F7 契约 4/6 的遗漏。

- 新增只读 `GET /api/modeling/model-specs/creation-context`，响应 `{planId: UUID|null}`，tenant 只取服务端配置；复用初始化服务相同的默认记录查询规则，不创建 plan/policy。只读默认记录可返回 ID 供读取来源，写入仍在保存时检查只读状态。
- `resolveDefaultModelingContextId` 改为读取该接口；规划列表保留给显式导入上下文选择，不参与新建默认选择。
- `emptyModelDraft` 创建的 ModelSpec 草稿 planId 留空，首次保存由服务端补齐。已有模型恢复/更新及 API 显式 planId 保持原值。来源列表使用服务端返回的同一默认上下文，避免向新模型提供其他规划的绑定。
- 验收：有普通旧规划但无默认上下文时，GET 不初始化；GET 与 POST 默认 ID 一致；归档默认可读但不可写；所有新建种类不继承列表上下文，已保存模型保留 planId；查询错误原样反馈，不退回随意选取规划。

## F7-T02 业务维度入口权限补齐（2026-09-10）

工作台 `createKind=dimension` 调用既有 `/api/modeling/dimension-definitions` 创建/维护可复用业务维度，不经过 DimensionModelResource。DimensionDefinitionResource 仍使用 CATALOG_MAINTAINERS，与 F7 菜单规则冲突；改为已认证准入。下游已复用 ModelSpecDomainWriteAccessPort，继续使用域可见性与来源独立约束，无须更改维度台账。原创建/更新/确认/退役 DTO、服务端 tenant/actor、ETag 及状态校验保持；新增普通 EMPLOYEE 与匿名的方法权限验证，恢复该旧测试类在正式 Maven 编译清单中的入口。
