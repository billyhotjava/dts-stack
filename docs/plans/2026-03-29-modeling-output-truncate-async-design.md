# 逻辑建模清空产出表异步执行设计

## 背景

当前逻辑建模页的 `清空产出表` 仍由 `dts-platform` 在请求线程里直连目标数仓执行 `TRUNCATE`。这会把页面能力绑定到 platform JVM 的 JDBC 连通性上，和 `重建产出表`、`编译`、`测试`、`上线` 这类已经走 dbt / Airflow 的后台任务模型不一致。

现场已经证明这种设计不稳定：dbt 容器能连目标库，不代表 `dts-platform` 进程也能连；结果就是“重建能做、清空失败”，用户感知割裂。

## 目标

把 `清空产出表` 重构为与 `重建产出表` 一致的后台任务模式：

1. 前端点击后只提交任务，不再同步等待库操作完成
2. `dts-platform` 不再负责直连目标库执行 `TRUNCATE`
3. 真正的 relation 检查和 truncate 交由 dbt 容器通过 Airflow 执行
4. 页面统一通过任务状态和执行日志反馈结果

## 方案对比

### 方案 A：继续保留 platform 直连 JDBC

优点是改动小。缺点是架构继续不一致，现场仍然会被 platform 与 dbt 的网络差异卡住，不值得继续投资。

### 方案 B：推荐方案，改成 `dbt run-operation truncate_relation`

在 dbt workspace 中新增宏 `truncate_relation`，由 Airflow DAG 调用 `dbt run-operation truncate_relation --args ...` 执行 relation 检查和 `TRUNCATE`。

优点：

- 和现有 `build/docs/test` 一样走后台任务
- 复用 dbt 容器已有目标库连通性
- 不再要求 `dts-platform` 自己持有数据库驱动和网络路径

缺点：

- 需要扩展 DAG 生成脚本和 workspace bootstrap
- 页面从“同步执行”切到“提交任务后查看结果”

### 方案 C：把清空逻辑塞进 `build --full-refresh`

不推荐。`full-refresh` 的语义是重建，不是清空数据，和当前按钮含义不一致。

## 推荐设计

采用方案 B。

### 后端

- `DbtOutputRelationService` 增加 `prepareTruncate()`，只负责解析模型、selector、qualified name 等元数据
- `EtlResource` 的 `POST /api/etl/dbt/output/truncate` 改为异步提交 Airflow 任务
- Airflow `conf` 使用：
  - `operation=run-operation`
  - `macro_name=truncate_relation`
  - `macro_args={ database_name, schema_name, identifier }`
  - `models=<selector>` 仅用于运行记录归因

### DAG

- `DbtDagService` 生成的 DAG Bash 脚本新增 `run-operation` 分支
- 当 `operation=run-operation` 时，执行：
  - `dbt run-operation <macro_name> --args <macro_args> --project-dir ... --profiles-dir ... --target ...`

### dbt workspace

- `services/dts-dbt/macros/` 新增 `truncate_relation.sql`
- `DbtConfigService` bootstrap 时同步写入该宏，确保运行时 workspace 总能得到它
- 宏内部逻辑：
  - 使用 `adapter.get_relation()` 检查 relation
  - relation 不存在：记录日志并安全跳过
  - relation 是视图：显式抛错，提示改用重建
  - relation 是表：执行 `TRUNCATE TABLE`

### 前端

- `清空产出表` 弹窗不再请求 `/api/etl/dbt/output` 做 platform 侧 JDBC 检查
- 弹窗展示基于模型元数据的预览，并明确提示“实际检查在后台任务中完成”
- 确认后调用异步提交接口，页面进入执行日志/运行状态视图

## 验证策略

- 后端单测覆盖：
  - `prepareTruncate()` 不依赖 JDBC 连接
  - truncate 接口提交的 Airflow conf 正确
  - DAG 源码包含 `run-operation` 分支
  - workspace bootstrap 包含 `truncate_relation.sql`
- 前端测试覆盖：
  - 清空产出表预览改成无预检模式
  - 点击确认后按异步提交语义更新页面状态
