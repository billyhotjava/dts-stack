# DTS v2.2.0 P0 Issue 清单（可直接开发）

## 1. 使用说明
- 目标：把 `platform-elt-task-list.md` 的 `P0` 拆成可直接创建工单的最小单元。
- 结构：按四类拆分 `接口/API`、`前端/UI`、`数据库/DDL`、`回归/测试`。
- 状态字段：`todo` / `doing` / `done`。

## 2. 接口/API 类

### P0-API-001 任务执行前 DAG 就绪等待
- 状态：`done`
- 完成：2026-02-12
- 说明：拆分 DAG 就绪等待与触发重试窗口，新增 `dagTriggerRetrySeconds`（默认 12s），失败返回 `AIRFLOW_DAG_NOT_READY_TIMEOUT`。
- 模块：`source/dts-ingestion`
- 目标：解决“新建任务后立即执行 DAG 404”。
- 改动点：
  - 在执行前新增 `waitDagReady(dagId, timeout)`。
  - 轮询 Airflow DAG 列表或 DAG 详情，识别 ready 后再 trigger。
  - 超时返回结构化错误码：`AIRFLOW_DAG_NOT_READY_TIMEOUT`。
- 验收：
  - 新建任务后 10 秒内可执行。
  - 不再出现首轮 trigger 404。

### P0-API-002 全量模式语义统一
- 状态：`doing`
- 说明：代码已对齐全量删表重建（文件链路由 Addax preSql 执行 DROP+CREATE；源库链路由 TargetTableProvisioner 执行 DROP+CREATE），待联调回归确认。
- 模块：`source/dts-ingestion`
- 目标：全量模式统一为“删表重建再导入”。
- 改动点：
  - Excel 入湖与源库入湖都按 `DROP TABLE IF EXISTS + CREATE TABLE + LOAD`。
  - 任务配置中明确 `loadMode=FULL_REPLACE`。
- 验收：
  - 二次执行同一任务时，目标表结构与数据完全由本次输入决定。

### P0-API-003 Addax 表名与 schema 规范化
- 状态：`done`
- 完成：2026-02-12
- 说明：多表任务拆分后按目标表精准裁剪 `preSql/postSql`，避免子作业携带其他表 SQL；目标表名解析继续保持 `schema.table` / `table` 规范与前缀一致。
- 模块：`source/dts-ingestion`
- 目标：修复 `db.schema.table` 错拼与 prefix 丢失。
- 改动点：
  - 明确 writer 表名只接受 `schema.table` 或 `table`。
  - 源 schema 与目标 schema 分离处理。
  - prefix 在拆分子 job 后保持一致。
- 验收：
  - 预览 JSON 与实际执行 JSON 的目标表名一致。

### P0-API-004 文件入湖附加字段策略
- 状态：`done`
- 完成：2026-02-12
- 说明：文件来源字段提取增强（支持对象/数组结构），`source_system` 优先取上传文件名，缺省降级为 `uploaded_file`；`import_time` 继续统一 Asia/Shanghai 表达式。
- 模块：`source/dts-ingestion`
- 目标：统一 `source_system` 与 `import_time` 写入。
- 改动点：
  - `source_system` 使用上传文件名（非 hardcode）。
  - `import_time` 使用 `Asia/Shanghai`，避免 DDL 默认表达式语法冲突。
  - 对建表 SQL 与 postSql 使用同一时间策略。
- 验收：
  - 文件导入可稳定写入两列，无 SQL 语法错误。

### P0-API-005 任务日志查询映射修复
- 状态：`done`
- 完成：2026-02-12
- 说明：日志查询按 TaskInstance 的 try_number 自动回退尝试，减少 TaskInstance not found 误判（支持 taskTryHints）。
- 模块：`source/dts-ingestion`
- 目标：解决“任务成功但查看日志 404 TaskInstance not found”。
- 改动点：
  - 统一 `dagRunId`、`taskId`、`tryNumber` 映射。
  - 对不存在实例提供明确文案，不回写失败状态污染执行历史。
