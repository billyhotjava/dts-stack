# Sprint-4：建模模块稳定性与体验优化

## 目标

解决建模模块 4 个核心问题：网络容错、上线流程、重建表安全、界面质量。

---

## S4-001：findDag 改为精确查询 + DAG 等待时间修复（P0） ✅

**根因：** `findDag()` 用 `listDags(200)` 扫描全部 DAG 而非按 ID 查询；`dagReadyWaitSeconds` 默认 0。

**修复：**
- `AirflowClient.java`：新增 `getDag(dagId)` 方法，直接 `GET /api/v1/dags/{dagId}`，O(1) 精确查询
- `EtlResource.java`：`findDag()` 改用 `getDag(dagId)`
- `AirflowClient.java`：`triggerDag()` 抛出 RuntimeException 携带 Airflow 错误详情
- `AirflowProperties.java`：`dagReadyWaitSeconds` 默认值从 0 改为 30

## S4-002：重建表安全顺序（P0） ✅

**根因：** 先 DROP TABLE 再触发 build，build 失败则表永久丢失。

**修复：**
- `DbtOutputRelationService.prepareRebuild()`：不再执行 DROP SQL，仅返回元数据
- `EtlResource.java`：rebuild 端点改为传 `fullRefresh=true`，由 dbt `--full-refresh` 原子处理
- `DbtDagService.java`：DAG 模板读取 `dag_run.conf.full_refresh`，为 true 时追加 `--full-refresh`
- 前端提示更新："安全重建，构建失败时保留原表"

## S4-003：网络容错 — 超时缩短 + 页面级重试（P0） ✅

**根因：** axios 50s 超时 + 401 重试链可达 150s + 无页面级错误恢复。

**修复：**
- `apiClient.ts`：全局超时从 50s 降到 15s
- `SqlModelingPage.tsx`：新增 `pageLoadError` 状态 + 重试 Alert 横幅
- 三个关键加载器 (loadConfig/loadModels/loadSpaces) catch 中设置 `pageLoadError=true`
- 重试按钮调用 `loadInitialData()` 重新加载所有数据
- `loadSyncStatus` 不再静默失败，改为 `toast.error` 提示

## S4-004：Airflow 错误透传（P1） ✅

**根因：** `AirflowClient` 吞掉所有异常，用户只看到通用"触发失败"。

**修复：**
- `EtlResource.triggerAirflowDagOrThrow()`：捕获 RuntimeException，转为 `ResponseStatusException(502)` 保留原始消息
- `checkDagReady()`：错误消息包含 dagId + 等待时间建议
- `waitForDagRegistration()`：超时/中断消息包含 dagId
- `RollbackCascadeService`：best-effort 调用，捕获异常不崩溃
- `OpsService`：失败时设置 backfill 状态为 FAILED 并记录原因
- 前端无需改动（已有 `toast.error(err.message)` 机制）

## S4-005：建模页面 UI 优化（P2） ✅

**修复：**
- 左侧模型树：`spacesLoading` / `modelsLoading` 时显示 Skeleton 占位
- 编辑器区域：未选模型时显示引导文案；加载中显示代码行 Skeleton
- 底部 tab：preview/execLog/columns 加载中显示 Skeleton
- 页面头部：增加模型数量 Badge + 最近同步时间
- 专题诊断区域加载 Skeleton

---

## 改动文件汇总

### 后端（dts-platform）

| 文件 | 改动 |
|------|------|
| `AirflowClient.java` | 新增 `getDag()`；`triggerDag()` 抛异常 |
| `AirflowProperties.java` | `dagReadyWaitSeconds` 默认 0→30 |
| `EtlResource.java` | `findDag` 精确查询；rebuild 传 fullRefresh；错误消息含 dagId |
| `DbtOutputRelationService.java` | `prepareRebuild` 不再 DROP |
| `DbtDagService.java` | DAG 模板支持 `--full-refresh` |
| `RollbackCascadeService.java` | 捕获 triggerDag 异常 |
| `OpsService.java` | 捕获 triggerDag 异常，记录失败状态 |

### 前端（dts-platform-webapp）

| 文件 | 改动 |
|------|------|
| `apiClient.ts` | 超时 50s→15s |
| `SqlModelingPage.tsx` | 页面级错误+重试；Skeleton 占位；重建提示优化；头部 Badge |
