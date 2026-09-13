# T05: IT admission 与回滚 runbook

**优先级**: P0
**状态**: READY
**依赖**: F1-F4

## 目标

定义 Sprint-35 的最终 IT 准入和回滚证据，确保上线前能证明分层入口、安全策略和发布闭环成立。

## 技术设计

- IT 覆盖 DWS 默认建模、DWD 生成 DWS 候选、ADS 复用、invalid layer、grain mismatch、platform unavailable、policy mismatch。
- 前端 Playwright 覆盖层级导航、画布节点、诊断定位、发布按钮状态。
- 后端 focused tests 覆盖 graph preflight、artifact generator、platform client、validation gateway。
- 回滚 runbook 覆盖前端路由、graph draft version、metric artifact version、platform publish record、BI/lineage registration。

## 影响范围

- `worklog/v2.2.3/sprint-35-202605/it/README.md`
- `worklog/v2.2.3/sprint-35-202605/it/evidence/**`
- 后续 `source/**/test/**`

## 验证

- [ ] IT README 中每类证据都有路径。
- [ ] 阻断条件覆盖核心安全和架构边界。
- [ ] 回滚不要求直接删除生产 dbt 表。

## 完成标准

- [ ] Sprint-35 可以用 evidence 驱动验收，而不是口头确认。
