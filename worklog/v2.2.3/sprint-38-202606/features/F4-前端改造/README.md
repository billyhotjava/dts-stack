# F4: 前端改造

**优先级**: P1
**状态**: IN_PROGRESS

## 目标

dts-platform-webapp 支持 API 数据源全流程：数据源登记（鉴权动态表单+凭据不回显）、入湖任务向导（资源/分页/游标配置）、执行详情展示。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 数据源表单API类型 | P0 | IN_PROGRESS | F1-T01 契约 |
| T02 | API入湖任务向导 | P0 | IN_PROGRESS | T01 |
| T03 | 执行详情与错误展示 | P1 | IN_PROGRESS | F3-T02 |

## 完成标准

- [ ] 数据源/任务/执行三个页面全流程可操作，敏感字段不回显
- [ ] 鉴权表单由契约 authProviders 驱动，disabled 项不可选
- [ ] 遵循分页统一约定（CompactTable，默认 10 条/页，切换条数刷新）
