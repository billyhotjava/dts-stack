# Sprint-72 集成验收

**状态**: CODE_AND_AUTOMATED_TESTS_DONE / NO-GO_PRODUCTION_ROLLOUT

本文件定义最终验收门禁。F1～F8 编码、定向回归、组合回归及隔离 PostgreSQL 集成测试已经完成。
按本轮约束未重建或发布容器，因此代码完成状态与生产放行状态分别记录。

## 验收层次

1. Task focused：每个写入口先证明不可降级和错误码。
2. Feature 组合：按接入、血缘、消费、大屏、生命周期分别验证。
3. Sprint 全量：F1～F8 全部实现后统一执行一次生产构建和真实环境闭环。

## IT-01 接入封存

- JDBC 源字段带 SECRET，ODS 首次落盘后字段和资产有效密级均不低于 SECRET。
- Excel/CSV 文件为 SECRET，未单独设置字段时所有字段最低为 SECRET。
- 文件字段设 CONFIDENTIAL 时，资产有效密级为 CONFIDENTIAL。
- 缺少密级时只允许隔离预检，生产落盘返回稳定阻断码。
- retry/checkpoint 复用相同 seal/version/checksum。

## IT-02 不可降级

- 对 dataset、column、model、metric、API、data product、report、screen 分别尝试降级，全部被服务端拒绝。
- 管理员、批处理、OpenLineage、同步任务和迁移脚本均不能绕过。
- 并发提交 SECRET 与 CONFIDENTIAL，最终必须为 CONFIDENTIAL。
- 未知密级编码不能按 PUBLIC 处理。

## IT-03 血缘传播

- 字段 A=INTERNAL、B=SECRET，派生 C=A+B 后 C=SECRET。
- 上游表 SECRET 与 INTERNAL join，下游表有效密级为 SECRET。
- 上游从 SECRET 升到 CONFIDENTIAL，所有下游幂等升密且不重复制造无意义事件。
- 字段血缘缺失时取资产最高值并阻断发布。
- 循环血缘进入错误/治理告警，不降低当前有效密级。

## IT-04 建模与发布

- ModelSpec revision 和 ReleaseCandidate 固定 classification snapshot version/checksum。
- 发布前上游升密导致快照过期时，发布被阻断并提示重新预检。
- dbt manifest/OpenLineage 真实运行能产生传播事件。
- 脱敏视图不触发自动降密。

## IT-05 消费资产

- 指标、Card、报表、API 和数据产品有效密级等于全部引用来源的最高值。
- 人工下限可以升高，不能降低。
- 上游升密后缓存失效，旧导出/分享/公开链接重新校验。
- 低密级用户无法通过旧 URL、缓存或批量接口读取高密级数据。

## IT-06 大屏

- 大屏包含 INTERNAL 与 SECRET 组件时，大屏有效密级自动为 SECRET。
- 多页面、多组件和多级下钻全部纳入计算。
- card、metric、dataset、SQL/database、API 六类来源均有覆盖。
- 无法解析 SQL 或任意 API 时可保存草稿但不能发布。
- 上游升密后大屏立即升密，旧公开链接和越级共享重新评估。
- 前端只允许设置人工密级下限，系统有效密级只读并可查看来源。

## IT-07 生命周期

- 创建、存储、使用、共享、归档、临时销毁、永久销毁在统一工作台可追踪。
- 所有受控动作都必须审批通过后执行。
- 临时销毁进入回收站后不可查询/分享，可在保留期内一键恢复。
- 永久销毁需要双人复核，执行后业务数据不可恢复但审计和销毁证明仍可查。
- 外部源表不会被 DTS 反向 DROP。

## IT-08 统计与审计

- 按密级统计在用、共享、归档、回收站和永久销毁数量/数据量。
- declared/detected/inherited/manual raise 均有来源证据。
- 审计事件能串起申请、批准、执行、失败、重试和最终结果。

## IT-09 存量迁移

- dry-run 输出资产数、字段数、冲突数、缺密级数、缺血缘数和候选升密数。
- 迁移对同一批次重复执行幂等。
- 候选低于旧值时保留旧值并阻断该记录。
- 分批暂停/恢复不会丢失进度。
- 双读对账达到约定阈值后才冻结旧写入口。

## IT-10 真实交付

- Liquibase 在空库和现有升级库均成功。
- `dts-common`、`dts-platform`、`dts-ingestion`、`dts-metrics`、`dts-analytics` 相关 focused/组合测试通过。
- 前端 TypeScript、source-contract 和 production build 通过。
- 真实 PostgreSQL、dbt target、OpenLineage/Airflow、Excel/CSV、JDBC、API 接入闭环通过。
- Chrome 95 完成接入、资产台账、生命周期工作台、大屏编辑/发布/访问 smoke。
- `git diff --check` 和 GitNexus `detect_changes` 只包含 Sprint-72 预期范围。

## 证据目录

最终证据放入：

```text
it/evidence/
  focused/
  feature/
  migration/
  runtime/
  chrome95/
  classification-matrix/
  destruction-proof/
  go-no-go/
```

没有真实数据库、运行容器和浏览器证据时，Sprint 不得标记 DONE。

## 2026-07-26 统一验证结论

编码、focused/组合测试、空库/升级库 Liquibase、资产台账、治理详情、密级事实、生命周期工作台、
迁移 dry-run 和大屏有效密级列表已形成证据。新增隔离集成测试已证明：

- JDBC 字段密级导入并按字段最高密级提升资产密级。
- column 从所属 table/dataset 继承存量密级。
- 临时销毁、恢复、双人永久销毁、销毁证明留存及外部源表不反向 DROP。
- Excel/CSV、dbt、OpenLineage/Airflow 原生适配器的密级参数和传播契约。
- 人员密级、旧链接、缓存、导出和管理员旁路的服务端权限矩阵。

生产发布当前仍为 No-Go：

- 现网 dry-run 8991 条中 8049 条阻断，双读差异返回 1000 条；该结果产生于最终继承逻辑部署前。
- 按本轮约束未重建/发布容器，未重新执行生产 dry-run、apply 或冻结旧写入口。
- 部署最终源码后的 dbt/OpenLineage/Airflow、Excel/CSV 外部运行链和三档真实人员账号矩阵待补证。
- 大屏编辑、发布、公开访问的完整 Chrome 95 用户旅程待补证。
