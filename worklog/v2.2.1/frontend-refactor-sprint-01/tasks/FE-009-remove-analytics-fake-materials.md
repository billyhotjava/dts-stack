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
- `builtinPluginAdapters` 不再内置 `demo-stat-pack` 适配器，仅保留真实 custom plugin 注册路径
- `ScreenHealthPanel` 移除 mock 组件 benchmark，改成真实浏览器帧稳定性测量
- `i18n` 去掉 `dummy / placeholder` 指标文案，改为真实客户线状态描述
- `ComponentRenderer` 未注册插件/未知组件改成明确不可用态，不再使用模糊占位文案

## 验收

- `pnpm -C source/dts-analytics-webapp/modern build`
- 屏幕插件运行链不再引用 demo-only 模块

## 完成记录

- 2026-03-09：已完成
- 验证：`pnpm -C source/dts-analytics-webapp/modern build`
- 结果：通过，`demo-stat-pack` builtin runtime 已从生产路径移除

## 风险

- 某些占位逻辑已经混入真实运行链，删除前要先理清调用路径
