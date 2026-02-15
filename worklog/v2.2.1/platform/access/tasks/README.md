# Platform 数据接入中心任务清单（v2.2.1）

> 依据：`worklog/v2.2.1/platform/access/report/data-access-center-gap-analysis.md`
> 状态定义：`planned` / `in-progress` / `done` / `blocked`

## 阶段总览

| 阶段 | 目标 | 状态 |
|---|---|---|
| P0 | 可用性与一致性补齐（调度、重试、门禁） | in-progress |
| P1 | 治理与可观测升级（流程化、漂移闭环、SLA） | in-progress |
| P2 | 商业化能力增强（能力契约、模板化、资源治理） | planned |

## 强制顺序

1. `P0-01` 入湖任务调度配置 UI/API 对齐
2. `P0-02` 执行历史补齐 FULL_RERUN
3. `P0-03` 元数据采集调度运维能力
4. `P0-04` P0 回归门禁自动化
5. `P1-01` 接入变更流程化（审批闭环）
6. `P1-02` Schema Drift 策略与工单化
7. `P1-03` 可观测性增强（SLA/失败趋势/MTTR）
8. `P2-01` 连接器能力契约统一
9. `P2-02` 模板化接入能力
10. `P2-03` 运行资源与配额治理

## 文件索引

- `P0-01-ingestion-schedule-ui-api-alignment.md`
- `P0-02-full-rerun-retry-ui.md`
- `P0-03-metadata-schedule-ops.md`
- `P0-04-p0-regression-gate-automation.md`
- `P1-01-change-log-workflow.md`
- `P1-02-schema-drift-policy-workflow.md`
- `P1-03-observability-sla-mttr.md`
- `P2-01-connector-capability-contract.md`
- `P2-02-ingestion-templates.md`
- `P2-03-resource-quota-governance.md`

## 管理规则

- 每个任务卡必须包含：范围、子任务、验收标准、风险与回滚。
- 每个任务完工后补：代码路径、测试命令、截图/日志证据。
- 未完成 `P0` 禁止进入 `P1/P2` 的代码实现阶段。
