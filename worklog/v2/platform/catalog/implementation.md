# 平台-数据资产 (Catalog) 功能落地实现说明

## 定位
数据资产目录与详情入口，统一承载资产、元数据、血缘、质量的检索与展示。

## 菜单与页面
- 数据集列表：`catalog/datasets`
- 元数据详情：`catalog/metadata`
- 血缘视图：`catalog/lineage`
- 质量概览：`catalog/quality`

## 主要功能
1) 资产目录
- 资产查询：按名称/标签/主题域/负责人过滤。
- 资产权限：与数据访问申请联动。

2) 元数据详情
- 展示字段、注释、类型、分区、采集时间。
- 支持数据标准映射/术语标注。

3) 血缘视图
- 业务表 -> 入湖任务 -> ODS -> dbt 模型 -> 指标/报表。
- 支持节点负责人、任务日志入口。

4) 质量概览
- 展示质量规则、最近执行结果与异常记录。

## 核心对象与数据表
- catalog_dataset / catalog_field / catalog_domain
- dataset_access_request / dataset_access_approval
- openmetadata_entity_cache / lineage_snapshot

## 数据流与依赖
- OpenMetadata：资产、字段、血缘为主数据来源。
- Airflow + dbt：质量与模型关系回写。
- 平台数据库：权限、审批、业务标签与扩展属性。

## 实现要点
- “资产列表”支持“OpenMetadata 缓存 + 平台扩展信息”合并展示。
- 血缘需对接 OpenMetadata ingestion，同时保留平台业务节点映射。
- 质量详情读取 dbt tests 或质量服务结果。

## 边界与异常
- OpenMetadata 不可用时，降级为平台缓存快照。
- 未授权用户仅显示基础元数据，不展示敏感字段。
