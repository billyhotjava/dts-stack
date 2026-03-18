# ODS 表字段映射说明

## 重要提示

**Excel 导入入湖任务时，ODS 目标表的字段名必须和本文档一致。** 模型 SQL 按字段名引用 ODS 表，如果字段名不匹配，dbt run / SQL 建表会报错。

## ODS 表名

```
ods_project_subject_domain
```

## 字段映射表（31列）

| 序号 | Excel 表头（project1.xlsx） | ODS 字段名 | 数据类型 | 模型引用 | 说明 |
|:---:|---------------------------|-----------|---------|:------:|------|
| 1 | 项目编号 | `project_no` | varchar | ✓ | 主键之一，不能为空 |
| 2 | 分系统/分任务 | `subsystem` | varchar | ✓ | 子项目标识，不能为空 |
| 3 | 节点任务及目标 | `node_task` | varchar | ✓ | 主键之一 |
| 4 | 节点计划时间 | `plan_date` | varchar | ✓ | 主键之一，模型用 parse_date_safe 转日期 |
| 5 | 节点计划周数 | `plan_week` | varchar | ✓ | 模型用 parse_numeric_safe 转数值 |
| 6 | 交付物 | `deliverable` | varchar | ✓ | v2.2.2 新增，描述性字段 |
| 7 | 节点类型 | `node_type` | varchar | ✓ | 枚举：一般节点/重要节点/重大节点/里程碑节点 |
| 8 | 负责人 | `owner` | varchar | ✓ | |
| 9 | 责任科室 | `dept` | varchar | ✓ | |
| 10 | 分管室领导 | `dept_leader` | varchar | ✓ | |
| 11 | 完成情况 | `completion_status` | varchar | ✓ | 枚举：6种完成状态 |
| 12 | 协同部门 | `collab_dept` | varchar | ✓ | |
| 13 | 责任监管部门 | `supervisor_dept` | varchar | ✓ | |
| 14 | 延期预计完成时间 | `delay_expected_date` | varchar | ✓ | 模型用 parse_date_safe 转日期 |
| 15 | 未完成原因及当前进展 | `incomplete_reason` | varchar | ✓ | 用于延期原因自动分类 |
| 16 | 风险等级 | `risk_level` | varchar | ✓ | 枚举：高/中/低 |
| 17 | 主要风险内容及措施 | `risk_content` | varchar | ✓ | |
| 18 | 延期影响分析 | `delay_impact` | varchar | ✓ | |
| 19 | 实际完成时间 | `actual_date` | varchar | ✓ | 模型用 parse_date_safe 转日期 |
| 20 | 实际完成周数 | `actual_week` | varchar | ✓ | 模型用 parse_numeric_safe 转数值 |
| 21 | 所领导 | `institute_leader` | varchar | ✓ | |
| 22 | 来源 | `source` | varchar | ✓ | |
| 23 | 延期项目原计划时间 | `original_plan_date` | varchar | ✓ | 模型用 parse_date_safe 转日期 |
| 24 | 计划延误时间（已变更） | `delay_days_changed` | varchar | | 透传，不参与计算 |
| 25 | 计划延误时间（未变更） | `delay_days_unchanged` | varchar | | 透传，不参与计算 |
| 26 | 是否提交延期申请 | `delay_applied` | varchar | ✓ | |
| 27 | 项目主管 | `project_manager` | varchar | ✓ | |
| 28 | 最后更新时间 | `last_update_time` | varchar | ✓ | 模型用 parse_date_safe 转日期 |
| 29 | 最后更新月数 | `last_update_month` | varchar | ✓ | v2.2.2 新增，模型用 parse_numeric_safe 转数值 |
| 30 | 填写人 | `filled_by` | varchar | ✓ | |
| 31 | 亮点工作 | `highlight` | varchar | ✓ | |

## 建表 SQL（手动建表时使用）

如果需要手动创建 ODS 表（不通过入湖任务），使用以下 SQL：

```sql
CREATE TABLE IF NOT EXISTS ods_project_subject_domain (
    id              serial PRIMARY KEY,
    project_no      varchar(500),
    subsystem       varchar(500),
    node_task       varchar(500),
    plan_date       varchar(500),
    plan_week       varchar(500),
    deliverable     varchar(500),
    node_type       varchar(500),
    owner           varchar(500),
    dept            varchar(500),
    dept_leader     varchar(500),
    completion_status varchar(500),
    collab_dept     varchar(500),
    supervisor_dept varchar(500),
    delay_expected_date varchar(500),
    incomplete_reason varchar(500),
    risk_level      varchar(500),
    risk_content    varchar(500),
    delay_impact    varchar(500),
    actual_date     varchar(500),
    actual_week     varchar(500),
    institute_leader varchar(500),
    source          varchar(500),
    original_plan_date varchar(500),
    delay_days_changed varchar(500),
    delay_days_unchanged varchar(500),
    delay_applied   varchar(500),
    project_manager varchar(500),
    last_update_time varchar(500),
    last_update_month varchar(500),
    filled_by       varchar(500),
    highlight       varchar(500),
    source_system   varchar(200) DEFAULT 'excel',
    import_time     timestamp DEFAULT now()
);
```

## 入湖操作时的字段对应

在平台页面创建入湖任务时：

1. **目标表名**填写：`ods_project_subject_domain`
2. **字段映射**按上表的"Excel 表头 → ODS 字段名"逐一对应
3. **所有字段类型**选择 `varchar/text`（不要选 date/number）
4. Addax 只负责搬运，类型转换由 dbt/SQL 层处理

## 完成情况枚举值（必须严格匹配）

以下 6 个值是 `dim_completion_status` 维度表定义的标准值，Excel 中的"完成情况"列必须是其中之一，否则对应的布尔标志（is_completed 等）会默认为 false：

| 完成情况 | is_completed | is_on_time | is_overdue_completed | is_incomplete |
|---------|:---:|:---:|:---:|:---:|
| 正常待完成 | ✗ | ✗ | ✗ | ✗ |
| 按时完成 | ✓ | ✓ | ✗ | ✗ |
| 超期已完成已变更 | ✓ | ✗ | ✓ | ✗ |
| 超期已完成未变更 | ✓ | ✗ | ✓ | ✗ |
| 不正常待变更 | ✗ | ✗ | ✗ | ✓ |
| 超期未完成未变更 | ✗ | ✗ | ✗ | ✓ |
| 超期未完成已变更 | ✗ | ✗ | ✗ | ✓ |

## 节点类型枚举值

| 节点类型 | is_general |
|---------|:---:|
| 一般节点 | ✓ |
| 重要节点 | ✗ |
| 重大节点 | ✗ |
| 里程碑节点 | ✗ |

## 风险等级枚举值

`高`、`中`、`低`

## 必填字段

以下字段为空时，该行会被模型过滤（不进入 DWD 层）：

- `project_no`（项目编号）
- `plan_date`（节点计划时间）

## 脏数据处理

以下占位值会被 `nullif_placeholder` 宏自动转为 NULL：

`''`, `'/'`, `'-'`, `'--'`, `'N/A'`, `'NA'`, `'#N/A'`, `'#VALUE!'`, `'#DIV/0!'`, `'NULL'`
