# T03: metrics / models CRUD + 列表

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
原生实现指标（含 formula）、模型（含 bindings）的列表/创建/编辑，消费 `/api/semantic`。

## 技术设计
- metrics：`listSemanticMetrics`/`create`/`update`（formulaType + formulaJson + format/unit）。formula 编辑先用结构化表单/JSON（受控构造器属 F2-T02）。
- models：`listSemanticModels`/`createSemanticModel`/`updateSemanticModel` + `getSemanticModelBindings`/`putBindings`（绑维度/指标）。
- 模型字段含 type(DWS/ADS)/grain/status/reviewStatus + **governanceMode**（F1 加控件，本任务先透传字段/列）。
- antd 表格 + 表单；列表显示状态/评审态。

## 影响范围
- `dts-platform-webapp`：metrics/models section 组件 + `semanticModelingApi`。

## 验证
- [ ] metrics(+formula)/models(+bindings) CRUD 贯通 /api/semantic。
- [ ] 模型列表显示 type/status/reviewStatus；构建通过。

## 完成标准
- [ ] metrics/models CRUD + bindings 可用、贯通后端、构建绿。
