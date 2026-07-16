# PJM 黄金主线夹具

## 业务链

```text
业务过程: project-node-plan-loop / 项目节点计划闭环
业务对象: project-node / 项目节点 / FACT
```

## 对象契约

- 业务粒度：`project_no + subsystem + node_task + plan_date`
- 技术主键：`source_row_id` 派生的 `node_id`
- 标准字段：`project_no`、`plan_date`、`node_type`、`completion_status`、`risk_level`、`delay_days`
- 一致性维度：日期、项目、节点类型、风险等级、完成状态

## 新版本产物

| 目标 | 逻辑名称 | 类型 | 目标层 |
|---|---|---|---|
| 明细事实 | 项目节点明细 | FACT | DWD |
| 月度汇总 | 项目进度月汇总 | SUMMARY | DWS |
| KPI 数据集 | 项目进度 KPI | APPLICATION | ADS |

新版本不要求继续使用旧模型名称；旧 `biz_dwd_project_node_v2` 等模型通过导入夹具验证兼容性。

## 验收断言

1. 普通用户可从业务过程创建项目节点对象并声明粒度。
2. 标准字段绑定后才能生成 DWD ModelSpec。
3. 生成物包含 SQL、schema.yml、至少一个质量测试和 lineage metadata。
4. 高级开发编辑 SQL 后，manifest 导入能识别模型、字段和来源。
5. Airflow 运行记录能关联 Addax 批次、dbt run、dbt test 和 PostgreSQL 目标表。
