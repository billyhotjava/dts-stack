# T05: 架构 review gate 固化

**优先级**: P0
**状态**: READY
**依赖**: T01-T04

## 目标

把架构评审变成实施前置门禁，后续 API、前端、后端、安全任务必须逐条引用。

## 技术设计

- 在 `assets/review-mechanism.md` 中设立 Gate 1。
- Gate 1 检查 DWS/ADS 默认入口、DWD 高级入口、platform 事实源、非目标和状态机。
- 未通过 Gate 1 的实现不得进入代码阶段。

## 影响范围

- `worklog/v2.2.3/sprint-35-202605/assets/review-mechanism.md`
- `worklog/v2.2.3/sprint-35-202605/it/README.md`

## 验证

- [ ] Review gate 有明确 checklist。
- [ ] Gate 1 的每一项都能映射到 README 或 PRD。

## 完成标准

- [ ] Sprint-35 的后续实施不再需要重新争论 DWD/DWS/ADS 入口边界。
