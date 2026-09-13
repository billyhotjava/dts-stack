# Sprint-31 F7 观测、审计与性能准入证据

## 状态

**READY**：准入脚本和证据口径已沉淀。按当前执行约束，真实接口输出、日志摘要和截图在 Sprint-32 完成后统一执行和归档。

## 待归档证据

| 证据 | 建议文件 | 验收口径 |
|---|---|---|
| 事件观测输出 | `events-console.json` | `summary.failed=0` 或失败有解释 |
| 审计证据输出 | `audit-evidence.json` | 黄金链路动作存在 audit action code |
| 发布治理输出 | `release-governance.json` | `readyForRelease=true` 或阻断项明确 |
| 服务认证日志 | `service-auth-denied.log` | 正常链路无 `service_auth_denied` |
| analytics fallback 日志 | `analytics-permission-fallback.log` | 迁移后无 fallback 命中 |
| 性能准入结论 | `performance-admission.md` | 明确 100MB/500MB/百万行边界 |

## 推荐命令

```bash
bash worklog/v2.2.3/sprint-31-202605/it/scripts/observability-admission-check.sh
```

如需读取实时接口：

```bash
RUN_LIVE=1 BASE_URL=http://127.0.0.1:18082 \
  bash worklog/v2.2.3/sprint-31-202605/it/scripts/observability-admission-check.sh
```

## 阻断条件

- 发布治理存在 BLOCKER 但仍允许上线。
- dbt release gate 阻断项未解释。
- 资产权限检查绕过 platform `asset_grant`。
- 非公开大屏无授权仍可在列表或概览中展示。
- 正常链路出现 `service_auth_denied` 或 analytics 本地权限 fallback，且没有迁移/配置解释。
- 页面或文档承诺 500MB/百万行 CSV 正式可用。
