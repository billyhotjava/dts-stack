# T01: governanceMode 前端贯通与开关控件

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标
模型创建/编辑暴露 `governanceMode`（CONTROLLED|PERMISSIVE）开关，列表标识模式，API DTO/类型透传。后端 Sprint-41 F1 已支持（新建默认 CONTROLLED）。

## 技术设计
- **API/类型**（`src/api/semanticModelingApi.ts`）：Model 的 DTO/类型加 `governanceMode?: "CONTROLLED" | "PERMISSIVE"`；创建/更新请求体透传。
- **编辑表单**（`src/pages/modeling/components/ModelEditDrawer.tsx`，antd `Form`）：加字段——`Form.Item name="governanceMode"` + `Select`/`Segmented`（CONTROLLED|PERMISSIVE），新建默认 CONTROLLED，带 help 文案说明含义。
- **列表**（`src/pages/modeling/SemanticModelsPage.tsx`）：列/Tag 显示模式（受控=主色 Tag，permissive=灰）。
- 向后兼容：存量模型读到 PERMISSIVE 正常显示；字段缺省不破现有创建。

## 影响范围
- `dts-platform-webapp`：`semanticModelingApi.ts`（+ 类型）、`ModelEditDrawer.tsx`、`SemanticModelsPage.tsx`。

## 验证
- [ ] 新建模型表单默认 CONTROLLED，可切 PERMISSIVE，保存后端持久（已支持）。
- [ ] 列表显示每个模型的治理模式。
- [ ] 编辑存量 PERMISSIVE 模型不被误翻转（后端 updateModel 不动 governance_mode，前端不强发）。

## 完成标准
- [ ] DTO/类型透传、表单控件、列表标识三处贯通；构建/类型检查通过。
