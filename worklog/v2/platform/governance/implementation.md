# 平台-治理与质量 (Governance) 功能落地实现说明

## 定位
统一治理标准、质量规则、主题域与指标管理，形成质量闭环。

## 菜单与页面
- 术语词汇表：`governance/glossary`
- 元素管理：`governance/elements`
- 参考码表：`governance/reference-codes`
- 主题域：`governance/subject-areas`
- 模板管理：`governance/templates`
- 质量规则：`governance/quality-rules`
- 指标中心：`governance/indicators`

## 主要功能
1) 术语与标准
- 术语定义、同义词、关联数据资产。
- 标准字段与码表，映射到模型字段。

2) 质量规则
- 基于 dbt tests 的规则定义(非空、唯一、枚举值等)。
- 规则与模型/表的绑定、执行计划。

3) 指标中心
- 原子指标定义，生成指标 SQL 模板。

## 核心对象与数据表
- glossary_term / reference_code / subject_area
- quality_rule / quality_rule_binding / quality_run
- indicator_definition

## 数据流与依赖
- dbt tests：规则执行与结果回写。
- OpenMetadata：术语/标签同步。
- Airflow：质量执行调度。

## 实现要点
- 质量规则与模型绑定需可追溯(模型版本/字段变更)。
- 规则执行结果需进入运维中心与资产详情。
- 术语与标签双向同步：平台 <-> OpenMetadata。

## 边界与异常
- dbt 不可用时，规则只能创建，无法执行。
- OpenMetadata 不可用时，术语同步降级为本地存储。
