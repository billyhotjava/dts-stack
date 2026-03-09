# FE-009

## 标题

删除 `analytics modern` 的 demo 适配器和占位运行逻辑。

## 范围

- `source/dts-analytics-webapp/modern/src/i18n.ts`
- `source/dts-analytics-webapp/modern/src/pages/screens/plugins/builtinPluginAdapters.tsx`
- `source/dts-analytics-webapp/modern/src/pages/screens/components/ScreenHealthPanel.tsx`
- `source/dts-analytics-webapp/modern/src/pages/screens/components/ComponentRenderer.tsx`

## 目标

- analytics 生产路径不再依赖 demo plugin 和假面板
- 需要 fallback 的地方用真实空态或不可用态，不再用假数据顶上

## 交付

- 删除 demo 适配器和无效 placeholder
- 清理与之关联的 runtime 导入

## 验收

- `pnpm -C source/dts-analytics-webapp/modern build`
- 屏幕插件运行链不再引用 demo-only 模块

## 风险

- 某些占位逻辑已经混入真实运行链，删除前要先理清调用路径
