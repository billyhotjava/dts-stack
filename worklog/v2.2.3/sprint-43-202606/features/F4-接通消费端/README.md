# F4: 接通消费端（F3 源表 gating + visual-assets 列族）

**优先级**: P0
**状态**: READY
**依赖**: F3（列族透出）

## 目标
用 F3 透出的列族/层级，接通两个下游缺口：① Sprint-41 F3 `enforceLayerGate` 的真实源表层级 + 标准码（解锁源表"禁 ODS/STG 建模" + DWD 标准码强制）；② dts-metrics `MetricVisualAssetResource` 空列族根因。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | enforceLayerGate 接入真实源表层级 + 标准码 | P0 | READY | F3-T01 |
| T02 | dts-metrics visual-assets 列族填充（替换 List.of()） | P0 | READY | F3-T02 |

## 完成标准
- [ ] `SemanticModelingService.enforceLayerGate`：源表层级查 catalog（替 model.type 单点）、hasStandardCode 取列 meta（替当前置 true）；受控模型从 ODS/STG 源表建模 / DWD 无标准码被阻断。
- [ ] `MetricVisualAssetResource.toVisualAsset` 列族从 schema-contract 列 meta 派生（替换 5 处 `List.of()`）+ 补 `standardCodes`；F3 可视化工作台拿到可选字段。
- [ ] Sprint-41 F3-T02 落地说明中"依赖 SP-2"项关闭。
