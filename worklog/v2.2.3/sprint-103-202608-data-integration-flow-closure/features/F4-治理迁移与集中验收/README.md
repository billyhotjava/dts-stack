# F4：治理迁移与集中验收

**优先级**：P0
**状态**：`PASS_WITH_ENV_NOTE`（功能已部署；Chrome 95、三角色与业务金丝雀待现场验收）
**依赖**：F1～F3 完成

## 目标

补齐旧 DSL 兼容、审计、数据迁移、DAG/质量证据对账和外部运行一致性，并通过一次集中的测试、部署、回滚与 Chrome 95 旅程完成交付。

## 契约定义

| 类型 | 契约 | 关键行为 |
|---|---|---|
| 旧 DSL | `graph_dsl` nullable JSONB | 可读/导出、不可发布；不自动删除 |
| 迁移 | expand/contract changelog | 先可空、回填/双读、再收敛；可重跑 |
| 审计 | 现有 action catalog/strict audit | actor/task/revision/planChecksum/result/correlationId |
| 对账 | task/revision ↔ Airflow DAG | 31 个差异逐项 KEEP/ADOPT/RETIRE，默认 KEEP |
| 资产/质量证据 | execution ↔ dataset ↔ workflow/run | additive 关联与新鲜度；不新增可信生命周期状态 |
| 发布 | release/rollback plan | 制品 digest、schema、DAG 与旧 ACTIVE revision 可恢复 |
| 运维 | runbook/metrics/alerts | 校验、发布、同步、取消、日志、漂移故障路径 |

## UI/UX 规格

- 历史 DSL 只显示“历史画布草稿，不参与运行”，支持下载 JSON，不支持发布或导入为任务配置。
- 发布/运行失败显示平台错误码和 correlationId，不暴露 Airflow/数据库敏感细节。
- Chrome 95 验收覆盖数据集成流程设计和运行 Tab 的空、加载、错误、成功状态。
- 资产详情与运行详情使用“接入后质量验证/已通过质量验证/可信可用”业务文案，并展示同一当前证据。

## Tasks

| Task | 状态 | 依赖 | 产出 |
|---|---|---|---|
| [T01-补齐审计兼容与运行对账](T01-补齐审计兼容与运行对账.md) | IMPLEMENTED_AND_DEPLOYED | F1～F3 | schema、审计脱敏、外部 run 去重、旧 DSL 与 DAG 对账 |
| [T02-集中验证发布回滚与运维交接](T02-集中验证发布回滚与运维交接.md) | PASS_WITH_ENV_NOTE | F4/T01 | 聚焦测试、制品、回滚准备、现代 Chrome E2E、runbook；现场门禁保留 |

## Definition of Ready

- [ ] 所有业务代码任务完成，精确 diff 与 impact 报告可复核。
- [ ] migration、DAG 对账和回滚目标为精确 ID，不使用宽泛删除。
- [ ] Chrome 95、三角色、金丝雀和测试夹具均可用。

## 完成标准

- [ ] 旧任务、旧 revision、旧 DSL 和旧客户端在兼容窗口内可读可用。
- [ ] legacy execution 无关联时显示 `MISSING/UNKNOWN`，不从 latest run 猜测当前证据；新批次不会沿用旧可信徽标。
- [ ] 每个历史 DAG 有证据与批准状态，没有自动清理。
- [ ] 构建、容器健康、业务运行和目标浏览器验收分别留证。
- [ ] runbook、指标、告警、容量和失败处置完成运维交接。
