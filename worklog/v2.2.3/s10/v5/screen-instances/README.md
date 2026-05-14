# v5 大屏实例生成说明

本目录用于存放从 v4 大屏迁移出的 v5 实例。迁移原则：以 v4 JSON 为结构基准，保留组件数量、组件类型、坐标尺寸、指标布局和数据源，只替换视觉样式。

## 当前输出

- gpmc-project-overview-v5.json：由 v4/pjm/screen-instances/gpmc-overview-v3.json 生成，结构保持 v4 原样，样式改为 project_dashboard_blue_bg.html 的企业蓝背景 + 白色卡片风格。
- gpmc-execution-board-v5.json：待按同一原则从 v4 执行监控页重构。
- gpmc-quality-board-v5.json：待按同一原则从 v4 质量跟进页重构。
- gpmc-tech-state-board-v5.json：待按同一原则从 v4 技术状态页重构。

## 2026-05-14 overview 样式纠偏

- 已重新生成 gpmc-project-overview-v5.json：以 v4/gpmc-overview-v3.json 为结构基准，仅更改企业蓝背景版视觉样式。
- 校验结果：保持 v4 的 64 个组件、组件类型、坐标尺寸、指标布局和数据源不变。
- 后续批量迁移其他 v4 大屏时也采用同一原则：保留布局和组件，只替换视觉样式。
