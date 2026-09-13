# Consumption Permission Evidence

状态: READY（最终 IT 阶段统一执行）

## 验收范围

- analytics 本地授权迁移到 platform `asset_grant`。
- 大屏列表与我的概览使用同一套 platform 权限事实源。
- PUBLIC 大屏无授权可见，非 PUBLIC 大屏必须显式授权。
- fallback 命中可被日志和中央审计追踪。

## 建议步骤

1. 执行存量迁移：

```bash
ANALYTICS_BASE_URL=http://127.0.0.1:18083 \
bash worklog/v2.2.3/sprint-31-202605/it/scripts/screen-permission-migration.sh
```

2. 检查 platform 权限：

```bash
curl -sS -X POST "$PLATFORM_BASE_URL/api/internal/asset-permission/check" \
  -H 'Content-Type: application/json' \
  -H 'X-DTS-Service: dts-analytics' \
  -H "X-DTS-Service-Token: $SERVICE_TOKEN" \
  -d '{"username":"ptrdemo","userRoles":["ROLE_PTR"],"userClassification":"INTERNAL","action":"READ","asset":{"type":"SCREEN","id":"7"},"assetClassification":"INTERNAL"}'
```

3. 对比 UI：

- `ptrdemo` 能在大屏列表看到已授予 `SCREEN:7` 的大屏。
- `ptrdemo` 不能在大屏列表或我的概览看到未授权 INTERNAL 大屏。
- PUBLIC 大屏不需要授权即可显示。

## 需留存证据

- 迁移接口返回 JSON。
- platform asset permission check 返回 JSON。
- 大屏列表和我的概览截图。
- fallback 演练时的 `analytics_permission_fallback` 日志和中央审计记录。
