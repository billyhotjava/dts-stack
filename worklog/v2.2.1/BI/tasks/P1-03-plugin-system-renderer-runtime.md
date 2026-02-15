# P1-03 插件化渲染体系与运行时解耦

`status`: `in-progress`  
`priority`: `P1`  
`inspiration`: `Superset(可插拔 Viz 插件) + Metabase(稳定核心)`

## 目标

新组件接入不改核心编辑器与运行时主流程，降低后续开发成本。

## 子任务

1. 插件协议
- `RendererPlugin`、`PropertySchema`、`DataContract` 定义。

2. 注册机制
- 通过 registry 注册组件与数据映射器。

3. 沙箱策略
- 插件渲染失败不拖垮整屏，显示组件级降级卡片。

4. 开发模板
- 提供插件脚手架与最小示例（line/table/kpi）。

## 验收标准

- 新增一个插件组件时，不修改核心 `ScreenDesignerPage` 即可渲染。
- 插件异常时，页面其余组件正常。

## 风险与回滚

- 风险：插件边界不清导致核心依赖反向耦合。  
- 回滚：强制插件 API 白名单，CI 校验禁止跨层引用。

## 实现记录（2026-02-14）

- 插件协议落地：
  - 新增 `RendererPlugin` / `PropertySchema` / `DataContract` 类型定义。
  - 路径：`source/dts-analytics-webapp/modern/src/pages/screens/plugins/types.ts`
- 注册机制落地：
  - 新增插件注册中心（注册/查询/运行时ID）。
  - 路径：`source/dts-analytics-webapp/modern/src/pages/screens/plugins/registry.ts`
- 运行时解耦：
  - `ComponentRenderer` 支持读取组件 `__plugin` 元数据并按 registry 渲染，不改 `ScreenDesignerPage` 主流程。
  - 未加载插件时自动降级到基础组件渲染并提示“插件未加载，已降级”。
- 沙箱策略：
  - 新增 `PluginRenderBoundary`，插件渲染异常仅影响当前组件，页面其余组件不受影响。
  - 路径：`source/dts-analytics-webapp/modern/src/pages/screens/plugins/PluginRenderBoundary.tsx`
- 开发模板与示例：
  - 新增插件目录 README 与示例适配器（KPI/趋势/表格）。
  - 路径：`source/dts-analytics-webapp/modern/src/pages/screens/plugins/README.md`
  - 路径：`source/dts-analytics-webapp/modern/src/pages/screens/plugins/builtinPluginAdapters.tsx`
- 清单协议增强：
  - `screen-plugins` 清单增加 `propertySchema/dataContract`；
  - Demo 插件新增 `table-matrix` 组件。
  - 路径：`source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ScreenPluginResource.java`
