# F6: 指标工作台语义编排编辑器

**优先级**: P0  
**状态**: IN_PROGRESS

## 目标

将指标工作台从“查看关系画布”推进为可编辑的语义编排界面。线条代表真实指标建模关系，而不是纯视觉注释：业务对象到指标继续回写 `objectId`，指标到指标的派生关系写入目标指标 `formulaJson.dependsOnMetricIds`。

## 来源

- 设计文档：`docs/superpowers/specs/2026-07-03-metric-workbench-workflow-editor-design.md`
- 当前基础：Sprint-56 已完成 React Flow 拖拽、连线绑定和浏览器位置保存。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 关系模型 helper 与预检规则 | P0 | DONE | - |
| T02 | Dify 风格画布工具栏与可编辑边 | P0 | DONE | T01 |
| T03 | 节点/边配置面板与保存删除闭环 | P0 | DONE | T01,T02 |
| T04 | source-contract、单测与浏览器验证证据 | P0 | IN_PROGRESS | T01,T02,T03 |

## 完成标准

- [x] 用户可以从指标节点拖线到指标节点，形成 `METRIC_DERIVES` 派生关系。
- [x] 用户可以选中关系边并编辑关系说明或派生表达式。
- [x] 用户可以删除业务对象绑定边和指标派生边。
- [x] 预检能识别孤立指标、环依赖、草稿依赖和公式 JSON 问题。
- [x] 原有业务对象 -> 指标绑定能力不回退。
- [x] helper 单测、source-contract、`pnpm exec tsc --noEmit` 和目标文件 `git diff --check` 通过。
- [ ] 真实业务数据下的指标 -> 指标连线 PUT payload 拦截与删除 payload 截图留证。

## 边界

- 不新增后端表、迁移或专用 metric relation API。
- 不做多人协作、画布版本管理或服务端布局保存。
- 不实现通用流程引擎节点，例如 LLM、HTTP 工具、条件分支。
- 不把线条作为只影响视觉的本地注释。
