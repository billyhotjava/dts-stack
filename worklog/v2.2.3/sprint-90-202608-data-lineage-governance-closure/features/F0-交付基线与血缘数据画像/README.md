# F0: 交付基线与血缘数据画像

**优先级**: P0
**状态**: BLOCKED_INPUT（依赖外部提供环境、账号与样本）

## 目标

在写第一行血缘功能代码前，证明「血缘验收路径真的走得通」，并用真实数据回答 Sprint README 的开放问题 Q1-Q4，使 F4 的性能目标可定量、F3 的运营台形态可确定。

## 契约定义

本 Feature 不产出代码契约，产出**事实**：填满 `assets/domain-profile.md` 的「真实数据画像」表与 `assets/nfr-budget.md` 的「当前」列，并关闭 `it/baseline.md` 的 B1-B5。

## 交付物

| 交付物 | 落点 |
|---|---|
| 可登录的运行实例 + 治理员账号 | `it/baseline.md` B1 |
| 血缘样本链路（外部源→Addax→ODS→dbt→下游，含字段血缘与存疑边） | `it/baseline.md` B2 |
| Chrome 95 验收环境 | `it/baseline.md` B3 |
| 血缘规模统计 SQL 与结果 | `assets/domain-profile.md` |
| 影响分析 depth=3/5 实测耗时 | `assets/nfr-budget.md` |

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 打通血缘验收基线并实测数据画像 | P0 | BLOCKED_INPUT | 外部输入 |

## Definition of Ready

- [x] 目标可验收（B1-B5 关闭 + 两张表填满）
- [ ] 依赖已就绪 —— **未就绪**：账号、实例、样本、Chrome 95 均为外部输入
- [x] 验收可验证（清单式）

## 完成标准

- [ ] `it/baseline.md` 六步验收路径逐条走通并留证据
- [ ] `assets/domain-profile.md` 无"待测"字样
- [ ] `assets/nfr-budget.md` "当前"列填满，Gate G1 非功能预算由 GAP 转 PASS
- [ ] Q1-Q4 四个开放问题在 Sprint README 中标注为已关闭
