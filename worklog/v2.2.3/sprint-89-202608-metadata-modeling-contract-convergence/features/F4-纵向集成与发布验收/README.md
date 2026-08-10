# F4: 纵向集成与发布验收

**优先级**: P0
**状态**: DRAFT / BLOCKED_INPUT
**依赖**: F1～F3
**价值**: 用一条真实、可回滚的纵向链证明元数据更新对建模的影响被正确控制，而不是只证明单个类或页面可用。

## 验收主链

collect v1 → catalog stable identity → plan confirm v1 → reverse/import → ModelSpec compile/publish/materialize → collect v2 → drift classify → compatible continue / breaking block → remap/reconfirm → recover。

## Task

| Task | 状态 |
|---|---|
| T01-覆盖采集到发布的纵向集成测试 | DRAFT |
| T02-部署回滚与真实浏览器验收 | BLOCKED_INPUT |

## DoD

- [ ] 一次集中后端/前端验证通过，失败只针对性重跑
- [ ] GitNexus detect changes 仅命中预期 catalog/modeling flows
- [ ] 无迁移，或 migration dry-run/rollback/backup 全部通过
- [ ] 真实登录、菜单点击、Chrome 95 与 API 证据齐全
- [ ] 健康、日志、告警、故障处置和回滚锚点写入 runbook
