# v2.2.1 P0 回归清单（初版）

## 目标
- 覆盖 `V221-P0-001/002/003` 的最小闭环：
  - 全量语义一致（Excel/源库）
  - 一键生成模型批量不中断
  - 关键接口与可见性回归

## 执行项
- [x] `QueryDataset` 部门匹配改为归一化匹配（避免 `owner_dept` 精确匹配误伤）
- [x] `QueryDataset`/`BI Link` 全局管理范围对齐到 `CATALOG_MAINTAINERS`
- [x] 一键生成模型（`generate-from-ods`）单映射失败不再中断整批
- [x] `Airflow DAG 404` 二段等待重试（`dagNotFoundRetryWaitSeconds`）
- [x] Airflow 任务日志/实例查询 `404` 降级为 `debug`（减少误告警）
- [x] 执行链路空值加固：无效 Addax 容器路径返回可读错误；执行成功审计元数据改为 null-safe 组装
- [x] 单元测试：`QueryDatasetServiceTest`（2 cases）
- [x] 单元测试：`AirflowAdapterTest`（3 cases）
- [x] 单元测试：`AddaxJobServiceTest` 新增全量语义用例（文件链路 DROP+CREATE、源库链路 TRUNCATE）
- [x] 单元测试：`IngestionTaskFullRefreshExecutionTest`（non-file 执行调用 `TargetTableProvisioner`; file 执行不调用）
- [x] 编译：`mvn -f source/dts-platform/pom.xml -DskipTests compile`
- [x] 编译：`mvn -f source/dts-ingestion/pom.xml -DskipTests compile`

## 待现场验证（legacy/normal/dev）
- [ ] Excel 全量：重复执行后确认目标表结构与数据仅来自最新输入
- [ ] 源库全量：重复执行后确认目标表被重建，历史脏列不残留
- [ ] DAG ready：创建后立即执行，触发等待时间在可接受范围
- [ ] 日志可读：平台“查看日志”与 Airflow 实际任务实例一致，不出现 404 误判

## 备注
- 当前代码语义：
  - 源库全量：由 `TargetTableProvisioner` 先 `DROP + CREATE`，再执行 Addax 导入。
  - 文件全量：由 Addax `preSql` 执行 `DROP + CREATE`，再导入。
- 新增配置：
  - `dts.airflow.dag-trigger-retry-seconds`
  - `dts.airflow.dag-not-found-retry-wait-seconds`


## 自动回填（来自 platform/raw/env-matrix.csv）
- 生成时间(UTC)：20260213T150447Z
- 样本总任务（按 mode+arch 最新样本聚合）：3（成功 2 / 失败 1）
- DAG 404 总数：0
- TaskLog 404 总数：0
- [x] DAG ready：自动样本中未出现 DAG 404
- [x] 日志可读：自动样本中未出现 TaskLog 404
- 说明：Excel/源库全量语义（C01/C02）与 ODS 一键生成（C05）仍需现场业务回归。
