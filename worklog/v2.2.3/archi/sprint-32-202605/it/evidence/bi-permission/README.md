# Sprint-32 BI Dataset 候选注册与权限校验证据

## 状态

**READY**：权限事实源边界已补齐；BI Dataset 远端注册仍为延展目标，不作为 Sprint-32 MVP 必达。

## 覆盖范围

- platform 是唯一权限事实源。
- `dts-metrics` 只允许调用 platform asset permission 的只读/校验端点。
- `dts-metrics` 不能写入 `asset_grant`。
- BI Dataset 产物在 Sprint-32 只作为候选 metadata/artifact，不承诺自动注册 Superset 或自动生成完整大屏。

## 待执行命令

```bash
curl -sS -X POST http://127.0.0.1:18082/api/internal/asset-permission/check \
  -H 'Content-Type: application/json' \
  -H 'X-DTS-Service: dts-metrics' \
  -H 'X-DTS-Service-Token: <token>' \
  -d '{"username":"ptrdemo","asset":{"type":"DATASET","id":"ads_flower_rental_overview"},"action":"READ"}'
```

## 阻断条件

- 指标预览或发布前未调用 platform 权限检查。
- `dts-metrics` 本地保存用户/角色/授权事实。
- `dts-metrics` 能直接创建或删除 platform `asset_grant`。
