# Sprint-23 发布、升级与回滚 Runbook

## 发布前检查

1. 确认 OpenMetadata server、ingestion、platform、platform-webapp 均为目标版本。
2. 执行数据库迁移，确认 `om_asset_cache`、`om_column_cache`、`om_lineage_cache`、`catalog_asset_extension`、`catalog_asset_mapping` 已存在。
3. 执行映射 dry-run：

```bash
psql "$DTS_PLATFORM_JDBC_URL" -f worklog/v2.2.3/sprint-23-202605/it/scripts/mapping-report.sql
```

4. 记录未匹配资产样例，人工确认高风险 FQN，不静默合并低置信度资产。
5. 确认前端未设置 `VITE_CATALOG_ASSET_PORTAL_V2=false`，否则资产地图会回退旧路径。

## 升级步骤

1. 发布 platform 后端，等待 health 为 healthy。
2. 发布 platform webapp。
3. 用内部服务 principal 或现场登录会话执行 OM cache 同步：

```bash
DTS_BASE_URL=http://127.0.0.1:18082 \
DTS_SERVICE_HEADER=dts-ingestion \
DTS_SMOKE_OUT=worklog/v2.2.3/sprint-23-202605/it/evidence/$(date +%Y%m%d)-local \
worklog/v2.2.3/sprint-23-202605/it/scripts/asset-portal-smoke.sh
```

4. 选取一个已映射资产和一个未治理 OM 资产，分别打开资产地图、资产详情、字段、技术详情、治理扩展、血缘和质量页。
5. 对已映射资产执行 lineage / quality smoke：

```bash
DTS_BASE_URL=http://127.0.0.1:18082 \
DTS_SERVICE_HEADER=dts-ingestion \
DTS_OM_ASSET_ID=<om_asset_cache.id> \
DTS_LEGACY_DATASET_ID=<catalog_dataset.id> \
worklog/v2.2.3/sprint-23-202605/it/scripts/lineage-quality-smoke.sh
```

## 验收判定

- 资产地图能看到 OpenMetadata cache 资产和 DTS legacy fallback 资产。
- 资产详情字段、profile、raw metadata 来自 OM cache。
- 治理扩展可保存密级、部门、负责人、生命周期、启停和权限/脱敏/行过滤引用。
- 映射异常和未治理资产有明确状态，不显示为空白成功。
- 质量和血缘缺失时页面能说明来源、缺失原因或同步入口。

## 回滚步骤

1. 前端构建设置：

```bash
VITE_CATALOG_ASSET_PORTAL_V2=false
```

2. 重新发布 platform webapp，资产地图回退 `/api/catalog/datasets` 主路径。
3. 不删除 `om_*` cache 和 `catalog_asset_*` 扩展表，避免后续恢复时丢失同步、映射和治理扩展。
4. 如后端需要回滚版本，只回滚应用镜像；保留新增表和 changelog 记录。
5. 回滚后验证旧资产列表、旧详情、血缘和质量页面仍可读取 legacy dataset。

## 常见故障

| 现象 | 优先排查 |
|---|---|
| 资产地图无数据 | `VITE_CATALOG_ASSET_PORTAL_V2`、`/api/catalog/assets-v2` 响应、`om_asset_cache` 记录数、权限部门过滤 |
| 质量页显示未找到元数据 | 资产 `legacyDatasetId` 映射、`catalog_asset_mapping.match_status`、质量接口是否传 legacy dataset id |
| 血缘只有 DTS 边无 OM 边 | `om_lineage_cache` 是否同步、`/assets-v2/{id}/lineage/sync` 返回边数 |
| 未治理资产普通用户不可见 | `catalog_asset_extension.enabled`、`ownerDept`、当前 `X-Active-Dept`、opadmin bypass |
| 映射数量异常 | FQN 大小写、schema/database 差异、forbidden database 过滤、历史污染资产 |

## 证据归档

把以下内容放入 `it/evidence/<yyyymmdd>-<env>/`：

- `asset-portal-smoke.sh` 输出目录。
- `lineage-quality-smoke.sh` 输出目录。
- `mapping-report.sql` 输出。
- 资产地图、资产详情、治理扩展、血缘、质量页截图。
- 发布参数、版本号、残余风险和回滚演练结果。
