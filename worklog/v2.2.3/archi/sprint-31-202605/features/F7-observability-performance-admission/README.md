# F7: 观测、审计与性能准入

**优先级**: P1
**状态**: DONE
**目标**: 给企业交付补上运行可观测、审计可追踪和性能边界，避免“功能跑通但现场不可承诺”。

## 任务

| Task | 内容 | 验收 |
|---|---|---|
| T01 | 黄金链路审计动作补齐 | 已形成审计动作矩阵，覆盖接入、预检、发布、注册、授权 |
| T02 | 运行指标面板 | 已复用事件观测、ELT 控制台、指标运营台、发布治理入口 |
| T03 | 大 CSV / 大表性能准入 | 已明确 v2.2.3 当前准入边界与阻断项 |
| T04 | 服务间调用失败诊断 | 已固化 `service_auth_denied` 诊断口径和日志检索方法 |
| T05 | Sprint-31 IT 证据归档 | 已新增 `it/evidence/observability-performance/` 和准入脚本 |

## 代码关注点

- audit filters / audit services
- `ExternalRunLogService`
- ingestion execution services
- dbt run result services
- platform/analytics service-auth logging

## 交付物

- 准入契约: `worklog/v2.2.3/sprint-31-202605/assets/observability-performance-admission.md`
- IT 证据目录: `worklog/v2.2.3/sprint-31-202605/it/evidence/observability-performance/README.md`
- 准入脚本: `worklog/v2.2.3/sprint-31-202605/it/scripts/observability-admission-check.sh`

## 说明

按当前执行约束，Sprint-31A -> Sprint-31 -> Sprint-32 期间不做中间编译、镜像构建或容器重建。本 Feature 完成的是准入逻辑、现有代码入口核对和最终验收脚本沉淀；运行输出将在 Sprint-32 完成后统一补证据。
