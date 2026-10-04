# P2-01 dbt DevOps 能力包

- 优先级：P2
- 状态：done

## 范围

- 补齐 compile/test/docs build 与产物状态跟踪。

## 子任务

- 新增 API：`/dbt/compile`、`/dbt/test`、`/dbt/docs`。
- 统一 run artifact 状态与失败摘要（manifest/run_results）。
- 在 `DbtFileBrowserPage` 与建模页展示最近构建状态。

## 验收标准

- 支持一键 compile/test。
- 失败模型/测试可定位到具体节点。
- 产物同步状态可视化。

## 风险与回滚

- 风险：dbt 命令耗时长影响体验。
- 回滚：先提供异步触发 + 后台状态追踪，不阻塞页面。

## 已完成进展（2026-02-16）

- 后端 API 已补齐：
  - `POST /api/etl/dbt/compile`
  - `POST /api/etl/dbt/test`
  - `POST /api/etl/dbt/docs`
- `EtlResource` 触发链路统一到 `triggerDbtOperation`，并扩展 `operation` 为 `run/test/compile/docs/build`。
- `DbtDagService` 生成 DAG 时已支持 compile/test/docs/build 命令映射（docs 使用 `dbt docs generate`）。
- `DbtRunResultService` 增加运行产物摘要能力：
  - 解析 `target/run_results.json` + `target/manifest.json`
  - 输出总数/成功/失败/跳过
  - 输出失败节点明细（unique_id/name/resource_type/path/message/execution_time）
- `DbtArtifactSyncState` 增加 `latestRun` 快照，并在：
  - 手动同步（`/dbt/models/sync`）
  - 定时同步（`DbtArtifactSyncScheduler`）
  - 状态查询（`/dbt/sync/status`）
  三条路径中统一维护。
- 前端能力补齐：
  - `platformApi.ts` 增加 `triggerDbtCompile/triggerDbtTest/triggerDbtDocs`
  - `SqlModelingPage.tsx` 增加一键“编译/测试/文档”按钮，并在“编译日志”Tab展示最近构建状态与失败节点
  - `DbtFileBrowserPage.tsx` 增加快速触发 compile/test/docs 与最近构建状态提示

## 影响文件

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/EtlResource.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtDagService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtRunResultService.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtArtifactSyncState.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/etl/DbtArtifactSyncScheduler.java`
- `source/dts-platform-webapp/src/api/platformApi.ts`
- `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`
- `source/dts-platform-webapp/src/pages/modeling/DbtFileBrowserPage.tsx`

## 回归结果（2026-02-16）

- `mvn -f source/dts-platform/pom.xml -DskipTests compile`：通过
- `pnpm -C source/dts-platform-webapp build`：通过
