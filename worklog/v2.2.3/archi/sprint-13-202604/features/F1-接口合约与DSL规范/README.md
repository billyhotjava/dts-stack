# F1: 接口合约与 DSL 规范

**优先级**: P0
**状态**: READY
**依赖**: —

## 目标

**Phase 0 接口锁定**——把整个自助 BI 系统的所有"契约"写成 spec 并评审通过：dbt `schema.yml` 扩展、REST API、派生指标 DSL、虚拟数据集 JSON schema、Arrow 协议。本 Feature 不写任何实现代码，只产 spec markdown + JSON schema + BNF。

## 为什么必须先做

1. F2-F6 全部依赖这些契约。一旦开工再改，前后端都要推倒重来。
2. 合约稳定比完美更重要——可以不完备但必须**不歧义**。
3. 本 Sprint 的所有 Task 验收标准都引用这些 spec，spec 不定则无验收。

## 评审机制

- F1 全部 Task 完成后必须进行一次**跨职能评审**：数据工程 + 后端 + 前端 + 产品。
- 评审记录存 `worklog/v2.2.3/sprint-13-202604/assets/f1-review-minutes.md`。
- 评审通过后 spec 冻结；后续变更走 change request（同一个 sprint 内可走 patch，跨 sprint 走新 sprint）。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | dbt `schema.yml meta.dts` 扩展规范 | P0 | READY | — |
| T02 | 语义层 REST API JSON schema | P0 | READY | T01 |
| T03 | 派生指标 DSL BNF + 函数白名单 | P0 | READY | T01 |
| T04 | 虚拟数据集 JSON schema + Arrow 返回约定 | P0 | READY | T01, T02 |

## 完成标准

- [ ] 4 份 spec markdown 全部产出，存于 `worklog/v2.2.3/sprint-13-202604/assets/specs/`
- [ ] 3 张示例 model 的 `schema.yml` 按 T01 规范改写完毕（`it/sample-schema-yml/`）
- [ ] 3 份 REST 调用样例（meta / single-table query / virtual-dataset query）
- [ ] 5 个派生指标样例用 T03 DSL 写出并附中英注解
- [ ] 评审会议记录，含所有 open question 的 resolution
- [ ] spec 冻结声明（`assets/specs/CHANGELOG.md` 首行）
