# F3: ELT 分层准入闸（EltLayerGate + 校验集成）

**优先级**: P0
**状态**: DONE
**依赖**: F1

## 目标
把 dts-metrics 的 ELT 分层准入移植为平台 `EltLayerGate`：DWS/ADS=建模入口、DWD=受控（需 grain+标准码）、ODS/STG=禁；受控模式下在建模/评审校验强制，违规 → 400，诊断码与 dts-metrics 对齐。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | EltLayerGate 组件（层级规则 + 诊断码）+ 单测 | P0 | DONE | F1-T01 |
| T02 | 集成进 model/business-object 校验 + validateModelForReview（受控模式生效） | P0 | DONE | T01, F1-T02 |
| T03 | 错误码映射（unsafe_expression→422；分层码→400） | P0 | DONE | T02 |

## 完成标准
- [ ] DWS/ADS 资产可作建模入口；ODS/STG 被拒（invalid_layer）。
- [ ] DWD 节点须声明 grain/主键（否则 grain_mismatch）且绑定标准码（否则 standard_code_required）。
- [ ] 仅受控模式强制；PERMISSIVE 模型不受影响。
- [ ] 诊断码与 dts-metrics 一致，便于 SP-3 前端复用。