- 验收：
  - 已成功执行任务的日志页面可稳定打开。

### P0-API-006 逻辑建模 ODS 来源映射修复
- 状态：`done`
- 完成：2026-02-12
- 说明：来源解析链路已按 dataset source / mapping source / fallback source 兜底；并新增“仅返回仍存在且启用的 ODS 数据集”过滤，避免历史脏映射继续出现在下拉
- 模块：`source/dts-platform`
- 目标：修复“来源数据源有值但 ODS 列表空”。
- 改动点：
  - ODS 列表改为按最新采集快照读取。
  - 来源数据源 id/name 双字段返回，前端只显示 name。
- 验收：
  - 选择来源后 ODS 列表立即出现最新表。

### P0-API-007 一键生成模型空来源约束修复
- 状态：`done`
- 完成：2026-02-12
- 说明：模型写入前强制解析可用来源；无可用来源直接返回可读错误，不再触发 source_data_source_id 空值入库
- 模块：`source/dts-platform`
- 目标：修复 `source_data_source_id` 为空导致批量 insert 失败。
- 改动点：
  - 生成前强校验来源数据源。
  - 不满足条件时拒绝入库并给出可读错误。
  - 生成 SQL 模型时填充合法 `source_data_source_id`。
- 验收：
  - 一键生成不再出现该 `not-null constraint` 异常。

## 3. 前端/UI 类

### P0-UI-001 数据采集执行异步化
- 状态：`done`
- 完成：2026-02-12
- 说明：任务创建页、列表页、详情页、执行历史页均统一为“异步提交 + 进度弹层 + 自动轮询”；轮询间隔支持后端回传 `pollIntervalMs` 并由前端环境变量兜底。
- 模块：`source/dts-platform-webapp`
- 目标：任务启动后界面不阻塞。
- 改动点：
  - 点击执行后立即返回“已提交”。
  - 增加进度条与分阶段状态：排队/执行中/成功/失败。
  - 轮询间隔可配置，失败自动停止。
- 验收：
  - 页面无卡死，执行状态 3 秒内可见。

### P0-UI-002 来源数据源下拉展示优化
- 状态：`done`
- 完成：2026-02-12
- 说明：来源下拉仅展示“当前有 ODS 映射”的来源，label 优先展示来源名称，避免显示 UUID/ID 片段
- 模块：`source/dts-platform-webapp`
- 目标：下拉显示数据源名称，不显示 ID 或 ID 片段。
- 改动点：
  - label 使用后端返回 `name`。
  - value 使用 `id`，界面不透出。
- 验收：
  - 所有下拉项均为可读名称。

### P0-UI-003 ODS 表下拉刷新机制
- 状态：`done`
- 完成：2026-02-12
- 说明：一键生成弹窗在来源数据源切换时按 sourceDataSourceId 重新请求 ODS 映射，清理并更新 mappingIds 候选。
- 模块：`source/dts-platform-webapp`
- 目标：切换来源数据源时，ODS 下拉立即刷新。
- 改动点：
  - 监听来源变化，清理旧缓存并重查。
  - 空状态给出可操作提示（刷新采集/前往采集页面）。
- 验收：
  - 不再出现来源变化后仍显示历史 ODS 列表。

### P0-UI-004 ODS 表名展示规范
- 状态：`done`
- 完成：2026-02-12
- 说明：ODS 显示统一为 schema.table，且去除重复前缀，避免出现 ods.table.unknown 这类格式
- 模块：`source/dts-platform-webapp`
- 目标：统一展示格式为 `schema.table`。
- 改动点：
  - 格式化函数统一处理 `schema/table`。
  - 禁止出现 `ods.table.unknown`。
- 验收：
  - 弹窗与列表展示一致。

