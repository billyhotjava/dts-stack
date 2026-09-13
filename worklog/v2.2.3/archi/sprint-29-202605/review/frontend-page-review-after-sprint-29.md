# Sprint-29 后前端逐页 Review 约束

**状态**: IN_PROGRESS
**日期**: 2026-05-06

## 目标

Sprint-29 之后的前端更新必须从真实页面体验出发，不能只检查组件或接口。每个被纳入本轮重构的页面都要完成一次页面级 review，再进入代码修改和验证。

## 页面级 Review 规则

- 每个路由页面都要按真实业务工作流 review：入口、主任务、次任务、保存/提交反馈、异常/空数据状态。
- 有画布、表格、SQL 编辑器、模型编辑器的页面，主工作区必须是第一视觉优先级；禁止在桌面端把主工作区压缩成窄列。
- 业务操作页优先使用“顶部上下文选择 + 主工作区 + 右侧/底部配置区”，避免三栏同时争抢宽度。
- 每页检查桌面、窄屏两类布局；桌面不低于 1366px，窄屏按单列堆叠。
- 所有页面改动后至少跑 TypeScript；涉及组件交互的页面补充或更新定向测试。
- Chrome 95 禁用 API 每次前端批量修改后 grep 一次。

## 当前 Review 记录

| 页面 | 路由 | 结论 | 处理 |
|---|---|---|---|
| 业务对象 Join | `/metrics/semantic/objects` | 原布局形成“对象/DWD 模型 + 画布 + Join 条件”三栏，画布高度 430px，主任务不突出 | 已改为顶部对象/模型选择区 + 620px 大画布 + 右侧 Join 条件栏 |
| 语义建模工作台内嵌 Join | `/metrics/semantic/workspace` 的 objects section | 原 7/10/7 三栏导致画布仅 320px 高 | 已改为 DWD 横向素材栏 + 560px 大画布 + 右侧 Join 条件栏 |

## 后续页面清单

- `/metrics/semantic/overview`
- `/metrics/semantic/subjects`
- `/metrics/semantic/objects`
- `/metrics/semantic/metrics`
- `/metrics/semantic/datasets`
- `/metrics/semantic/publish`
- `/metrics/semantic/runs`
- `/explore/etl/orchestration`

后续每改一个页面，都要在“当前 Review 记录”里补结论、处理方式和验证命令。
