# 平台-数据可视化 (Visualization) 功能落地实现说明

## 定位
提供报表与自助分析入口，统一对接分析服务与外部 BI。

## 菜单与页面
- 报表中心：`visualization/reports`
- 报表管理：`visualization/reports-manage`
- 自助分析：`visualization/analytics`

## 主要功能
1) 报表中心
- 报表列表、分类、权限管理
- 链接到外部 BI 或内置报表引擎

2) 报表管理
- 报表元数据、目录管理、发布/下线

3) 自助分析
- 路由到 `dts-analytics` / 分析前端
- 支持嵌入式探索/仪表盘

## 核心对象与数据表
- report / report_category
- analytics_dataset / analytics_view

## 数据流与依赖
- dts-analytics：分析 API 与前端
- OpenMetadata：数据集元数据引用

## 实现要点
- `/analytics` 路由需要透传到分析服务并保持登录态。
- 报表管理权限与角色绑定。

## 边界与异常
- analytics 服务不可用时，展示维护提示。
