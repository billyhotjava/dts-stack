# 数仓规划、数据标准与维度建模闭环设计

> **状态：IMPLEMENTED / UI 骨架 SUPERSEDED**——Sprint-63 的规划、标准和维度闭环能力已经实现；本文定义的页面顺序和规划上下文骨架已由 `worklog/v2.2.3/sprint-65-202607/assets/classic-warehouse-planning-golden-path-design.md` 取代，历史验收与后台能力继续保留。

**日期**: 2026-07-11  
**范围**: `dts-platform-webapp` UI-first 端到端闭环  
**关联 Sprint**: Sprint-63

## 1. 背景与目标

数据集成已经具备数据源、连接测试和接入状态基础。下一步需要让客户可以沿着一条清晰路径完成：

```text
主题域/数仓分层规划
  -> 选择数据标准与数据元
  -> 生成字段标准草稿
  -> 进入维度建模
  -> 形成可微调的模型草稿
```

当前页面已经分别具备主题域管理、数据元管理、标准字段草稿、低代码建模和 SQL 建模能力，但中间缺少一个统一的规划上下文。用户目前可以从标准进入建模，却无法证明模型属于哪个主题域、数仓层和建模意图。

本设计的目标是补齐这一条最小可验收闭环，而不是新建一个孤立的规划系统。

## 2. 方案比较

### 方案 A：只增加路由参数

在页面间传递 `domainId`、`warehouseLayer` 和 `modelingMode`。

- 优点：改动小，立即可见。
- 缺点：刷新后无法恢复规划草稿，参数容易被手工修改，无法统一展示规划来源。

### 方案 B：版本化 UI 规划上下文（推荐）

以 `planningId` 为入口，在 session storage 保存一份轻量规划草稿，同时通过 query 参数把上下文带到标准和建模页面。

- 优点：保持 UI-first，支持刷新和页面回跳；不伪造后端持久化；可以逐步替换为后端对象。
- 缺点：当前只在浏览器会话内有效，不能跨用户共享。

### 方案 C：直接新增后端规划实体和模型规格 API

规划、标准绑定和维度模型都落库，由后端生成规划 ID 和版本。

- 优点：完整、可审计、可多人协作。
- 缺点：涉及数据库、权限、版本和迁移，无法作为当前第二步的最小闭环快速验收。

本 Sprint 采用方案 B。后端规划对象作为下一阶段缺口，不在 UI 中显示为已持久化。

## 3. 核心契约

### 3.1 规划上下文

```ts
type WarehousePlanningContext = {
  version: 1;
  planningId: string;
  domainId: string;
  domainName?: string;
  warehouseLayer: "ODS_RAW" | "ODS_STANDARDIZED" | "DWD" | "DWS" | "ADS";
  modelingMode: "dimension"; // 新增 mode（如 fact）不升 version：解析器对未知 mode 归 blocked，即向前兼容
  sourceId?: string;
  standardDraftId?: string;
  createdAt: string;
  updatedAt: string;
};

type WarehousePlanningStatus = "draft" | "ready" | "blocked";
```

**status 是计算态，不入存储。** 存储只存事实字段；`resolvePlanningStatus(context, deps)` 纯函数现算（对齐 Sprint-62 "绿色必须可信"原则：standardDraftId 后续失效时，存储态 status 会漂移成假 ready，计算态不会）。

维度建模使用 `warehouseLayer=DWD` 和 `modelingMode=dimension`。不新增 `DIM` 作为数仓分层，避免破坏现有 SQL 建模的 DWD/DWS/ADS 层级和资产层级规则；物理维度表是否采用 `dim_` 命名属于后续模型生成策略。

### 3.2 URL 参数

页面之间保留可诊断的白名单参数：

```text
journey=e2e-data-product
planningId=<session planning id>
domainId=<topic domain id>
warehouseLayer=DWD
modelingMode=dimension
sourceId=<optional source>
standardDraftId=<optional standard draft>
```

URL 只传递上下文索引和回跳信息，完整草稿从版本化 session storage 读取。读取失败时页面显示“规划上下文不可用”，不得生成绿色完成态。

**session 与 URL 不一致的裁决规则**：session 草稿是权威。`planningId` 命中但 URL 携带的 `domainId/warehouseLayer/modelingMode` 与草稿不一致时，判为 blocked 并提供“以规划草稿为准继续 / 重新创建规划”两个动作；不静默采用任何一方。

### 3.3 与旅程机制的集成（关键决策）

旅程侧的全部 URL 构造器（`buildJourneyUrl`、`buildJourneyParamClearUrl`）、旅程快照（journeySnapshot）与上下文条只透传 `JOURNEY_CONTEXT_PARAM_KEYS` 白名单参数。若不处理，客户在带规划上下文的页面点“继续下一步/返回工作台/清除无效参数”会**静默丢失全部规划参数**，旅程快照恢复出的 URL 也不含规划。

**决策：方案 (a)——把 `planningId/domainId/warehouseLayer/modelingMode` 纳入 `JOURNEY_CONTEXT_PARAM_KEYS`。** 规划上下文本质是旅程上下文的组成部分。连锁改动（全部计入 F1/T01 影响范围）：

