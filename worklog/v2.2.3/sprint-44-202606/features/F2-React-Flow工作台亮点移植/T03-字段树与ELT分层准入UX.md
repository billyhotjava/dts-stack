# T03: 字段树拖拽 + ELT 分层准入 UX

**优先级**: P1
**状态**: READY
**依赖**: T01；SP-2（Sprint-43）列族字段角色

## 目标
治理画布配字段树（按列语义角色分组，拖拽入画布）+ ELT 分层准入的可视化提示（DWS/ADS 入口、DWD drilldown 受控、ODS/STG 禁），把 dts-metrics 的 `SemanticFieldExplorer` + 分层 UX 亮点带到治理路径。

## 技术设计
- **字段树**：参照 dts-metrics `SemanticFieldExplorer`（subject→model→group→field，关键字过滤，HTML5 拖拽）。字段角色分组（dimension/metric/time）**依赖 SP-2 列族**透出（Sprint-43 F3/F4）——SP-2 未就绪前可降级为不分组列表。
- **分层准入 UX**：资产/节点按 `warehouseLayer` 着色 + 准入态（DWS/ADS 可入、DWD 需 grain+标准码、ODS/STG 禁）；与 F1-T03 后端分层诊断联动（前端预提示 + 后端兜底）。
- 复用 T01 选定的画布组件；拖拽落点生成 graph 节点/绑定。

## 影响范围
- `dts-platform-webapp`：字段树组件（复用/移植）+ 画布分层着色 + 类型。

## 验证
- [ ] 字段树按角色分组（SP-2 就绪后）/降级列表（未就绪）。
- [ ] 画布对 ODS/STG 源给可视化禁用/警告；DWD 提示需 grain+标准码。

## 完成标准
- [ ] 字段树 + 分层 UX 落地、与 SP-2/F1-T03 联动点清晰、构建通过。

## 备注
- SP-2 列族是本任务字段角色分组的硬依赖；排期上 T03 宜在 Sprint-43 SP-2 F3/F4 之后或并行末段。
