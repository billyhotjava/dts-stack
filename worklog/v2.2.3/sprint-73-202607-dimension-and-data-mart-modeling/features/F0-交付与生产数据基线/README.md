# F0: 交付与生产数据基线

**优先级**: P0  
**状态**: READY

## 目标

在任何运行时代码开工前，证明真实认证、API、PostgreSQL、Chrome95 和生产数据画像可用于验收与迁移决策。

## 契约定义

| 类型 | 契约 | 关键字段/签名 |
|------|------|---------------|
| 基线 | `it/baseline.md` | P1～P8 每项包含命令、真实结果和阻断 Task |
| 数据画像 | `assets/domain-profile.md` | 数量、空值、重复、引用分布、增长和脏数据 |
| 迁移预检 | `assets/migration-dry-run.md` | `runId/sourceChecksum/counts/unresolved[]/warnings[]` |

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 恢复真实验收链与索引基线 | P0 | READY | - |
| T02 | 补齐生产数据画像与迁移 dry-run | P0 | READY | T01 的数据库访问基线 |

## Definition of Ready

- [x] 探针范围、证据格式和阻断规则已钉死
- [x] 不修改业务数据，只执行只读画像/dry-run
- [x] 受影响 Feature 已保持 DRAFT

## 完成标准

- [ ] `it/baseline.md` 从 GAP 更新为 PASS 或明确 BLOCKED
- [ ] 生产画像和迁移 dry-run 均有真实输出
