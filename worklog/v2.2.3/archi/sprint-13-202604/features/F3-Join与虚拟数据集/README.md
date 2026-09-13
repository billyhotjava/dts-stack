# F3: Join 与虚拟数据集

**优先级**: P0
**状态**: READY
**依赖**: F1, F2

## 目标

在 F2 单表查询的基础上，增加**多表 join**能力，并引入**虚拟数据集**作为用户画布结果的持久化形态。关键挑战是 **1:N fanout 的对称聚合（Symmetric Aggregate）**——让用户拖出来的 join 永远算出正确的数。

## 核心难点

1. **只能走白名单 join**：前端画布只暴露 `gov_join_edge` 里声明过的 edge，编译器校验每个 join 必须在图里。
2. **Fanout 正确处理**：1:N join 不做特殊处理会让 measure 被放大（一个 order 有 3 个 payment → order 被计算 3 次）。编译器必须识别并自动改写。
3. **Join 路径解析**：用户只指定 "join to X"，编译器要找出路径（可能多跳）。本 Sprint 只支持**一跳 join**，多跳走 F3-T02 的 Path Resolver 但实现简化为一跳。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | JoinGraphRegistry | P0 | READY | F2/T01 |
| T02 | MultiModelCompiler — 多表 join 编译 | P0 | READY | T01, F2/T03 |
| T03 | FanoutDetector + SymmetricAggregate | P0 | READY | T02 |
| T04 | VirtualDataset CRUD + 持久化 | P0 | READY | F1/T04 |

## 完成标准

- [ ] JoinGraphRegistry 能从 `gov_join_edge` 表加载关系图，回答"A 能否 join 到 B"
- [ ] 两表 join 查询端到端跑通（`ads_sales_daily` × `dim_customer`）
- [ ] 1:N join 场景 FanoutDetector 自动改写为 CTE 预聚合，结果与人工写的参考 SQL 一致
- [ ] VirtualDataset CRUD 所有端点实现，遵循 F1-T04 JSON schema
- [ ] 从画布保存 → 读取 → 编译 → 查询全链路通
- [ ] 覆盖率 ≥ 80%
