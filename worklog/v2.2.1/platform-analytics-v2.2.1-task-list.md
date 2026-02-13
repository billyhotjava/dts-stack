# DTS v2.2.1 未完成任务清单（承接 v2.2.0）

## 0. 范围
- 本清单仅包含：
  - v2.2.0 未完成项（`todo/doing`）
  - 本轮 Review 新增修复项（RV）

## 1. P0 收尾（必须）

### V221-P0-001 全量语义联调闭环（done-first-pass）
- 来源：`P0-API-002 (doing)`
- 目标：确认 Excel/源库两条链路在全量模式下均执行“删表重建”。
- 进展：新增 `IngestionTaskFullRefreshExecutionTest`，覆盖“源库全量执行触发 `TargetTableProvisioner` 且记录 droppedTables”与“文件全量执行跳过 `TargetTableProvisioner`（走 Addax preSql 建表）”。
- 验收：重复执行同任务后，目标表结构与数据仅由本次输入决定。

### V221-P0-002 模型来源非空约束完善（done-first-pass）
- 来源：`P0-DB-003 (todo)`
- 目标：彻底杜绝 `source_data_source_id` 为空写入。
- 进展：已补 `ModelingSqlModelServiceTest`，覆盖“请求/映射来源不可用时回退到 ODS 数据集来源”和“单映射无可用来源时跳过且不中断整批”。
- 验收：批量生成场景无空来源入库，失败可读且不中断其它项。

### V221-P0-003 P0 回归包执行（done-first-pass）
- 来源：`P0-QA-001~007 (todo)`
- 目标：完成 DAG ready、日志可读、ODS 列表刷新、前缀/scheme、一键生成联调、环境矩阵。
- 进展：已完成 `Airflow DAG 404 二段等待重试`、`日志接口 404 降噪`，并补齐执行链路空值加固（无效 Addax 容器路径给出可读错误，执行成功审计元数据改为 null-safe 组装）。已补现场矩阵模板 `worklog/v2.2.1/p0-regression-matrix.md`，待现场逐环境回填结果。
- 验收：形成回归报告（legacy/normal/dev × x86/ARM）。

## 2. P1 稳定性与性能

### V221-P1-001 用户管理性能优化闭环（done-first-pass）
- 来源：`P1-API-005`、`P1-UI-003`、`P1-DB-002`、`P1-QA-003`
- 目标：800~1000 用户查询稳定在目标时延。
- 进展：已完成“搜索空结果不触发全量 Keycloak 同步”和“列表角色聚合批量查询（消除 N+1）”第一轮改造。
- 验收：P95 < 1.5s，搜索/翻页无明显卡顿。

### V221-P1-002 增量审计与失败归因（done-first-pass）
- 来源：`P1-API-001~004`、`P1-DB-001`、`P1-QA-001/002`
- 目标：可筛选审计 + 标准化错误分类 + 重试策略。
- 进展：已完成第一轮失败归因增强（Airflow 失败同步时落库失败任务日志摘录；失败分类/建议写入执行审计；重试审计附带上一次失败分类与建议）。第二轮补齐执行记录结构化字段（`failure_category`/`failure_advice`）与执行历史筛选参数（`status` + `failureCategory`）。
- 验收：Top20 失败归类命中率 > 95%，审计查询性能达标。

### V221-P1-003 报表密级与权限回归（done-first-pass）
- 来源：`P1-API-006`、`P1-UI-004`、`P1-DB-003`、`P1-QA-004`
- 目标：密级可见性与角色权限一致。
- 进展：已补齐 `BiReportLinkService` 单测（密级过滤 + 角色匹配）与 `ReportsResource` Web 层权限回归（员工只读、维护者可创建）。
- 验收：无越权显示/访问。

## 3. P2 产品化闭环

