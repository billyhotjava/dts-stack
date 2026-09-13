# API 兼容和弃用矩阵

**Sprint**: Sprint-31A
**Feature**: F6/T03
**状态**: DONE

## 原则

1. platform 是资产、权限、审计、发布门禁的唯一事实源。
2. 新增调用优先使用 `/api/catalog/assets-v2/**`、`/api/internal/asset-permission/**`、`/api/internal/capabilities`。
3. 旧 `semantic_*` 能力在 Sprint-32 作为兼容代理或明确弃用，不允许继续新增 platform 内部写入。
4. analytics 本地权限只读 fallback，命中要审计或告警。

## 矩阵

| 旧 API / 表面 | 新契约 | Sprint-31A 状态 | Sprint-31/32 策略 |
|---|---|---|---|
| `GET /api/catalog/datasets/**` | `GET /api/catalog/assets-v2/**` | 保留 | 继续兼容，新增能力不再扩展旧接口 |
| `GET /api/catalog/assets-v2` | 同路径 | 稳定 | 作为资产列表事实源 |
| `GET /api/catalog/assets-v2/{id}/contract` | 同路径 | 稳定 | dts-metrics / dts-analytics 读取资产身份 |
| `GET /api/catalog/assets-v2/{id}/schema-contract` | 同路径 | 稳定 | 指标建模和发布门禁读取字段合同 |
| `GET /api/catalog/assets-v2/governance-gaps` | 同路径 | 稳定 | 发布前和门户显示治理阻断 |
| `GET /api/catalog/assets-v2/lineage-failures` | 同路径 | 稳定 | 发布前和门户显示血缘证据缺口 |
| `GET /api/catalog/assets-v2/migration/dry-run` | 同路径 | 新增 | 迁移前只读风险报告 |
| `/api/semantic/**` | `/api/metrics/**` 或 dts-metrics 服务路径 | 未迁移 | Sprint-32 提供一个 Sprint 兼容代理或明确 410/503 |
| platform `semantic_*` 表写入 | dts-metrics `metric_*` 表 | 停止扩展 | 只做 dry-run、迁移报告、兼容读 |
| analytics `analytics_screen_access` 写入 | platform `asset_grant(SCREEN)` | 禁止新增 | local IAM 只读 fallback，命中告警 |
| `/bi/api/screens/{id}/grants` | platform-backed screen grant | 兼容 | 调用 platform 授权事实源 |
| 本地密级缺失默认放行 | platform classification deny | 已收紧 | 缺密级拒绝，需治理补齐 |

## 错误表达

| 场景 | 响应策略 |
|---|---|
| metrics 服务不可用 | 明确服务不可用，不伪装成 403 |
| 资产缺密级 | 返回/展示治理阻断，不自动降级为内部 |
| 旧 semantic 写入 | 返回明确弃用或只读错误 |
| fallback 命中 | 返回可用但记录审计/告警，不静默 |

## 弃用节奏

1. Sprint-31A：稳定 platform 资产和权限契约。
2. Sprint-31：主链路调用迁到 platform 契约。
3. Sprint-32：`/api/semantic/**` 提供兼容代理和弃用日志。
4. 后续版本：删除 platform 内部 semantic 写路径，保留数据迁移回滚脚本。
