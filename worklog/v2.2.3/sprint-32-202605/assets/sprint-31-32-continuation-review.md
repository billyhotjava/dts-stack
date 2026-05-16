# Sprint-31 / Sprint-32 Continuation Review

**日期**: 2026-05-16
**范围**: Sprint-31 主链路计划、Sprint-32 `dts-metrics` 拆分计划、当前 `dts-platform` / `dts-metrics` 服务边界。

## P0 Findings

| 编号 | 问题 | 证据 | 处理 |
|---|---|---|---|
| P0-1 | Sprint-31 和 Sprint-32 的版本启停口径冲突 | Sprint-31 仍写“基础版可关闭语义指标”，Sprint-32 已调整为 metrics 默认部署、商务限制后续交给 license | 已把 Sprint-31/F5/IT 和 sprint queue 调整为“主链路解耦，不在配置层关闭 metrics” |
| P0-2 | `dts-metrics` 被加入 trusted-services，但平台服务鉴权白名单没有放行任何 metrics internal 调用 | `application.yml` 有 `dts-metrics` token 配置；`ServiceDependencyAuthenticationFilter` 只处理 `dts-ingestion` 和 `dts-analytics` | 已补 `dts-metrics` 对 `/api/internal/capabilities`、asset-permission check/batch/accessible/read-grants 的只读/校验访问 |

## P1 Findings

| 编号 | 问题 | 影响 | 后续建议 |
|---|---|---|---|
| P1-1 | `PlatformCapabilityResource` 只有公开 `/api/capabilities`，缺服务间契约入口 | F2 文档中的 `GET /api/internal/capabilities` 无法验证服务鉴权 | 已新增 `/api/internal/capabilities`，后续让 `dts-metrics` 客户端实际调用并写 IT 证据 |
| P1-2 | Sprint-32 F1/F2 已有部分代码落地，但任务状态仍是粗粒度 `IN_PROGRESS` | 团队无法分辨“服务壳已完成”和“服务鉴权/平台契约未完成” | 下一批补 task 级状态和 evidence 映射 |
| P1-3 | metric-pack 当前只校验 manifest，不校验引用文件和 platform 已登记资产 | 合作方包仍可能在后续导入阶段暴露依赖缺失 | F4 第一优先补包目录解析、依赖资产 dry-run 和差异报告 |

## Current Boundary

- `dts-platform`: IAM、资产、权限、审计、审批、数据源密钥、dbt 发布门禁唯一事实源。
- `dts-metrics`: 指标语义、行业包、受控 DSL、候选 DWS/ADS artifact，不持有数据源密钥，不维护本地用户/角色/权限事实源。
- `dts-analytics`: 大屏/看板消费层，权限继续收敛到 platform `asset_grant`。

## Next Batches

1. Sprint-32 F2: 让 `dts-metrics` 实际调用 `/api/internal/capabilities`，并补服务鉴权失败/成功测试。
2. Sprint-32 F4: 扩展 metric-pack v0.1 校验，从 manifest 扩展到包内文件和依赖资产引用。
3. Sprint-31 F6: 复核 analytics local fallback 默认策略，输出 fallback 命中审计和迁移报表。
4. Sprint-31 F1/F4: 补黄金链路脚本和 dbt release gate 的 strict/dev 模式差异证据。
