# T01: EltLayerGate 组件与单测

**优先级**: P0
**状态**: DONE
**依赖**: F1-T01

## 目标
把 dts-metrics 的层级准入规则移植为平台独立、可单测的 `EltLayerGate`。

## 技术设计
新建 `com.yuzhi.dts.platform.service.modeling.EltLayerGate`（@Component，纯函数）。移植自 dts-metrics `MetricGraphDraftService.diagnostics`（层级段）+ `MetricVisualAssetResource.parseLayers`：
- **入口层**：DWS/ADS 允许作建模 base。
- **受控层**：DWD 允许但须声明 `grain` 或 `primaryKeys`（否则 `grain_mismatch`）且绑定标准码 `standardCode(s)`（否则 `standard_code_required`）；DWD 不可直连发布节点。
- **禁用层**：ODS/STG 不可作建模入口（`invalid_layer`）。
- 输入：模型节点的 `warehouseLayer` + grain/主键/标准码；输出：诊断列表（code + message + 定位），ERROR 即拒。
- 诊断码与 dts-metrics 字面一致（`invalid_layer`/`grain_mismatch`/`standard_code_required`）。

`warehouseLayer` 来源：平台 catalog 资产已有该字段（`CatalogAssetExtension`）。

## 影响范围
- `dts-platform`：新增 `EltLayerGate.java` + `EltLayerGateTest`。无既有行为改动（集成在 T02）。

## 验证
- [ ] DWS/ADS → 通过；ODS/STG → invalid_layer。
- [ ] DWD 缺 grain/主键 → grain_mismatch；缺标准码 → standard_code_required；直连发布 → invalid_layer。

## 完成标准
- [ ] 组件 + 单测全绿（`clean test`），诊断码与 dts-metrics 对齐。
