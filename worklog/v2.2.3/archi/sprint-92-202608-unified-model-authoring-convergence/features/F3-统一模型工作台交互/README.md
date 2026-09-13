# F3：统一模型工作台交互

**优先级**：P0  
**状态**：IMPLEMENTED（组件/source-contract/build 通过；真实浏览器 E2E 待验收）

## 目标

在既有模型工作台删除接管/回切和来源驱动只读心智，让用户在一个页面通过 visual/code 编辑同一草稿，并使用统一保存、校验、提交和 PUBLISHED fork 动作。

## 契约定义

| 类型 | 契约 | 关键字段/签名 |
|---|---|---|
| Route | `/data-modeling/dimensions/workbench?modelSpecId={id}&view=visual|code` | 旧 `open=advanced` 继续归一化；不新增路由 |
| State | `ModelAuthoringDraftState` | `{context,draftId,etag,modelSnapshot,files,projection,dirtyByView,busy,diagnostics}` |
| Components | `ModelingWorkbenchEditor`、`AdvancedDbtWorkspace`、`DbtCodeEditor` | 同一 context/provider；Monaco 仍懒加载 |
| API | F1/F2 authoring context/draft facade | UI 不直接编排 ownership transition/convert |
| Tests | source-contract + Vitest | 控件、文案、无重复入口、URL/dirty、请求顺序 |

## UI/UX 规格

具体页面和按钮见 `assets/page-capability-matrix.md`、`assets/button-component-matrix.md`。

- 模型上下文条展示 DRAFT/PUBLISHED、pins、来源、投影覆盖，不展示“所有权”。
- 两个视图共享顶部工具栏和诊断；切换不保存也不丢 dirty。
- PUBLISHED 只显示“创建新草稿版本”；DRAFT 显示“保存草稿/校验/提交实现”。
- 删除“接管代码实现”“转为可视化维护”“当前由代码维护 · 可视化只读”和维护方式 selector。
- 空/加载/错误/权限/成功/PUBLISHED 六态均有明确反馈。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 接入统一 context 与双视图共享状态 | P0 | IMPLEMENTED | F1/T02、F2/T01 |
| T02 | 统一保存校验提交与 PUBLISHED fork 交互 | P0 | IMPLEMENTED | T01、F1/T03 |
| T03 | 落地 PARTIAL/raw node 并删除接管只读文案 | P0 | IMPLEMENTED | T01、F2/T02、F2/T03 |

## 实施证据（2026-08-20）

- 新增 `useModelAuthoringSession`，visual/code 共享 context、draftId、ETag、files、dirty、diagnostics 与命令状态。
- 页面已接入统一 authoring API，并保留 `PUBLISHED` 显式 fork 边界。
- 旧的“接管/回切/代码维护导致可视化只读”产品文案与选择器已从新 UI 移除。
- 前端聚焦测试 8 文件/88 用例、TypeScript、Biome 与生产构建通过；详见 `../../it/evidence/20260820-automated.md`。

## Definition of Ready

- [x] 页面、route、组件、按钮和四态已命名。
- [x] API 与共享状态字段已钉死。
- [x] source-contract/Chrome95 验收入口明确。
- [x] F1/F2 contract 已实现并通过聚焦自动化。

## 完成标准

- [ ] 三种 provenance 在同一页面呈现相同主动作和状态机。
- [ ] 切换视图无写请求；dirty、诊断和 ETag 保持。
- [ ] 旧接管/回切/整页只读入口从 UI 消失，旧深链仍可到 canonical 页面。
- [ ] visual 首屏不加载 Monaco；Chrome95 与双视口通过。
