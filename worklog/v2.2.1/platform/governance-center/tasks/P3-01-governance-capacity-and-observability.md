# P3-01 治理中心容量与可观测

`status`: `done`
`priority`: `P3`

## 目标

建立治理核心接口与批处理的容量基线和观测指标。

## 后端实施点

1. 为关键查询增加索引与慢 SQL 排查清单。
2. 统一返回耗时、失败分类、分页统计。
3. 对关键任务增加限流与并发保护。

## 前端实施点

1. 页面加载状态分层（骨架屏/局部 loading）。
2. 超时与重试提示统一。
3. 大表分页默认阈值与延迟加载优化。

## 验收标准

- 常用页面首屏时间、列表查询耗时达到目标阈值。
- 失败能通过页面+日志快速定位。

## 完成情况

1. 关键查询与索引：
   - 新增 Liquibase 变更：`20260224_02_governance_capacity_indexes.xml`
   - 覆盖 `gov_indicator_version` 发布查询与 `gov_reference_import_run` 历史查询复合索引。
2. 接口返回可观测字段统一：
   - 指标/维度/公共码表列表接口统一补充 `queryCostMs` 与 `pageStats`。
   - 新增码表导入运维概览接口：`GET /api/governance/reference-codes/ops/import-overview`。
3. 失败分类与并发保护：
   - 结构化导入预检/执行/回滚补充标准失败分类码（`GOV_REFERENCE_IMPORT_*`）。
   - 码表导入执行与回滚加入按 `codeTypeId` 并发锁，冲突时返回 `429`（busy）。
4. 前端可观测与交互优化：
   - 公共码表页面新增“导入运维概览”卡片（窗口、运行量、冲突/错误、失败 TopN）。
   - 导入历史弹窗增加刷新/重试入口，提升异常态可恢复能力。
   - 列表分页补齐 `showSizeChanger` 与阈值档位（10/20/50/100）。
5. 构建验证：
   - `source/dts-platform`: `./mvnw -DskipTests compile` 通过。
   - `source/dts-platform-webapp`: `pnpm build` 通过。
