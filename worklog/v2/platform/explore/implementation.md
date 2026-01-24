# 平台-数据开发 (Explore) 功能落地实现说明

## 定位
提供面向开发者的数据查询、ETL 转换与作业编排入口。

## 菜单与页面
- 查询工作台：`explore/query-workbench`
- 转换任务：`explore/etl/transform`
- 编排中心：`explore/etl/orchestration`
- 脚本工作室：`explore/etl/script-studio`

## 主要功能
1) 查询工作台
- 交互式 SQL 查询与结果预览。
- 权限校验与审计。

2) 转换任务
- 以 dbt 模型为核心的转换任务管理。
- 任务参数、物化策略、依赖关系。

3) 编排中心
- 可视化任务流编排，生成 Airflow DAG。
- 节点类型：入湖任务、dbt 模型、质量校验。

4) 脚本工作室
- SQL/脚本模板管理，支持复用与版本。

## 核心对象与数据表
- adhoc_query / query_history
- etl_job / etl_node / etl_dependency
- airflow_dag_snapshot

## 数据流与依赖
- Airflow：DAG 生成、运行调度、日志聚合。
- dbt：模型编译/运行。
- dts-ingestion：入湖任务触发与状态回写。

## 实现要点
- 编排保存时必须解析依赖关系并生成 DAG。
- SQL 查询需走统一网关/服务端代理，避免直连数据源。
- 运行态需要与运维中心共享同一“任务实例”模型。

## 边界与异常
- 权限不足时禁止查询/编排发布。
- Airflow 不可用时仅支持保存，不允许运行。
