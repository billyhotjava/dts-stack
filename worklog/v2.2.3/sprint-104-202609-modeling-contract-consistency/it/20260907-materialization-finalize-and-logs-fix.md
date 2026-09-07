# 物化收尾顺序与日志入口修复

日期：2026-09-07。源码基线：19c7518b4。范围：项目任务快照明细 v2 r4 构建失败，以及执行日志定位。

## 运行证据（只读）

- ModelSpec：`6c632178-50b1-4d03-a268-7126ed2c54c1`。
- Candidate：`6eef96db-5f6f-484c-80ac-309e5b53384d`，BUILD_FAILED。
- 最近一次 dispatch / run group：`2b1e3d22-d180-35fd-bda2-52b47f4ba981`，attempt 4。
- DAG：`dts_release_build_postgres_primary`。
- Airflow run：`dts_rc_6eef96db5f6f484c80ac309e5b53384d_a4`。
- 12:28:24，`dbt_build` 任务 SUCCESS；数据库内 pipeline 为 DBT_SUCCEEDED，物理表 observation 的 relation_exists / verified 均为 true。前四次尝试均有相同的数据库状态组合。
- 12:28:26，`sync_manifest_and_probe` 的 POST 回调返回 HTTP 422。
- 12:28:28，`finalize_run` 收尾记录 `MODEL_DBT_AIRFLOW_UPSTREAM_FAILED`。
- 历史 Python 日志丢弃了 HTTP 响应体，因此无法从该日志还原当时的原始业务错误码。

## 源码原因与修正

`syncAndProbe` 原先先把 Candidate 转为 BUILT，再调用 `CandidateQualityAssetRegistrationService.ensureRegistered`。后者调用 `CandidatePublicationEvidenceRepository.requireCurrent`，其查询要求 dispatch.status = COMPLETED；而 COMPLETED 要到下一个 `finalize_run` 才写入。这个顺序必然无法满足首次成功构建的质量资产登记前置条件。

调整为：

1. sync-probe 保存构建结果、物理表 observation，核验成功后更新 pipeline 为 BUILT，Candidate 保持 BUILDING。
2. finalize 在一个事务中完成 dispatch、将 Candidate 转为 BUILT、登记质量检查资产、保存完成审计；失败时回滚该事务并记录具体错误。
3. 构建结果已保存后发生的错误不再重复执行只能更新待执行行的 recordDbtResults，避免错误记录事务再次失败。
4. 后续 FAILED 回调保留已有业务错误码，已为 BUILD_FAILED 的 Candidate 不重复执行带有旧幂等键的新版本迁移。重复成功回调也不重复迁移 Candidate。
5. 平台日志补充 run group、错误码和异常栈；Airflow HTTP 错误仅附加格式受限的业务错误码和 UUID 关联 ID，不打印响应消息、请求路径或凭据。

## 如何查看日志

前端更新后，进入 `/#/ops/logs`（日志查看），找到对应运行并点击“查看日志”。执行步骤可选：

| 页面标签 | Airflow task ID | 用途 |
| --- | --- | --- |
| 准备执行环境 | prepare_runtime | 配置及执行环境准备 |
| 执行模型构建 | dbt_build | 构建任务执行状态 |
| 同步结果与核验物理表 | sync_manifest_and_probe | 构建产物同步、目标表结构核验 |
| 完成构建与资产登记 | finalize_run | 任务完成、Candidate 状态、质量资产登记 |

此前日志页固定读取 `dbt_run`，与这套构建流程的 task ID 不匹配。本次同时修复实例页传入 taskId 后被忽略的问题，指定 runId 的链接会自动加载该行日志。

本次日志目录（可立即查看，无需部署新代码）：

```text
/opt/prod/s10/deploy/logs/airflow/dag_id=dts_release_build_postgres_primary/run_id=dts_rc_6eef96db5f6f484c80ac309e5b53384d_a4/
```

实际错误文件：`task_id=sync_manifest_and_probe/attempt=1.log`。
收尾文件：`task_id=finalize_run/attempt=1.log`。
平台关联日志：`/opt/prod/s10/deploy/logs/dts-platform/app.log`，新代码记录 `runGroup=<dispatch UUID>`。

`a4` 表示整次构建的第 4 次尝试；`attempt=1.log` 表示该次运行内具体步骤的第 1 次执行，二者不能混用。

## 检查与交付边界

- GitNexus：Java 和前端 owner upstream impact 为 LOW；Python 工厂未被索引收录，已对源码调用点作静态核对。
- 已完成 diff 静态检查、Python AST 解析、前端格式检查和本次改动审阅。
- 前端 Biome check 剩余 2 项原有 loadRecords effect 依赖告警；对 HEAD 临时副本核对原本有 5 项，未新增告警。
- 已同步回归用例：完成顺序、收尾错误码保留、已保存结果不重复写入、重复成功回调，以及 HTTP 日志敏感内容过滤。按用户要求未执行任何测试。
- 未编译、打包、构建镜像、重建容器或进行页面验收。
- 用户手动发布需同步同一 Git 提交的 dts-platform、dts-platform-webapp 和 `services/dts-airflow/extra/dts_runtime/dbt_task_factory.py`。Airflow extra 当前由部署目录只读挂载；按正式部署流程更新，禁止容器内补丁。
- 手动验收：更新后对原模型重新构建；确认四个 Airflow 步骤成功、Candidate BUILT、dispatch COMPLETED、pipeline BUILT；若存在其他业务门禁，使用步骤日志中的具体错误码及平台异常栈定位，不以数据库历史表存在作为本次交付成功证明。
