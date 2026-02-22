# .env 变更重建清单（v2.2.1）

生成时间：2026-02-22

## 结论规则

- 只要 `.env` 变量被 `docker-compose*.yml` 引用，修改后都需要 `recreate` 对应容器（`restart` 不够）。
- 仅被 `init.sh/builds/*.sh` 使用的变量，通常需要重跑脚本，不一定需要立即重建运行容器。

## 统计

- `.env` 总变量：`220`
- 被 compose 引用（需要重建容器）：`168`
- 未被 compose 引用（脚本变量/未使用）：`51`
- 完全未引用：`2`（`IMAGE_TRINO`、`DEPLOY_MODE`）

## 关键分组（按前缀）

- `DTS_PKI_*` -> `dts-admin`
- `OAUTH2_*` + `OIDC_ISSUER_URI` -> `dts-admin`, `dts-platform`
- `DTS_AIRFLOW_*` -> `dts-ingestion`, `dts-openmetadata`, `dts-platform`
- `DTS_OPENMETADATA_*` -> `dts-ingestion`
- `DTS_PLATFORM_OPENMETADATA_*` -> `dts-airflow-init`, `dts-airflow-scheduler`, `dts-airflow-triggerer`, `dts-airflow-webserver`, `dts-ingestion`, `dts-openmetadata-ingestion`
- `DTS_MDM_GATEWAY_*` -> `dts-admin`
- `ANALYTICS_*`（compose 里使用到的部分）-> `dts-analytics`
- `PG_*` -> `dts-pg`, `dts-admin`, `dts-platform`, `dts-ingestion`, `dts-keycloak`, `dts-openmetadata`, `dts-openmetadata-ingestion`, `dts-openmetadata-init`, `dts-airflow-*`, `dts-analytics`
- `KC_*` -> `dts-keycloak`
- `HOST_*` -> `dts-proxy` 及各 webapp / backend（按 compose 引用）
- `IMAGE_*` -> 对应服务镜像定义（改动后建议 `pull/build` + `recreate`）
- `ADMIN_*` -> `dts-admin`, `dts-admin-webapp`
- `PLATFORM_*` -> `dts-platform-webapp`
- `TRAEFIK_*` -> `dts-proxy`

## 非 compose 变量（脚本侧）

以下主要由 `init.sh`/构建脚本消费：  
`KEYTOOL_IMAGE`、`KEYTOOL_IMAGE_STRICT`、`DTS_RUNTIME_ARCH`、`HOST_TRINO`、`HOST_RANGER`、`HOST_ANALYTICS`、`DOCKER_HOST_GATEWAY_IP`、`KC_DB_URL_PROPERTIES`、`KC_REALM`、`PG_AUTH_METHOD`、`PG_MODE`、`DTADMIN_DB_*`、`DTADMIN_API_PORT`、`DTS_PKI_CLIENT_CERT_HEADER_NAME`、`DTS_PKI_GATEWAY_ALT_PORT`、`DTS_PKI_GATEWAY_ENDPOINT`、`VITE_ADMIN_API_BASE_URL`、`VITE_ADMIN_PROXY_TARGET`、`VITE_KOAL_PKI_ENDPOINTS`、`VITE_KOAL_VENDOR_BASE`、`ANALYTICS_*`（部分）、`DTS_PLATFORM_OPENMETADATA_UI_BASE_URL`、`DTS_PLATFORM_OPENMETADATA_TABLE_FQN_PATTERN`、`DTS_DBT_*`、`ADMIN_ALLOWED_IPS`、`ADMIN_BACKUP_IPS`、`RANGER_*PASSWORD`、`IAM_*`、`GOVERNANCE_*`、`EXPLORE_*`、`LEGACY_STACK`。

## 产物

- 全量 key->service 映射：`worklog/v2.2.1/admin/configuration/raw/env-service-map.tsv`
