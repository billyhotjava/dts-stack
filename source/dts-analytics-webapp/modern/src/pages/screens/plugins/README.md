# 屏幕插件开发最小模板

本目录提供 `P1-03` 所需的最小插件运行时：

- `types.ts`：`RendererPlugin` / `PropertySchema` / `DataContract` 协议。
- `registry.ts`：插件注册中心。
- `PluginRenderBoundary.tsx`：插件异常隔离，组件级降级卡片。
- `builtinPluginAdapters.tsx`：示例适配器（KPI/趋势/表格）。
- `useScreenPluginRuntime.ts`：运行时加载与安装入口。

## 插件标识规范

- 运行时 ID：`{pluginId}:{componentId}@{version}`
- 组件配置中的插件元数据：

```json
{
  "__plugin": {
    "pluginId": "demo-stat-pack",
    "componentId": "kpi-card-pro",
    "version": "1.0.0"
  }
}
```

## 新增插件步骤（不改 `ScreenDesignerPage`）

1. 后端 `screen-plugins` 清单新增组件。
2. 为该组件提供适配器（或加载远程渲染器）并注册到 `registry`。
3. 组件拖入画布时写入 `__plugin` 元数据（由组件库映射负责）。
4. `ComponentRenderer` 自动按 `__plugin` 元数据匹配并渲染。

