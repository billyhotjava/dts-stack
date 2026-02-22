# P0-01 `.env` 参数台账与迁移边界（最新）

生成时间(UTC): 2026-02-22

## 总览

- 参数总量: `220`
- `visual_runtime`: `59`
- `visual_restart`: `25`
- `env_only`: `70`
- `review_required`: `66`

原始产物: `worklog/v2.2.1/admin/configuration/raw/env-catalog.csv`

## 可视化优先迁移（已纳入本轮）

- `DTS_AIRFLOW_*`
- `DTS_OPENMETADATA_*`
- `DTS_PLATFORM_OPENMETADATA_*`
- `DTS_MDM_GATEWAY_*`
- `DTS_SECURITY_IP_ALLOWLIST_*`
- `ADMIN_ALLOWED_IPS` / `ADMIN_BACKUP_IPS` / `ADMIN_WHITELIST_CIDRS`

## 可视化但需重启提示（已纳入本轮）

- `OAUTH2_*`
- `OIDC_ISSUER_URI`
- `DTS_PKI_*`
- `ANALYTICS_OIDC_*`
- `ANALYTICS_ENCRYPTION_SECRET`

## 保留在 `.env` / 编排层（本轮不迁移）

- `IMAGE_*`
- `HOST_*`
- `BASE_DOMAIN` / `TLS_PORT`
- `PG_SUPER_*`、`PG_DB_*`、`PG_USER_*`、`PG_PWD_*`
- `DEPLOY_MODE` / `LEGACY_STACK`

## 数据质量问题

- 检测到疑似截断值: `DTS_PLATFORM_OPENMETADATA_TABLE_FQN_PATTERN={service`
- 建议先修复 `.env` 后再执行批量配置迁移。