### V221-P2-001 项目包导入（一个 ZIP 一个项目）（done）
- 来源：`P2-API-004 (done)、P2-UI-004 (done)、P2-QA-003 (done)`
- 目标：导入向导 + 冲突检测 + 幂等 + 审计。
- 进展：后端 `POST /api/modeling/sql-models/import-project` 与 `ModelingSqlProjectImportService` 已完成（manifest + SQL 分层目录自动识别，冲突策略 `skip/overwrite/fail`，支持 `dryRun` 预检与包指纹 `SHA-256`）；建模页导入向导已支持 `dry-run` 开关并展示导入明细、告警和包指纹。
- 验收：同包重复导入无脏数据，冲突提示可读。

### V221-P2-002 外部 LLM 产物导入兼容（done-first-pass）
- 来源：`P2-API-005 (done-first-pass)`
- 目标：支持分层模型与指标定义导入，适配离线交付。
- 进展：项目包导入已支持 `manifest/indicators.tsv`，可将指标定义（`code` + `expression_sql/sql_path`）转换为 ADS 模型导入；与 `skip/overwrite/fail` 冲突策略、`dryRun` 预检、导入明细共用同一流程。
- 验收：导入报告含成功/跳过/失败明细，能进入平台治理流。

### V221-P2-003 数据集管理页闭环（done-first-pass）
- 来源：`P2-UI-002 (doing)`、`P2-QA-001/002/004 (todo)`
- 目标：可视化管理数据集版本、发布与看板依赖。
- 进展：`QueryWorkbenchPage` 新增“查询数据集”页签，并补齐 `QueryDatasetManager` 组件（数据集列表筛选、版本新建/发布/归档、看板依赖列表、SQL 预览）。
- 验收：完成“查询 -> 数据集 -> 看板”端到端演示。

## 4. Review 新增修复项（RV）

### V221-RV-001 QueryDataset 部门匹配归一化（done-first-pass）
- 来源：`RV-001`
- 目标：修复 owner_dept 精确匹配导致的误不可见。
- 涉及文件：
  - `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/QueryDatasetService.java`
  - `source/dts-platform/src/main/java/com/yuzhi/dts/platform/repository/explore/QueryDatasetAssetRepository.java`
- 验收：部门编码变体下可见性稳定。

### V221-RV-002 维护者角色权限矩阵对齐（done-first-pass）
- 来源：`RV-002`
- 目标：确认并固化 `ADMIN/OP_ADMIN/INST_DATA_OWNER/DEPT_DATA_OWNER` 的 QueryDataset/BI Link 权限边界。
- 涉及文件：
  - `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/QueryDatasetService.java`
  - `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/visualization/BiReportLinkService.java`
- 验收：角色回归用例全部通过。

### V221-RV-003 P3 验证证据落盘（done-first-pass）
- 来源：`RV-003`
- 目标：将“已完成的 P3 能力”转化为可审计的测试证据。
- 进展：已新增 `worklog/v2.2.1/rv-003` 模板包（24h 稳定性记录、Addax/Airbyte 语义对照、跨项目隔离与血缘回归），支持现场统一回填。
- 交付：
  - 24h 稳定性执行记录
  - Addax/Airbyte 语义对照报告
  - 跨项目隔离与血缘影响分析回归报告

## 5. 里程碑建议
- M1（1周）：完成 V221-P0-001/002/003 + V221-RV-001。
- M2（1~2周）：完成 V221-P1-001/002/003 + V221-RV-002。
- M3（2周）：完成 V221-P2-001/002/003 + V221-RV-003。

## 6. 大屏设计器商用化专项（新增）

### V221-SD-PLAN-001 商用化任务分解（P0/P1/P2）（todo）
- 来源：大屏设计器商用化分析（对标 DataEase 等产品能力）
- 目标：形成可执行的任务分解、验收标准、里程碑、风险依赖，作为 2.2.1+ 迭代输入。
- 文档：`worklog/v2.2.1/BI/screen-designer-commercialization-p0-p2-breakdown.md`
- 说明：本项为规划落盘，不代表功能已交付；具体开发任务按 P0->P1->P2 拆分进入迭代。
