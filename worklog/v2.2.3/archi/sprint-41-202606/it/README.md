# Sprint-41 集成测试计划

## 目标
证明受控建模逻辑移植达成：受控模式严格生效、安全/语义与 dts-metrics 纪律一致，且不破坏平台现有 permissive 路径（绞杀者并存）。

> **parity 口径修正**：dts-metrics 吃字符串表达式、平台吃 JSON formula，输出形态不同，故验收为
> **安全/语义一致**（白名单 + 拒 raw + 方言 quote + 注入防御）而非字节复刻 dts-metrics 黄金 SQL。

## 证据（2026-06-16 真实运行）
| 证据 | 测试类 | 例数 | 状态 |
|------|--------|------|------|
| 受控 DSL 正确性（每函数 × postgres/doris + 注入/raw 拒） | `ControlledMetricDslCompilerTest` | 15 | ✅ DONE |
| ELT 分层准入（DWS/ADS 过 / DWD 闸 / ODS/STG 禁） | `EltLayerGateTest` | 7 | ✅ DONE |
| 受控集成 + 绞杀者并存（PERMISSIVE 不破 + governanceMode 默认） | `SemanticModelingServiceTest` | 12 | ✅ DONE |
| 违规码 HTTP 映射（unsafe_expression→422 / 分层码→400） | `SemanticModelingResourceTest` | 2 | ✅ DONE |

**合计 36 例全绿。**

## 验收命令
```bash
cd source
./mvnw -pl dts-platform clean test -Dtest='ControlledMetricDslCompilerTest,EltLayerGateTest,SemanticModelingServiceTest,SemanticModelingResourceTest'
```
> 铁律：改完必须 `clean` 再信测试结果（增量编译假绿——已多次踩过）。

## 阻断条件（均未触发 ✅）
- ✅ 受控模式拒 raw/custom SQL 与非白名单函数。
- ✅ 标识符按方言 quote（postgres `"` / doris `` ` ``）、注入串被拒。
- ✅ 受控模式下 ODS/STG 不可建模、无 grain 的 DWD 被拒。
- ✅ PERMISSIVE 模型行为字节不变（既有测试零回归）。
- ✅ governanceMode 迁移：存量默认 PERMISSIVE、新建默认 CONTROLLED。

## SP-2 followup（非本 sprint 阻断项）
- 源表层级 gating（禁止从 ODS/STG 源表建模）+ DWD 标准码强制：平台模型当前不携带源表层级/标准码，随 SP-2 语义富化接入；`EltLayerGate` 组件已支持完整规则。
- doris 方言端到端：编译器已支持双方言，wiring 当前默认 POSTGRES（dialect 来源待 SP-2 明确）。