### P0-UI-005 一键生成结果反馈增强
- 状态：`done`
- 完成：2026-02-12
- 说明：一键生成结果弹窗新增“跳过原因统计”与“全部跳过/仅失败项”分视图，便于快速定位失败映射
- 模块：`source/dts-platform-webapp`
- 目标：新增/更新/跳过原因可读化。
- 改动点：
  - 逐条展示跳过原因。
  - 提供“仅看失败项”过滤。
- 验收：
  - 用户可直接定位失败数据源或映射问题。

## 4. 数据库/DDL 类

### P0-DB-001 全量替换行为审计字段补充
- 状态：`done`
- 完成：2026-02-12
- 说明：执行记录表新增 `replace_mode` 与 `dropped_tables`，执行前根据任务同步模式写入替换策略，并从 Addax 作业解析目标表列表用于审计追溯。
- 模块：`source/dts-ingestion` Liquibase
- 目标：记录 full replace 行为（删除/重建）。
- 改动点：
  - 新增执行审计字段：`replace_mode`、`dropped_tables`。
- 验收：
  - 可追溯每次全量是否执行删表。

### P0-DB-002 采集快照存储与失效策略
- 状态：`done`
- 完成：2026-02-12
- 说明：`catalog_dataset` 新增 `snapshot_time`，并在 JDBC/Postgres/Inceptor/ODS 映射同步中写入快照时间；Postgres 同步补齐 stale dataset 清理，JDBC 默认开启 stale cleanup（可通过 `catalogCleanupStale=false` 显式关闭）
- 模块：`source/dts-platform` Liquibase
- 目标：ODS 列表来源可追溯且可刷新。
- 改动点：
  - 采集结果表增加 `snapshot_time`、`source_id`、`schema_name`、`table_name` 索引。
  - 支持按来源覆盖旧快照。
- 验收：
  - 删除源表后重采集，ODS 列表可同步更新。

### P0-DB-003 模型表来源约束与兜底校验
- 状态：`todo`
- 模块：`source/dts-platform` Liquibase
- 目标：避免模型写入无来源。
- 改动点：
  - 保持 `source_data_source_id` 非空。
  - 增加写入前 service 层校验与异常码。
- 验收：
  - 批量生成不因单条空来源中断整批。

## 5. 回归/测试类

### P0-QA-001 任务创建后立即执行稳定性
- 状态：`todo`
- 场景：新建任务后 1 秒内点击执行。
- 验收：
  - 100 次压测，DAG 404 比例 = 0。

### P0-QA-002 全量语义一致性
- 状态：`todo`
- 场景：Excel/源库各执行两轮全量。
- 验收：
  - 第二轮前历史数据不残留。

### P0-QA-003 表名前缀与 schema 用例
- 状态：`todo`
- 场景：`ods_erp_` 前缀 + 多 schema 源库。
- 验收：
  - 目标表名、preSql、postSql 全一致。

### P0-QA-004 日志可读性
- 状态：`todo`
- 场景：成功/失败任务分别查看日志。
- 验收：
  - 不出现“TaskInstance not found”误判。

### P0-QA-005 逻辑建模联动
- 状态：`todo`
- 场景：元数据采集后直接进入一键生成弹窗。
- 验收：
  - 来源下拉有名称，ODS 下拉有最新表。

### P0-QA-006 环境矩阵回归
- 状态：`todo`
- 场景：`legacy`、`normal`、`dev` x `x86_64`、`aarch64(鲲鹏+麒麟)`。
- 验收：
  - 核心流程全部通过并留存日志。

## 6. 实施顺序（建议）
- Step 1：`P0-API-001/002/003/004/005` + `P0-DB-001`。
- Step 2：`P0-API-006/007` + `P0-DB-002/003`。
- Step 3：`P0-UI-001~005`。
- Step 4：`P0-QA-001~006` 全量回归。

## 7. Definition of Done
- 每个 issue 有：代码变更、日志样例、回归截图/输出。
- 关键流程（新建任务->执行->查看日志->一键生成模型）可一次走通。
- 双模式双架构通过：`legacy/normal/dev` 与 `x86/ARM`。
