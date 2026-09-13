# F1: 模型双模式工作台

**优先级**: P0
**状态**: IN_PROGRESS（T03/T01/T02 源码与聚焦自动化完成；等待 F0 真实浏览器基线）

## 目标

建模人员打开一个已保存模型后，可在同一页面清晰切换“可视化模式 / 代码模式”；控件状态来自模型表示能力，刷新、分享链接和旧深链均保持一致。

## Feature 关联

- 上游：F0/T01 提供真实账号/浏览器基线；既有 representation capability 是唯一访问事实源。
- 下游：F2 在 code view 提供预览/接管，F3 在 visual view 提供只读投影，F6/T02 与 F7 继续使用同一 editor/URL/capability 承载依赖和转换编辑。
- 约束：本 Feature 只拥有视图与入口，不拥有依赖、草稿、物化或发布状态；下游不得再加第二个工作台。

## 契约定义

| 类型 | 契约 | 关键字段/签名 |
|---|---|---|
| URL | `?modelSpecId={UUID}&view=visual\|code` | 缺省 `visual`；`open=advanced` 规范化为 `view=code` 并删除旧参数 |
| 状态解析 | `resolveModelingModeAccess(business, technical, canMaintain)` | 返回 visual/code 的 `{visible,editable,reason,action}`；不得只判断模型是否持久化 |
| REST | `GET /api/modeling/model-specs/{id}/representations` | **路径为复数**；query 必填 `modelRevision:int` 与 `representationScope:BUSINESS\|TECHNICAL`，可选 `implementationRevision:int`（`ModelRepresentationResource.java:41-47`） |
| BUSINESS 表示 | 既有 representation API，**零改动** | `OPEN_VISUAL`（DBT_MANAGED）、`OPEN_VISUAL`+`EDIT_VISUAL`（DESIGNER） |
| TECHNICAL 表示 | **由 F1/T03 扩展**——首版误标为“既有” | `OPEN_ADVANCED_DBT`（DBT_MANAGED，语义不变）或新增的 `OPEN_DBT_PREVIEW`（DESIGNER，只读）。接管/转换动作**不进 `allowedActions`**，由 F2 的 transition validate 单独判定 |
| 导航保护 | 当前 dirty blocker | visual draft 与 dbt draft 任一 dirty 均阻止离开模型或切换模型 |

## UI/UX 规格

- **入口**: 数据建模 → 模型工作台 → 选择一个 ModelSpec；不新增菜单和页面。
- **布局**:

  ```text
  ┌ 基本信息 / 实现摘要 ─────────────────────────────┐
  │ 模型 rN | 实现 rN | 所有权 | 发布状态              │
  ├ 字段与实现 ───────────────────────────────────────┤
  │              [可视化模式] [代码模式]               │
  │  可视化字段表 / 只读投影   或   代码预览/代码工作区   │
  └───────────────────────────────────────────────────┘
  ```

- **四态**:
  - 加载：保持模型标题，模式区显示“正在读取模型表示能力”。
  - 成功：当前模式高亮，显示可编辑或只读说明。
  - 不可用：控件保留但禁用，直接显示 capability reason。
  - 错误：表示读取失败时不得猜测可写能力，提供“重试”。
- **关键交互**: Tab 切换只更新 URL 与本地视图；不调用任何写 API，不产生 revision。
- **兼容**: 旧 `open=advanced` 链接一次性归一化；浏览器前进/后退、刷新、复制 URL 均恢复相同模式。
- **Chrome 95**: 使用现有按钮/分段组件和基础 CSS，不使用仅新浏览器支持的 selector/API。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T03 | 扩展 DESIGNER 的技术表示只读能力 | P0 | IN_PROGRESS | F0/T01 |
| T01 | 建立双模式访问与 URL 状态契约 | P0 | IN_PROGRESS | T03（需要 `OPEN_DBT_PREVIEW` 能力码） |
| T02 | 重构字段区模式切换并收敛重复入口 | P0 | IN_PROGRESS | F0/T01、T01 |

**执行顺序为 T03 → T01 → T02**（编号保留首版；T03 不依赖接管 bundle 形态）。

## Definition of Ready

- [x] URL 与 dirty guard 契约已钉死
- [x] UI 控件位置和四态已定义
- [x] 不新增页面/菜单
- [x] REST 路径已按源码核对更正为 `representations`
- [ ] capability 契约待 T03 落地 —— 首版误认为 `OPEN_DBT_PREVIEW` 已存在（复核结论 C）
- [ ] F0 登录及浏览器 harness 通过（T02 前置）

## 完成标准

- [ ] 同一模型可在 visual/code 间无副作用切换。
- [ ] “高级 dbt 工作区”重复按钮移除，字段区只保留一个分段入口。
- [ ] DESIGNER 模型的代码模式可见且只读；DBT_MANAGED 的既有高级入口语义零回归。
- [ ] 旧深链、只读账号、capability 错误和 dirty guard 有自动化回归。
