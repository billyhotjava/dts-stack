# P1-01 元数据采集与陈旧数据修复

`status`: `done`
`priority`: `P1`

## 目标

修复采集后资产列表与实际库结构不一致、陈旧表残留问题。

## 后端实施点

1. 明确采集结果的主数据来源与刷新策略。
2. 对删除表增加失效标记/清理策略。
3. 补充采集运行日志与错误分类接口。

## 前端实施点

1. 元数据采集页展示“新增/更新/失效”统计。
2. 资产列表显示最近同步时间与同步状态。
3. 支持手动刷新并显示进度。

## 验收标准

- 删除或变更表后，资产页可在可控时间内反映。
- 采集日志可定位失败原因。

## 实施结果

1. JDBC 采集引入“失效标记/清理策略”：
   - 默认 `MARK`：缺失表改为失效（`enabled=false`、`lifecycleStatus=STALE`）。
   - 可配置 `PURGE`：通过 `catalogPurgeStale=true` 或 `catalogCleanupMode=PURGE` 物理清理。
   - 采集结果补充：`datasetsMarkedStale`、`datasetsPurged`、`datasetsRemoved`。
2. 元数据主视图收敛到本地 catalog 可见数据（过滤失效数据），避免陈旧表继续出现在“已发现表”中。
3. 采集运行诊断增强：
   - 新增接口：`GET /api/catalog/sync/runs/{runId}/diagnostics`。
   - 统一错误分类：`AUTH/TIMEOUT/NETWORK/SQL/DRIVER/UNKNOWN`。
4. 前端页面完善：
   - 元数据采集页新增“同步进度条”“最新新增/更新/失效统计”。
   - 采集历史新增“失效标记/物理清理/错误分类/查看日志”列。
   - 资产列表新增“最近同步时间/同步状态”列。

## 验证记录

- `source/dts-platform`: `./mvnw -DskipTests compile` 通过。
- `source/dts-platform-webapp`: `pnpm build` 通过。
