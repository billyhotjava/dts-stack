# T01: enforceLayerGate 接入真实源表层级 + 标准码

**优先级**: P0
**状态**: READY
**依赖**: F3-T01

## 目标
把 Sprint-41 F3 `enforceLayerGate` 当前的占位输入（标准码置 true、源表层级用 model.type 单点）换成真实源表层级 + 标准码，解锁源表"禁 ODS/STG 建模" + DWD 标准码强制。

## 技术设计
- `SemanticModelingService.enforceLayerGate`：除模型输出层外，对模型涉及的源表（business object main_table + table-mappings）逐表查 catalog 的 `warehouseLayer`，构建多个 `EltLayerGate.LayerNode`。
- `hasStandardCode` 取自列 meta（F3 透出的 standardCodes）；`hasGrain` 取 grain。
- `EltLayerGate` 组件已支持完整规则（Sprint-41 F3-T01），本任务只补真实输入。

## 影响范围
- `dts-platform`：`SemanticModelingService.enforceLayerGate` + 源表层级解析（catalog 查询）。

## 验证
- [ ] 受控模型从 ODS/STG 源表建模 → invalid_layer 阻断。
- [ ] DWD 源缺标准码 → standard_code_required 阻断。
- [ ] DWS/ADS 源 + 合规 → 通过；PERMISSIVE 不受影响。

## 完成标准
- [ ] 真实源表 gating 生效、Sprint-41 F3-T02"依赖 SP-2"项关闭、集成测试覆盖、`clean test` 通过。
