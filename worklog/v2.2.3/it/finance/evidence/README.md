# 财务 Demo 验收证据

本目录只提交证据模板或脱敏后的验收记录，不提交凭据、Token、Cookie、完整连接串或客户数据。

建议由测试同事按以下文件归档：

| 文件 | 必含内容 |
|---|---|
| `01-source-ingestion.md` | 数据源 ID、连接测试、3 张表探测、三阶段入湖 run ID 和行数 |
| `02-planning-metadata.md` | ODS 资产 ID、业务域/过程/集市/计划 ID 和 CURRENT revision |
| `03-standards-master-data.md` | 术语、数据元、码表、单位和主数据验证 |
| `04-model-design.md` | 3 个维度定义、6 个 ModelSpec ID/revision、粒度和依赖截图 |
| `05-build-materialization.md` | 编译、测试、物化、物理表和行数；失败时保留错误 |
| `06-quality-gates.md` | 基线通过、脏数据失败、修复通过的规则级结果 |
| `07-metrics.md` | 8 个指标版本、口径、owner、发布状态和抽样值 |
| `08-consumption-permission-lineage.md` | ADS、BI、API、数据产品、授权正反例和血缘 |
| `09-ops-audit.md` | 运行实例、操作人、时间、审计动作、失败恢复 |

每条关键结论至少记录：

```text
tenant:
object_type:
object_code:
actual_id:
revision:
run_id:
status:
executed_at:
operator:
evidence:
notes:
```

截图只能作为辅助证据；数据库行数、API 响应、run ID、revision 和审计记录应尽量保留为可复制文本。
