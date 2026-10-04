# MDM 单一源收敛（最小改动版）

日期：2026-02-22

## 目标

- 在不改业务主流程的前提下，先把 MDM 配置收敛为“以 `DTS_MDM_GATEWAY_*` 环境变量为主”。
- 消除 `docker-compose` 各模式透传不一致导致的“同样 .env，行为不同”。
- 降低页面误编辑风险（将 MDM 配置标记为 BOOTSTRAP）。

## 已完成改动

1. `docker-compose-app.yml`
- 为 `dts-admin` 补齐 MDM 全量环境变量透传（28 项）。
- 增加 `./data:/data` 挂载，配合 `DTS_MDM_GATEWAY_STORAGE_PATH=/data/mdm`。

2. `docker-compose.dev.yml`
- 为 `dts-admin` 补齐缺失的 10 项 MDM 环境变量透传：
  - `CALLBACK_ALLOWED_IPS`
  - `REGISTRY_*` 5 项
  - `ROOT_CODE`
  - `AUTO_PROVISION_*` 3 项

3. `config/mdm/mdm-gateway.yml`
- 新增并统一为 env 占位：
  - `auto-provision-*`
  - `root-code`
  - `callback.allowed-ips`
  - `upstream.connect-timeout/read-timeout`
- 将 `upstream.form-params` 和 `payload-template.pushMode` 改为环境变量可覆盖，避免写死值与 `.env` 脱节。

4. `source/dts-admin`
- `OpsConfigEnvSyncService`：将 `DTS_MDM_GATEWAY_*` 归类为 `BOOTSTRAP`（并标记需重启）。
- Liquibase（`20260222-03_ops_config_runtime_metadata.xml`）新增变更：
  - 已存在的 `DTS_MDM_GATEWAY_*` 配置统一回写为 `config_scope=BOOTSTRAP`、`restart_required=true`。

## 验证结果

- `docker compose -f docker-compose.yml -f docker-compose-app.yml config -q` 通过。
- `docker compose -f docker-compose.yml -f docker-compose.dev.yml config -q` 通过。
- `source/dts-admin` 编译通过（`mvn -DskipTests compile`）。

## 备注

- `docker-compose.legacy.yml` 的 `group_add` 重复项为历史问题，与本次改动无关。
