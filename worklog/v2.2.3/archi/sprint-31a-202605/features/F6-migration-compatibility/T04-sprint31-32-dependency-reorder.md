# T04: Sprint-31/32 依赖重排

**优先级**: P0
**状态**: DONE
**依赖**: T01-T03

## 目标

把 Sprint-31 和 Sprint-32 的任务依赖显式改为依赖 Sprint-31A 的资产事实源。

## 技术设计

- 更新 Sprint-31 F3/F4/F5/F6 依赖说明。
- 更新 Sprint-32 F2/F3/F4/F5 对 platform contract 的依赖。
- 保持原 Sprint 目标不扩大。

## 影响范围

- sprint-31 README / feature docs
- sprint-32 README / feature docs
- sprint-queue

## 验证

- [x] Sprint 顺序明确为 31A -> 31 -> 32。
- [x] 无文档仍声称 metrics 可绕过 platform 资产事实源。

## 完成标准

- [x] 团队协作按新顺序执行。

## 证据

- `worklog/v2.2.3/sprint-31a-202605/assets/sprint31-32-dependency-reorder.md`
