# 平台-工作台 (Workbench) 功能落地实现说明

## 定位
工作台是平台首页与个人工作入口，承载“概览 + 待办 + 快捷入口”三类能力，避免直接操作底层组件。

## 菜单与页面
- 工作台首页：`workbench/index`
- 流程中心：`workbench/workflow-center`

## 主要功能
1) 我的概览
- 指标：我创建的资产数、所辖任务成功率、本日新增数据量。
- 数据来源：OpenMetadata(资产统计)、Airflow(任务运行)、平台数据库(用户/权限/收藏)。

2) 待办事项
- 类型：权限审批、Schema Drift 审核、质量核查失败工单。
- 触发源：
  - 入湖任务 schema 漂移策略=人工审核。
  - 数据质量规则失败。
  - 数据集访问申请。

3) 我的收藏
- 收藏对象：逻辑模型、入湖任务、常用资产。
- 支持快速跳转到对应详情页或运行历史。

## 核心对象与数据表
- portal_user_favorites
- access_request / approval_task
- ingestion_task / ingestion_run
- openmetadata_asset_snapshot

## 数据流与依赖
- 资产统计：OpenMetadata API/同步表 -> 平台聚合接口 -> 工作台。
- 任务统计：Airflow API -> 平台聚合接口 -> 工作台。
- 待办聚合：平台审批/质量/漂移任务表 -> 工作台。

## 实现要点
- 以平台 API 聚合后返回，前端避免多源直连。
- 指标类数据可缓存(分钟级)以减轻 OpenMetadata/Airflow 压力。
- 待办列表需支持“角色/组织/数据范围”过滤。

## 边界与异常
- OpenMetadata/Airflow 不可用时，工作台仍可展示本地统计与待办。
- 若用户无权限，隐藏模块入口并避免接口调用。
