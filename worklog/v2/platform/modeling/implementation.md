# 平台-模型与标准 (Modeling) 功能落地实现说明

## 定位
管理模型设计、SQL 逻辑与建模模板，是 dbt 核心入口。

## 菜单与页面
- SQL 建模：`modeling/sql-modeling`
- 模型模板：`modeling/model-templates`
- 数据标准(同步展示/引用)：`modeling/metadata-standards`

## 主要功能
1) SQL 建模 (dbt)
- 项目选择、模型编辑、ref/source 语法提示。
- 试运行/编译：dbt compile/preview。
- 物化策略：table/view/incremental。

2) 模型模板
- 模板库（维表/事实表/汇总表）
- 支持模板复制与快速生成。

3) 数据标准引用
- 关联字段标准/枚举、便于一致性落地。

## 核心对象与数据表
- dbt_project / dbt_model / dbt_template
- modeling_standard_mapping

## 数据流与依赖
- dbt 项目：/opt/dts/dbt (容器挂载)
- dbt artifacts：manifest.json、run_results.json
- Airflow：模型运行调度与日志入口

## 实现要点
- 通过平台服务封装 dbt CLI 调用，避免前端直连容器。
- 模型保存需同步更新 dbt 项目目录与平台元数据。
- 编译/预览结果需要落库或短期缓存，供 UI 展示。

## 边界与异常
- dbt 项目不可用时，模型入口需降级为只读。
- 预览/编译失败需要回显 dbt 日志。
