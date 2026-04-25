# T01: `bi_report_link.source` 列与 liquibase changeset

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

给 `bi_report_link` 加 `source VARCHAR(32)` 列（默认 `MANUAL`），区分手工注册的 BI 报表与定时同步进来的大屏，防止 reconcile 误删管理员行。

## 技术设计

新建 changeset `20260425-1000_bi_report_link-source.xml`：

- `addColumn`：`source VARCHAR(32) DEFAULT 'MANUAL' NOT NULL`
- 现有数据全部默认 `MANUAL`
- 加索引 `idx_bi_report_link_source` 加速 reconcile 的扫描
- preCondition `tableExists`、`columnExists.not`，保持幂等
- rollback：`dropIndex` + `dropColumn`

domain 层：`BiReportLink.java` 新增 `private String source;` + getter/setter（不放进任何 DTO，仅服务层使用）。

## 影响范围

- `source/dts-platform/src/main/resources/config/liquibase/changelog/20260425-1000_bi_report_link-source.xml`（新建）
- `source/dts-platform/src/main/resources/config/liquibase/master.xml`（include 新 changeset）
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/visualization/BiReportLink.java`（加字段）

## 验证

- [ ] `./mvnw -pl dts-platform liquibase:status` 显示新 changeset pending
- [ ] 启动后 `\d bi_report_link` 看到新列默认值 `MANUAL`
- [ ] 现有手工行 `source` 全为 `MANUAL`
- [ ] rollback 脚本能回退（手测）

## 完成标准

- [ ] changeset 文件创建，preCondition + rollback 完整
- [ ] `BiReportLink` domain 字段 + getter/setter 添加
- [ ] master.xml include 该 changeset