- `journeyContext.ts`：PARAM_KEYS、`JOURNEY_CONTEXT_PARAM_LABELS`（规划/主题域/数仓层/建模模式）。
- `journeySnapshot.ts`：params 类型随 key 联合类型自动扩展，快照测试补规划参数用例。
- `journeyArtifactValidation.ts`：新 key 无校验器时按现有规则自然落 unknown，`ARTIFACT_VALIDATION_API_NAMES` 补规划实体接口名（见第 9 节）。
- 既有测试回归：快照 round-trip、清参 URL、Bar 上下文标签。

## 4. 页面职责与数据流

### 4.1 主题域/数仓规划页

`SubjectAreasPage` 在选中主题域后提供规划卡片：

- 选择 `DWD / 维度建模` 作为第一条可执行规划。
- 显示主题域、当前规划状态、数据源和标准草稿缺口。
- 创建或恢复 `WarehousePlanningContext`。
- 进入数据元页时携带 `planningId/domainId/warehouseLayer/modelingMode`。

### 4.2 数据标准/数据元页

`ElementsPage` 在存在 planning context 时显示来源条：

- 当前主题域和规划层级。
- 需要输出的标准字段范围。**来源条仅展示规划来源，不过滤数据元列表**（当前数据元没有主题域归属字段，过滤关系属于后端规划实体阶段的能力）。
- “生成字段落标草稿”写入 `standardDraftId`，并保留规划上下文。
- 没有可用数据元时显示阻断原因和返回规划动作。

### 4.3 维度建模页

`LowCodeDevelopmentPage` 和 `SqlModelingPage` 读取 planning context：

- 展示“来自数仓规划”的来源证据。
- 将 `modelingMode=dimension` 映射为维度模型候选，不把页面伪装成已经生成物理表。
- 模型草稿继续携带 `planningId/domainId/standardDraftId`。
- 缺少标准草稿时，生成模型动作保持 disabled，并给出回到数据元页的动作。

## 5. 状态与错误处理

- `draft`：规划已创建，但尚未生成标准草稿。
- `ready`：存在标准草稿，可进入维度建模。
- `blocked`：主题域、数据元或规划上下文不可用；显示具体缺口和下一步。
- session storage 不可用：降级为当前 URL 上下文，标记“刷新后需要重新选择规划”，不抛异常。
- **旅程快照恢复但规划草稿已失效**：旅程快照存 localStorage（跨浏览器重启），规划草稿存 sessionStorage（关标签即失）。“继续上次旅程”可能恢复出带 `planningId` 但 session 无草稿的 URL——此时按“URL 最小上下文 + 提示重新确认规划”降级，必测场景。
- planning context 版本不兼容：丢弃旧草稿并显示“规划上下文已过期”，不得复用未知字段。
- 后端规划持久化缺失：显示 API 缺口，不显示“已保存/已发布”。

## 6. 验证策略

采用 TDD，先写源码契约测试，再修改页面：

1. 规划上下文纯函数：创建、读取、版本校验、URL 构造和缺口计算。
2. 主题域页：选中主题域后能创建 DWD/维度规划并跳转标准页。
3. 数据元页：规划上下文可见，生成标准草稿后继续跳转建模。
4. 低代码/SQL 页：读取规划和标准上下文，显示维度模型模式和阻断状态。
5. 前端 `tsc`、`pnpm build`、相关 source-contract 和 `git diff --check`。

本 Sprint 不把浏览器登录/DNS 作为实现完成条件；真实浏览器视觉证据继续挂靠 Sprint-61 F9。

## 7. 验收口径

- 客户从一个主题域出发，不需要重新理解菜单，就能进入数据元和维度建模。
- 标准草稿页面能说明它属于哪个主题域、哪个数仓层、哪个建模意图。
- 维度模型草稿能说明来源规划和标准字段；缺标准时不能误生成“已完成模型”。
- 刷新当前会话后规划上下文可恢复；版本不兼容或 storage 失败时有明确 blocker。
- 所有新增 query 参数和 session 草稿都有测试覆盖。

## 8. 明确不做

- 不新增后端规划实体、数据库迁移或跨用户共享。
- 不在本 Sprint 实现完整 SCD1/SCD2、事实表/维表自动推导。
- 不重写主题域、数据元、低代码和 SQL 页面。
- 不新增 `DIM` 数仓层；维度建模先作为 DWD 层内的建模模式表达。

## 9. 后端 API 缺口清单（命名，UI blocker 与后端排期共用）

| 缺口 | 未来接口（建议） | 当前替代 |
|------|------------------|----------|
| 规划实体读写 | `GET/POST/PUT /api/governance/warehouse-plannings/{id}` | 版本化 sessionStorage 草稿 |
| 按主题域查询规划 | `GET /api/governance/subject-domains/{domainId}/plannings` | 无（单实例 session） |
| 标准草稿关联规划的持久化 | `PATCH /api/modeling/standard-binding-drafts/{id}/planning` | 草稿 metadata 本地扩展 |
| 维度模型候选登记 | `POST /api/modeling/models?mode=dimension` | 模型草稿以 query/metadata 携带来源 |

UI 中出现的规划相关 blocker 一律引用本表接口名，与 Sprint-62 `ARTIFACT_VALIDATION_API_NAMES` 惯例一致。
