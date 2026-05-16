# F6: 迁移、回滚、集成测试与运维验收

**优先级**: P0
**状态**: READY
**目标**: 保证现有语义指标数据和 API 可以平滑迁移到 `dts-metrics`，并具备回滚和可验收证据。

## 任务

| Task | 内容 | 验收 |
|---|---|---|
| T01 | 现有数据迁移 dry-run | platform 现有 `semantic_*` 数据可映射到 `metric_*` 表，先输出 dry-run 报告 |
| T02 | 兼容 API 对照表 | 明确旧 `/api/semantic/**` 到新 `/api/metrics/**` 的映射、弃用和不兼容项 |
| T03 | 回滚方案 | 配置级回滚、数据级回滚、发布 artifact 回滚和容器回滚路径清晰 |
| T04 | 端到端 IT | 默认启动 metrics、导入包、生成 artifact、发布 dbt、注册 BI、权限校验 |
| T05 | 运维文档 | 端口、环境变量、健康检查、日志、备份、恢复、常见故障 |
| T06 | 发布准入 | 未通过迁移、权限、审计、dbt gate、IT 证据不得合并为 DONE |

## 完成标准

- [ ] IT 证据放入 `worklog/v2.2.3/sprint-32-202605/it/evidence/`。
- [ ] migration dry-run 输出可审计；生产级自动迁移作为后续延展，不作为 Sprint-32 必达。
- [ ] 回滚后默认入口、服务异常态和权限行为都可验证。
