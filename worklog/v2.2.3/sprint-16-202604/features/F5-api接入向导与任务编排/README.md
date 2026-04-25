# F5: API 接入向导与任务编排

**优先级**: P1
**状态**: DRAFT
**依赖**: F1, F2, F3, F4

## 目标

在平台前端提供完整的 API 接入任务创建、preview、映射、调度、运行观察和重跑入口，避免用户通过 JSON 高级参数完成配置。

## Task 列表

| ID | Task | 优先级 | 状态 |
|---|---|---|---|
| T01 | API source category 与数据源选择 | P1 | DRAFT |
| T02 | Provider 驱动的鉴权配置表单 | P1 | DRAFT |
| T03 | Preview、schema mapping 与字段编辑 UI | P1 | DRAFT |
| T04 | 同步模式、分页、cursor 与调度 UI | P1 | DRAFT |
| T05 | 任务详情、重跑、回补与诊断入口 | P1 | DRAFT |

## 完成标准

- [ ] 普通数据开发人员无需编辑原始 JSON。
- [ ] 鉴权字段由后端 provider metadata 驱动。
- [ ] Preview 到 ODS mapping 的路径清晰可回退。
- [ ] 任务运行失败时能直接看到分类和建议动作。

