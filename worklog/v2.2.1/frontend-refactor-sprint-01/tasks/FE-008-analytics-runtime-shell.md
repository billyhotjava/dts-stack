# FE-008

## 标题

重构 `analytics modern` 运行时壳。

## 范围

- `source/dts-analytics-webapp/modern/src/pages/screens/PublicScreenPage.tsx`
- `source/dts-analytics-webapp/modern/src/pages/screens/ScreenPreviewPage.tsx`
- `source/dts-analytics-webapp/modern/src/pages/screens/ScreenExportPage.tsx`
- `PreviewScaleControl`
- `DeviceModeSwitcher`
- `GlobalVariablePanel`

## 目标

- 统一公开页、预览页、导出页的控制层
- 统一加载/错误/空态表现
- 保留业务大屏画布自身主题能力

## 交付

- 新 runtime shell 只统一外层和控制层
- 不强行把业务大屏内容涂成后台控制台配色

## 验收

- `pnpm -C source/dts-analytics-webapp/modern build`
- 运行时页的控制条、缩放区、变量区风格统一

## 风险

- 画布主题和外层壳混在一起处理时，容易误伤业务屏表现
