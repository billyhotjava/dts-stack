# F15：发布与构建单线流程体验

**优先级**：P0（大版本入版，2026-09-24 用户确认）
**状态**：IN_PROGRESS（设计）
**依赖**：F13（质量/登记/发布阶段数据）、F14（构建前检查/构建/恢复数据）；不新增后端接口。

## 目标

操作者打开「发布与构建」后，**不需要知道先点哪个页签**：一条从上到下的流程告诉他"现在走到哪一步、为什么停在这里、下一步点哪个按钮"。任何一步出错，错误只出现在出错的那一步，用业务语言说明原因和处理办法，技术细节可展开但默认收起。

## 现状问题（2026-09-24 截图与 `ModelPublishDialog.tsx` 核对）

| # | 问题 | 位置 |
|---|------|------|
| P1 | 两个页签（生成构建任务 / 发布模型）把一条顺序流程拆成两半；发布阶段条只在"发布模型"页签可见，构建失败时用户在另一页签看不到流程位置 | `ModelPublishDialog.tsx:630-640`、`ModelReleaseWorkflowPanel.tsx:218-227` |
| P2 | 「资产登记结果」固定在最上方，构建前显示"暂无当前记录"，顺序与实际（构建后才登记）相反 | `ModelPublishDialog.tsx:659`、`ModelAssetDeliveryResult.tsx` |
| P3 | 同一阻断在多处重复：失败提示、占用发布单提示、计划阻断列表、"主要阻断"摘要、ScopeSummary blocker，没有"唯一的下一步" | `:643-656, 681-687, 747-756, 767-770, 822-827` |
| P4 | 内部术语与代码直接上屏：UPSTREAM、BUILD、REUSE、候选状态码（BUILDING/QUALITY_PASSED）、发布单 ID、计划校验码、错误码 | `useModelMaterializationColumns.tsx`、`:652, 757-759, 685` |
| P5 | 执行环境、依赖策略两个下拉框放在最前，多数用户只需默认值；计划需手动"刷新计划" | `:689-714` |
| P6 | 主操作按钮位于长滚动区底部，1366×768 下首屏看不到 | `ModelMaterializationActions`、`:833-866` |
| P7 | 发布页签"操作说明"输入框对所有动作都显示，是否必填不明 | `:811-814` |

## 设计

### 1. 单线布局（替代两个页签）

```
┌ 发布与构建 · biz_ads_budget_derived_v2 r3 ───────────────────────── ✕ ┐
│ ┌ 当前状态条（固定在顶部，不随滚动消失）──────────────────────────────┐ │
│ │ ● 第 4 步 治理数据质量：缺少质量规则                                  │ │
│ │   该模型尚未配置业务质量规则。当前为"提示"策略，可继续发布。          │ │
│ │                                     [配置质量规则]  [继续发布 ▶]       │ │
│ └──────────────────────────────────────────────────────────────────────┘ │
│  ✓ 1 构建前检查      通过 · 开发环境 · 缺失上游一并构建     [展开]        │
│  ✓ 2 构建            3 张表完成 · 09-24 14:02               [展开]        │
│  ✓ 3 资产登记        目录已登记 · 分析已准备                [展开]        │
│  ✓ 4 工程验证        通过                                                 │
│  ! 5 治理数据质量    缺少质量规则（提示，不阻断）  ← 当前步骤默认展开     │
│      ├ 资产 biz_ads_budget_derived_v2：未检测                            │
│      └ 查看技术详情 ▸（错误码、执行编号、qualityRoundId）                  │
│  ○ 6 发布            等待上一步                                           │
│  ○ 7 上线就绪        等待发布                                             │
└──────────────────────────────────────────────────────────────────────────┘
```

- 阶段固定为：构建前检查 → 构建 → 资产登记 → 工程验证 → 治理数据质量 → （发布评审，仅需评审时出现）→ 发布 → 上线就绪。
- **当前步骤**默认展开，已完成步骤折叠为一行摘要，未到步骤灰显。用户可手动展开任何一步看历史。
- **状态条**永远只给出一个原因和不超过两个按钮（主操作 + 次要处置）；按钮只来自服务端 `allowedActions` / `allowedRecoveryActions` / `pageAction`，前端不自行推断可执行性。
- 批量构建（`batch`）使用同一布局，步骤摘要显示"n/m 完成"，展开后逐表列出。
- 嵌入向导模式（`embedded`，`ModelWizardFrame`）只渲染当前步骤内容，不渲染整条流程，保持现有向导行为。

### 2. 构建前检查步骤内容

- 显示：模型范围、目标数仓（F14-T06 第 6 项的只读目标卡片放在这里）、依赖构建计划。
- 执行环境与依赖策略收进「高级设置」，默认折叠，显示当前取值；修改后自动重新预览计划（去掉"刷新计划"作为必需步骤，保留为次要按钮）。
- 打开弹窗即自动预览计划；计划阻断直接成为本步骤的失败原因。
- 计划表列名与取值中文化：UPSTREAM→上游依赖、TARGET→本次模型、BUILD→需要构建、REUSE→复用已有结果；计划校验码移入技术详情。

### 3. 错误呈现规则

