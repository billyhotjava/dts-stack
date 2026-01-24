# 平台-运维中心 (Ops) 功能落地实现说明

## 定位
统一监控任务运行、质量告警与补数管理。

## 菜单与页面
- 运行概览：`ops/overview`
- 实例监控：`ops/instances`
- 补数管理：`ops/backfill`
- 告警日志：`ops/alert-log`

## 主要功能
1) 运行概览
- 今日失败数、SLA 超时、成功率趋势。

2) 实例监控
- 所有任务实例列表与详情
- 日志分节点展示（Airbyte/dbt/Airflow）

3) 补数管理
- 按日期范围生成补数实例
- 自动触发 Airflow DAG

4) 告警日志
- 质量/任务/漂移告警统一列表

## 核心对象与数据表
- airflow_run / airflow_task
- ops_alert / ops_backfill

## 数据流与依赖
- Airflow：任务实例与日志
- dts-analytics：运行统计聚合
- 平台数据库：告警与补数记录

## 实现要点
- 统一任务实例模型，屏蔽底层系统细节。
- 日志需黑盒化（不暴露 Airbyte/dbt 具体实现）。

## 边界与异常
- Airflow 不可用时，实例列表仅展示历史缓存。
