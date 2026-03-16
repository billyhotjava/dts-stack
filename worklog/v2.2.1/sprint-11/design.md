# 专题模板与动态数据绑定中心设计

日期：2026-03-16  
Sprint：`worklog/v2.2.1/sprint-11`

## 1. 设计目标

把当前“项目管理专题专用绑定”升级成一套平台化能力，让 DTS 能用同一条机制承载多个动态专题：

- 专题是动态的
- 场景是动态的
- 现场 ODS 物理表名是动态的
- dbt 模型只能依赖稳定逻辑实体，不能依赖现场物理表名

第一阶段采用**环境级全局绑定**。原因很直接：当前专题消费和客户现场部署都是环境级，不按项目空间分叉更简单，也更符合现有 `analytics` 专题入口的运行方式。

## 2. 当前问题

### 2.1 专题特例化

当前项目管理专题已经引入了：

- `project_management_ods_schema`
- `project_management_ods_table`

这能临时跑通一个专题，但不适合作为平台长期方案。后续如果继续这样扩展，就会变成：

- `plm_*`
- `qms_*`
- `hr_*`

最后形成一堆彼此独立、不可治理的专题专用 vars。

### 2.2 现场 ODS 名不稳定

客户导入 Excel/CSV 后，ODS 表名可能是任意填写的。  
因此 dbt 模型里绝不能再写：

- `ods_prj_project`
- `col_20260312`
- 任何现场特定物理表名

### 2.3 缺少正式绑定关系

现在系统缺少一层正式语义：

`专题模板 -> 逻辑实体 -> 真实 ODS 表/视图`

没有这层绑定关系，平台只能看到“很多 ODS 表”，却不知道哪张表应该喂给哪个专题。

## 3. 选定方案

采用“专题模板 + 数据绑定中心 + 运行时 source/vars 编译”。

### 3.1 不选专题专用 vars 继续堆叠

优点是快。  
缺点是平台最终会被专题专用变量污染，不利于后续治理、验证和页面提示。

### 3.2 不选过重的语义引擎

完整语义编排引擎长期是对的，但当前阶段过重，会拖慢专题落地。

### 3.3 选定结构

平台维护三类核心对象：

1. `topic_template`
2. `topic_template_entity`
3. `topic_binding`

运行前由平台把当前绑定关系编译为 dbt 运行所需的 `sources/vars`，供 dbt 与专题消费层统一使用。

## 4. 数据模型

### 4.1 `topic_template`

定义一个专题模板，例如：

- `project-management`
- `plm-overview`

建议字段：

- `id`
- `template_code`
- `template_name`
- `description`
- `status`
- `binding_scope`
- `enabled`
- `created_by`
- `created_date`
- `last_modified_by`
- `last_modified_date`

其中：

- `binding_scope` 第一阶段固定为 `GLOBAL`

### 4.2 `topic_template_entity`

定义某个专题模板需要哪些稳定逻辑实体。

例如：

- `project-management.project_subject_domain`
- `plm-overview.plm_bom_domain`
- `plm-overview.plm_change_domain`

建议字段：

- `id`
- `template_id`
- `entity_code`
- `entity_name`
- `entity_type`
- `required`
- `source_name`
- `table_name`
- `expected_schema`
- `description`

这里的 `source_name/table_name` 是逻辑名，不是现场物理表名。

### 4.3 `topic_binding`

定义某个逻辑实体在当前环境里实际绑定到了哪张 ODS 表或视图。

建议字段：

- `id`
- `template_id`
- `entity_id`
- `binding_mode`
- `data_source_id`
- `database_name`
- `schema_name`
- `table_name`
- `view_name`
- `ods_mapping_id`
- `batch_id`
- `status`
- `bound_by`
- `bound_at`
- `last_verified_at`
- `notes`

第一阶段：

- `binding_mode` 主要支持 `ODS_TABLE`

## 5. 运行时链路

### 5.1 Excel/CSV 入湖

客户上传 Excel/CSV 后，DTS 先走标准入湖：

`上传 -> 解析 -> ODS`

这里不直接把专题概念硬写进 dbt 模型。

### 5.2 绑定动作

入湖成功后，信息科或平台自动流程执行一次绑定：

`选择专题模板 -> 选择逻辑实体 -> 绑定到真实 ODS 表`

例如：

- `project-management.project_subject_domain -> ods.pm_upload_20260316`
- `plm-overview.plm_bom_domain -> ods.plm_bom_20260316`

### 5.3 dbt 编译前生成 source/vars

dbt 执行前，平台根据绑定关系生成：

- 专题对应的 `sources.yml`
- 必要的运行时 `vars`

模型内只写：

- `source('pm_ods', 'project_subject_domain')`
- `source('plm_ods', 'plm_bom_domain')`

而不写现场物理表名。

### 5.4 发布门禁

发布前要新增一层专题绑定校验：

- 当前模型依赖的逻辑实体是否都有绑定
- 绑定是否可验证
- 绑定目标表/视图是否仍存在

缺失绑定时，门禁应明确报：

- 哪个专题
- 哪个逻辑实体
- 当前缺什么

## 6. 页面设计

### 6.1 专题绑定中心

新增平台页，作为统一的专题数据绑定入口。

页面至少展示：

- 专题模板列表
- 模板需要的逻辑实体
- 当前绑定状态
- 已绑定的真实 ODS 表/视图
- 最近验证时间
- 最近来源批次

### 6.2 入湖成功后的绑定动作

在 Excel/ODS 成功页增加：

- `绑定到专题`

用户可以直接把这次 ODS 结果挂到某个专题逻辑实体上。

### 6.3 逻辑建模/发布页

在逻辑建模和提交上线前补充绑定诊断：

- 当前模型命中的专题逻辑实体
- 绑定是否完整
- 缺少哪些实体
- 当前将读取哪张真实 ODS 表

## 7. 项目管理专题如何迁移

项目管理专题不再依赖专题专用物理表名。

迁移后：

- 模型继续使用稳定逻辑 source
- 项目管理专题绑定到实际 ODS 表
- `analytics` 项目看板读取数仓结果
- 口径支撑显示当前绑定来源和最近绑定批次

## 8. `PLM` 如何复用

`PLM` 不需要新造一套专用机制。  
只需新增：

- `plm-overview` 模板
- 该模板的逻辑实体
- 对应的绑定关系
- 相关 dbt 模型

机制保持不变。

## 9. 边界

本轮不做：

- 项目空间级/工作区级隔离绑定
- 绑定历史版本管理
- 绑定审批流
- 图形化模板设计器
- 主数据系统正式接入

但要保留扩展位，避免下一轮推倒重来。

## 10. 结论

项目管理专题不应该继续作为特例维护。  
本次正确方向是把它提升成“专题模板体系”的第一个实例，用统一的绑定中心承载后续项目管理、`PLM`、`QMS` 等动态专题。
