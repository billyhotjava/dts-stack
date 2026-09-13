# T01: API source category 与数据源选择

**优先级**: P1
**状态**: DRAFT
**依赖**: F2/T01

## 目标

在接入任务向导中新增 API 来源类型，与数据库、文件并列。

## 范围

- 新增 source category：`database`、`file`、`api`。
- API 类型只展示 API 数据源。
- 选择数据源后拉取 connector capability 和 provider metadata。
- 保持数据库和文件现有流程不受影响。

## 完成标准

- [ ] API 入口清晰可见。
- [ ] 非 API 数据源不会出现在 API 流程中。
- [ ] capability 加载失败有降级说明。
- [ ] 老流程无视觉和行为回归。

