# Sprint-41 集成测试计划

## 目标
证明受控建模逻辑移植达成：受控模式严格生效、与 dts-metrics 语义一致，且不破坏平台现有 permissive 路径（绞杀者并存）。

## 证据目录
| 证据 | 路径 | 状态 |
|------|------|------|
| 受控 DSL 正确性（每函数 × postgres/doris） | `it/evidence/controlled-dsl/` | READY |
| ELT 分层准入（DWS/ADS 过 / DWD 闸 / ODS/STG 禁） | `it/evidence/elt-layer-gate/` | READY |
| 绞杀者并存（PERMISSIVE 字节不变） | `it/evidence/strangler-parity/` | READY |
| 与 dts-metrics 黄金 SQL 语义比对 | `it/evidence/golden-sql-parity/` | READY |

## 验收命令
```bash
cd source
./mvnw -q -pl dts-platform clean test -Dtest='ControlledMetricDslCompilerTest,EltLayerGateTest,SemanticModeling*Test'
```
> 铁律：改完必须 `clean` 再信测试结果（增量编译假绿——已多次踩过）。

## 阻断条件（任一触发即不可 DONE）
- 受控模式仍接受 raw/custom SQL 或非白名单函数（受控失效）。
- 受控编译输出与 dts-metrics 黄金 SQL 语义不一致（移植漂移）。
- 标识符未按方言 quote，或注入串未被拒。
- 受控模式下 ODS/STG 可建模、或 DWD 缺 grain/标准码仍放行。
- PERMISSIVE 模型行为被改变（既有测试出现回归）。
- governanceMode 迁移导致存量模型默认值错误（非 PERMISSIVE）。
