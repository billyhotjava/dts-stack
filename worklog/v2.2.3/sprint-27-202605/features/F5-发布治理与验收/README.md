# F5: 发布治理与验收

**优先级**: P1
**状态**: IN_PROGRESS
**目标**: 为 ELT 与指标可视化补齐发布前检查、回归脚本和证据归档，避免只交付页面而没有企业级验收闭环。

## 任务

| Task | 状态 | 内容 |
|------|------|------|
| T01 | IN_PROGRESS | 定义 ELT 发布前检查清单：采集成功、dbt 成功、质量通过、血缘可查 |
| T02 | IN_PROGRESS | 定义指标发布前检查清单：定义完整、口径完整、运行成功、质量状态可见 |
| T03 | DONE | 新增 Sprint-27 smoke 脚本，覆盖 ELT/指标/审计核心路径 |
| T04 | DONE | 产出 evidence 目录结构和截图/响应归档规范 |
| T05 | IN_PROGRESS | 汇总剩余风险：Kafka 可选接入、审批待定、权限脱敏待定 |

## 当前落地

- 新增 `/ops/release-governance` 与 `/platform/release-governance` 发布治理入口。
- 后端新增 `/api/platform/sprint27/release-governance` 聚合 API，统一聚合治理发布门禁、ELT 执行观测、指标运维观测、事件 outbox 分发状态和 dbt release gate。
- 页面已改为消费 Sprint-27 聚合 API，并展示数据源状态；依赖接口 `ERROR` 时发布结论不会误判为可发布。
- 页面提供发布结论、通过率、发布路径、证据入口和检查清单。
- ELT 控制台、指标运营台、事件观测页已接入发布治理跳转。
- 新增 `it/scripts/sprint-27-smoke.sh`，覆盖 Sprint-27 聚合 API，证据归档到 `it/evidence/<date>-local/sprint-27/`。
- 本地已执行 smoke，证据目录：`it/evidence/20260503-local/sprint-27/`。

## 当前约束

- 发布治理只做检查和提示，不强制阻断发布。
- 客户审批、端到端权限、脱敏策略仍保持预留，不在本页面固化。

## 验收标准

- 有明确的手工和脚本验收入口。
- evidence 能支撑客户演示和回归。
- 发布治理不阻塞当前功能上线，只作为检查和提示。

## 非目标

- 不引入新的 CI/CD 平台。
- 不强制审批阻断发布。
- 不做跨环境 promotion 自动化。
