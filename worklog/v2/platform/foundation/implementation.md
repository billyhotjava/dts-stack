# 平台-基础数据维护 (Foundation) 功能落地实现说明

## 定位
负责数据源接入、入湖配置、变更审计与调度配置，是 ELT 的入口。

## 菜单与页面
- 数据源列表：`foundation/data-sources`
- 数据源详情：`foundation/data-sources/:id`
- 变更记录：`foundation/access-changes`
- 任务调度：`foundation/task-scheduling`

## 主要功能
1) 数据源管理
- 选择类型(MySQL/PG/Oracle...)
- 参数配置 + 连通性测试(Addax check)
- 密码托管（三员管理端/密钥管理）

2) 入湖任务
- 选择数据源与目标 ODS schema
- 选择表/流并生成目标表名
- 同步策略：全量/增量/CDC
- Schema Drift 策略：自动迁移/阻断/待办提醒

3) 变更记录
- 记录入湖任务/数据源变更历史
- 供审批与追溯使用

4) 调度配置
- 入湖任务周期、窗口、失败重试策略

## 核心对象与数据表
- datasource / datasource_credential
- ingestion_task / ingestion_stream
- ods_table_mapping
- schema_drift_event

## 数据流与依赖
- Addax：生成 Reader/Writer/Job 配置
- dts-ingestion：统一封装 Addax 作业生成与触发
- 平台数据库：入湖任务元数据与映射

## 实现要点
- ODS/RAW 表必须保留，用于追溯与模型引用。
- 需要落库 `source/stream -> ods_table` 映射，供 dbt sources 生成。
- Drift 事件落库并触发工作台待办。

## 边界与异常
- Addax 不可用时，禁止创建/修改入湖任务。
- 密码不可明文展示，前端仅显示脱敏。
