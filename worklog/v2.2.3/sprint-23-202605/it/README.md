# Sprint-23 IT / 验收入口

## 验收目标

验证数据资产门户从 DTS 弱 catalog 迁移到 OpenMetadata 技术资产主目录后，仍满足 DTS 治理、权限、血缘、质量和回滚要求。

## 验收场景

1. **OpenMetadata 同步**
   - OpenMetadata 中至少有一个 PostgreSQL/dbt 采集资产。
   - DTS 执行同步后，`om_asset_cache` 和 `om_column_cache` 有对应记录。
   - 记录包含 `om_entity_id`、`fqn`、service、database、schema、table、lastSyncedAt。

2. **存量资产映射**
   - 至少一个旧 `catalog_dataset` 能自动匹配 OM 资产。
   - 密级、部门、负责人、生命周期迁移到 `catalog_asset_extension`。
   - 无法匹配资产进入人工确认状态。

3. **资产门户**
   - 数据资产列表展示 OM 资产。
   - 已治理资产显示 DTS 扩展属性。
   - 未治理资产显示待认领/待定级状态。
   - OpenMetadata 暂不可用时，列表仍能读取最近 cache。

4. **资产详情**
   - 字段、字段类型、描述和 profile 来自 OM cache。
   - 密级、部门、权限、脱敏、生命周期来自 DTS extension。
   - 页面显示同步时间和数据来源。

5. **血缘**
   - 技术 table/column lineage 来自 OpenMetadata cache。
   - DTS 接入任务、运行批次、dbt、指标或报表节点能作为 overlay 出现。
   - 图上边来源可区分 `openmetadata` 和 `dts-*`。

6. **质量**
   - DTS 治理运行结果优先展示。
   - OM test case 作为技术质量来源展示。
   - 两者缺失时显示可定位原因。

7. **权限与审计**
   - 普通用户只能看到有权限的资产。
   - opadmin 可看到映射异常和待治理资产。
   - 治理属性变更写入审计。

8. **回滚**
   - feature flag 切回旧路径后，旧资产列表和详情可用。
   - 回滚不删除 OM cache 和 DTS extension 数据。

## 脚本

- `scripts/asset-portal-smoke.sh`：执行 OM 资产同步、资产列表和映射诊断 API 验证。
- `scripts/lineage-quality-smoke.sh`：按 `DTS_OM_ASSET_ID` / `DTS_LEGACY_DATASET_ID` 验证 OM 血缘缓存、DTS 血缘影响和质量结果。
- `scripts/mapping-report.sql`：输出映射统计和未匹配样例，作为迁移 dry-run / 发布前风险报告。

## 回滚开关

- 前端设置 `VITE_CATALOG_ASSET_PORTAL_V2=false` 时，数据资产地图回退旧 `/api/catalog/datasets` 主路径。
- 回滚不删除 `om_*` cache 和 `catalog_asset_*` 扩展表，避免丢失同步和治理扩展数据。

## 证据目录约定

```text
it/evidence/<yyyymmdd>-<env>/
  01-openmetadata-source.json
  02-cache-sync.sql.txt
  03-mapping-report.json
  04-asset-list-response.json
  05-asset-detail-response.json
  06-lineage-response.json
  07-quality-response.json
  screenshots/
  residual-risks.md
```
