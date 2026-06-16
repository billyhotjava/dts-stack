# F4: 代码归档与整合收口

**优先级**: P1
**状态**: READY
**依赖**: F2/F3 完成

## 目标
归档 dts-metrics 源码（保留 git 历史）、处置 sprint-35b 分支、更新文档与记忆、闭环整合大计划（SP-1~SP-4）。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 源码归档/移出构建（source/dts-metrics + dts-metrics-webapp） | P1 | READY | F2,F3 |
| T02 | 文档与分支处置（opmanager 文档 + sprint-35b 分支 + 整合路线图闭环） | P1 | READY | T01 |
| T03 | 全量回归与大计划闭环（记忆更新 + sprint-queue 收口） | P1 | READY | T01,T02 |

## 完成标准
- [ ] `source/dts-metrics` + `dts-metrics-webapp` 移出构建（根 pom/workspace 不再含），保留 git 历史 + ARCHIVED 说明；不删历史。
- [ ] opmanager 文档移除 dts-metrics；sprint-35b 分支加归档说明（硬化工作作废原因）。
- [ ] 整合路线图 SP-1~SP-4 全闭环；记忆 [[dts-metrics-elt-ecosystem]] 更新为"已退役"；sprint-queue 收口。
- [ ] 全量回归：语义建模唯一入口=平台原生页；全栈 grep `dts-metrics` 仅余归档/历史说明。
