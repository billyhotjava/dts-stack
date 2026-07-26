# Sprint-73 运维手册

## 健康检查

- `/management/health` 为 `UP`；
- `databasechangelog` 中三个 `20260727-*` changeset 为 `EXECUTED`；
- DataMart 列表默认窗口 10、最大 100，`limit=101` 返回业务错误；
- `modeling_data_mart`、`modeling_dimension_definition`、`modeling_model_spec` 的 tenant 条件不可省略。

## 常见故障

| code/现象 | 原因 | 恢复动作 |
|-----------|------|----------|
| `DATA_MART_BASELINE_REQUIRES_CURRENT` | 集市未确认，或其分类未在计划中确认 | 先确认业务分类和 DataMart，再保存计划集市范围 |
| `DATA_MART_SCOPE_IN_USE` | 已有计划、维度或模型引用该集市 | 解除活动引用后再调整集市分类 |
| `DIMENSION_DEFINITION_ATTRIBUTES_REQUIRED_FOR_CONFIRMATION` | 维度缺少属性或业务主键 | 编辑维度属性并选择一个业务主键 |
| `MODEL_SPEC_DIMENSION_VARIANT_CONFLICT` | 同计划/集市/维度/variant 已有活动实现 | 打开响应中的 `repairRoute`，或填写新的 variant |
| `DIMENSION_ATTRIBUTE_MAPPING_INCOMPLETE` | 维度表字段未覆盖锁定维度 revision 的属性 | 在字段设计 Tab 完成属性映射 |
| `MODEL_SPEC_DIMENSION_INPUT_REQUIRED` | 进入实现前无物理来源或生成策略 | 在数据实现登记已确认来源，或选择受控生成策略 |
| `MODEL_SPEC_PHYSICAL_NAME_REQUIRED` | 尚未设置物理表名 | 回逻辑设计的“进入数据实现前补齐”区设置 |

## 数据核验

```sql
select status, count(*) from modeling_data_mart group by status;
select count(*) from modeling_warehouse_plan_data_mart;
select scope_type, count(*) from modeling_dimension_definition group by scope_type;
select count(*) from modeling_model_spec where data_mart_id is not null;
```

禁止直接修改 revision 快照或 checksum。冲突统一通过 ETag/CAS 重试，迁移问题通过新的前向 changeset 修复。
