# Sprint-43 集成测试计划

## 目标
证明语义富化贯通达成：dbt 列 meta → catalog → 列族透出 → 消费端接通，且不破坏现有 catalog/建模行为（绞杀者：无 meta 回退）。

## 证据
| 证据 | 测试类/路径 | 状态 |
|------|-------------|------|
| 列族派生（dimension/metric/time 分组 + grain + standardCodes） | F3-T01 派生器单测 | READY |
| 列同步捕获 meta（含无 meta 回退） | F2-T02 单测 | READY |
| schema-contract/assets-v2 透出列族非空 | 集成测试 | READY |
| enforceLayerGate 用真实源表层级阻断 ODS/STG 源 | F4-T01 单测 | READY |
| visual-assets 列族非空（空壳根因关闭） | F4-T02 单测 | READY |

## 验收命令
```bash
cd source
./mvnw -pl dts-platform clean test -Dtest='CatalogAsset*Test,SemanticModelingServiceTest,*ColumnSync*Test'
./mvnw -pl dts-metrics clean test -Dtest=MetricVisualAssetResourceTest
```
> 铁律：改完必须 `clean` 再信测试结果。record 加字段必同步全部构造点（增量假绿陷阱）。

## 阻断条件（任一触发即不可 DONE）
- 列 meta 未贯通到列契约 / schema-contract（贯通断裂）。
- 列族派生错分（dimension/metric/time 混淆）或 grain/standardCodes 丢失。
- 无 meta 的存量资产行为被改变（catalog 回退失效）。
- enforceLayerGate 仍用占位（源表 gating 未真接入）。
- visual-assets 列族仍空（空壳根因未关闭）。

## 前置
- **F2-T00 spike 必须先有结论**（OM 是否抓 dbt meta），否则 F2-T02 路线未定，sprint 不应推进实现。