- 每个阶段最多一条"原因"：优先服务端 `primaryBlocker`，其次阶段失败码经错误码目录（评审 D3）翻译成用户文案；原始 `failureMessage` 与错误码、执行编号放入「查看技术详情」。
- 删除重复呈现：`主要阻断` 摘要、`ModelReleaseScopeSummary.blocker`、顶部 `failure` 横幅中与阶段原因重复的内容，统一由状态条 + 阶段原因承载；与阶段无关的请求失败（如读取失败）仍在状态条显示"状态读取失败，[重新读取]"，不清空已显示数据。
- 占用发布单（`blockingWorkspace`）作为"构建前检查"阶段的阻断原因呈现，处置按钮"关闭占用发布单"保留二次确认。
- ADVISORY 下的质量问题显示为黄色"提示"，不显示为失败；BLOCKING 下显示为红色"需处理"。状态条注明当前适用策略（冻结候选显示冻结时的策略）。

### 4. 发布步骤

- "操作说明"只在所选动作需要时出现（驳回、回滚必填；提交评审可选；发布不显示），并标注必填。
- 回滚、驳回保留 danger 样式与确认。

## 契约（只消费既有接口，不新增后端）

| 数据 | 来源（既有） | 用于 |
|------|--------------|------|
| 发布单工作台 | `getReleaseCandidateWorkbench` → `candidate`、`entryEvidence`、`evidence`、`governanceQuality`、`primaryBlocker`、`allowedActions` | 阶段状态、原因、按钮 |
| 构建计划 | `previewMaterializationPlan` → `orderedEntries`、`blockers`、`planChecksum` | 构建前检查 |
| 交付状态 | `getModelDeliveryStatus` | 资产登记步骤 |
| 运行绑定 | `getPlanExecutionWorkspace` → `executionBinding.allowedActions` | 上线就绪 |
| 写操作 | 现有 `build/createReleaseCandidate/runReleaseCandidateQuality/rerunReleaseCandidateGovernanceQuality/publishReleaseCandidate/...` | 不变 |

新增前端纯函数契约（T01）：

```ts
type JourneyStageKey = "precheck" | "build" | "registration" | "engineering" | "governance" | "review" | "publish" | "online";
type JourneyStageState = "waiting" | "active" | "passed" | "warning" | "failed" | "rolled-back";
interface JourneyStage { key: JourneyStageKey; label: string; state: JourneyStageState; summary: string; reason?: string; technical?: { code?: string; runId?: string; detail?: string } }
interface ReleaseJourney { stages: JourneyStage[]; current: JourneyStageKey; headline: string; primaryAction?: JourneyAction; secondaryAction?: JourneyAction; policy?: "ADVISORY" | "BLOCKING" }
function deriveReleaseJourney(input: ReleaseJourneyInput): ReleaseJourney
```

`JourneyAction` 只能由输入中的服务端动作映射得到；输入缺失（读取中/失败）时 `current` 停在最后一个可确认的阶段，headline 为"状态读取中/读取失败"，不得推测成功。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 发布旅程派生模型（纯函数 + 表驱动测试） | P0 | READY | - |
| T02 | 弹窗单线布局重构（去页签、状态条、阶段折叠） | P0 | DRAFT | T01 |
| T03 | 业务语言与错误去重呈现 | P0 | DRAFT | T01；评审 D3 错误码目录 |
| T04 | 与 F13-T05 / F14-T06 集成（阶段内容插槽） | P0 | DRAFT | T02；F13-T04、F14-T03 接口 |
| T05 | 体验验收（五条黄金路径 × 四态 × 1366/窄屏/Chrome95） | P0 | DRAFT | T02–T04 |

**与 F13/F14 的分工**：F15 负责弹窗的信息架构、布局和"当前步骤/下一步"的推导；F13-T05（质量与发布阶段内容）、F14-T06（构建进度、恢复操作、目标数仓卡片）负责各自阶段内部内容，作为 F15 阶段插槽渲染。三者修改同一文件，由 F15 集成人统一合并。

## Definition of Ready

- [x] 目标可验收（操作者无需切页签即可知道当前步骤与下一步）
- [x] 契约：只消费既有接口，已列出；新增纯函数签名已定
- [x] UI 落点：模型工作台 → 模型行"发布与构建"弹窗；向导嵌入模式保持
- [ ] T03 依赖错误码目录（评审 D3）尚未建立 → T03 保持 DRAFT
- [x] 验收可验证：T05 黄金路径

## 完成标准

- [ ] 五条黄金路径在本机正式部署实例上走通，截图入 `it/F15-发布与构建体验验收.md`：①首次构建到发布成功；②构建失败（目标数仓不可用）；③资产登记失败后重试；④治理质量缺规则 ADVISORY 可继续 / BLOCKING 阻断；⑤被其他发布单占用。
- [ ] 1366×768 首屏可见状态条与主操作；窄屏无横向滚动；Chrome95 可操作。
- [ ] 任一阶段失败时，页面上同一原因只出现一次。
- [ ] 界面不出现 UPSTREAM/BUILD/REUSE/候选状态码等内部取值（技术详情内除外）。
