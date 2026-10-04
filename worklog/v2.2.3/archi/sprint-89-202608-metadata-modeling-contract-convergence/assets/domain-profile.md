# Sprint-89 领域与数据画像

**采样时间**: 2026-08-10（Asia/Shanghai）
**环境**: 本地 v2.2.3 运行实例，`dts_platform` PostgreSQL；只读 SQL
**边界**: 这是当前本地环境画像，不等同客户生产画像；生产容量与分布仍是 F0 输入。

## 统一语言

| 术语 | 定义 |
|---|---|
| 技术元数据 | 数据源、库/表/字段、类型、nullable、同步时间和结构变化等可机器采集事实 |
| 业务元数据 | 业务名称、owner、主题域、分类分级、标签、说明等需要权限与人工确认的事实 |
| source locator | 定位来源对象的技术 ID；`CATALOG_TABLE` 当前为 `catalog_table_schema.id` |
| asset identity | 跨模块授权、分类、血缘使用的 `CatalogAssetType + CatalogAssetKey` |
| source version | 对可执行表结构生成的 canonical fingerprint |
| confirmed version | 数仓规划中用户确认可用于建模的来源版本 |
| compatible drift | 不破坏既有被引用字段的结构变化 |
| breaking drift | 会使已引用字段不存在或不兼容的变化 |

## 领域不变量

1. 数据资产是元数据与资产身份的唯一权威 owner；采集端不能另建业务台账。
2. locator ID 不等于 asset key；两者必须通过明确 resolver 转换。
3. 同一物理来源自然键在消失、重现、重复同步时保持稳定 ID。
4. ModelSpec 只保存 WarehousePlan source binding 及已解析版本，不保存浏览器自报的权威表名。
5. 业务元数据不因重新采集被覆盖；字段删除先失效，不能直接物理删除。
6. 权限、租户、分类证据或来源版本未知时 fail closed。

## 当前数据剖面

| 指标 | 当前值 | 判断 |
|---|---:|---|
| `catalog_dataset` | 83 | 有可用于契约验证的真实本地目录数据 |
| `catalog_table_schema` | 78 | 5 个 dataset 当前无 table |
| `catalog_column_schema` | 1510 | 全部 status=ACTIVE |
| `catalog_schema_drift_event` | 29 | 全部 ticket_status=OPEN、policy_mode=REVIEW |
| `modeling_warehouse_plan` | 1 | 有规划容器 |
| `modeling_warehouse_plan_source` | 0 | 缺少元数据→规划的真实绑定样本，阻塞全链验收 |
| `modeling_model_spec` | 33 | 32 ARCHIVED、1 DRAFT |

### 采集与脏数据分布

| 观察项 | 当前值 | 设计影响 |
|---|---:|---|
| harvest_status=NULL | 57 | 不能把 NULL 一律解释为 CURRENT |
| harvest_status=SYNCED | 25 | 可作为生命周期回归样本 |
| harvest_status=STALE | 1 | 可验证失效解析，但不能修改真实数据 |
| source_id 非空且 harvest_status=NULL | 7 | 视为 UNKNOWN，需重采集/确认，不能无证据批量回填 |
| source_id 为空且 harvest_status=NULL | 50 | 手工/遗留资产，视为非采集管理 |
| dataset 自然键重复 | 0 | 当前唯一索引与样本一致 |
| table 无 parent dataset | 0 | table→dataset resolver 可依赖 FK |
| drift added/removed/changed | 838 / 2 / 0 | 当前 drift 主要为初次/增量新增，需避免把所有 added 都判为破坏性 |
| snapshot_time 范围 | 2026-08-04 ～ 2026-08-10 | 可验证新旧采集证据，不代表生产保留周期 |

### 来源类型

| type | 数量 | harvest=NULL / SYNCED / STALE |
|---|---:|---|
| POSTGRESQL | 72 | 52 / 19 / 1 |
| MYSQL | 6 | 0 / 6 / 0 |
| EXTERNAL_TABLE | 3 | 3 / 0 / 0 |
| DATASET | 2 | 2 / 0 / 0 |

## 边界与合规

- 画像只统计行数、状态与时间范围，不读取或输出业务字段值、身份、token 或凭据。
- 分类分级和部门访问继续使用现有 `CatalogAccessChecker` / classification boundary。
- 本 Sprint 不将采集字段覆盖业务 owner、classification、tags、description。
- 任何生产回填、purge 或 migration 必须先取得脱敏分布、备份与 rollback rehearsal。

## 待补生产输入

| 输入 | 用途 | 阻塞范围 |
|---|---|---|
| 客户环境 dataset/table/column 量级与最大单表字段数 | 校准分页、索引、响应体和同步耗时预算 | G1 性能最终签字 |
| 实际 drift 分布及重命名/类型变化样本 | 校准 compatible/breaking 规则 | F2/T02 |
| 至少一个授权建模账号 | API/UI/权限验收 | F3、F4/T02 |
| 一个可重复采集的非敏感来源样本 | 跑通消失/重现与全链路 | F0、IT-02/08 |
