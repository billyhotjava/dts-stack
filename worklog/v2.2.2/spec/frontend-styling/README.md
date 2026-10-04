# Frontend Styling Spec

本目录固化 `v2.2.2` 阶段前端样式体系的边界约定，重点解决 `dts-platform-webapp` 中 `Tailwind + AntD` 混用的职责划分，以及 `dts-analytics-webapp/modern` 是否引入 Tailwind 的取舍。

文档分工：

- `01-ant-design-tailwind-boundary.md`
  说明两个项目的推荐样式策略、组件与布局分工、禁止混用场景，以及建议的落地约束。

阅读顺序建议：

1. 先读 `01-ant-design-tailwind-boundary.md`
2. 重构 `platform-webapp` 或 `analytics-webapp/modern` 前先对照本规范收敛方案
