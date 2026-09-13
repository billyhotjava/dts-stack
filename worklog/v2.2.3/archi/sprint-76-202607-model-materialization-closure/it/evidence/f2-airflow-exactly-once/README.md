# F2/T02 真实 Airflow exactly-once effect 证据

**日期**：2026-07-28  
**结论**：PASS；解除 F4 的 F2/T02 前置阻断，不代表 F5/F7/F6 完成。

## 场景

目标 DAG：`dts_release_build_postgres_primary`  
故障注入 DagRun：
`manual__s76_fault_20260728T023408_7022d7ad`

1. 代理将 trigger 请求完整提交给真实 Airflow；
2. Airflow 返回接受后，代理故意不向客户端返回响应，客户端观察到 timeout；
3. 以同一 `dagId + dagRunId + durable conf` 直接重放；
4. 重建 dts-platform 单服务，再次读取同一 DagRun。

注入使用无效 runtime token，使 prepare fail-closed，不启动 dbt、不写目标表，
也不签发 profile lease。

## 结果

| 检查 | 结果 |
|---|---|
| 客户端观察到 timeout | PASS |
| Airflow 已在 timeout 前接受 | PASS |
| durable conf 精确匹配 | PASS |
| 同 runId 重放 | HTTP 409 |
| Airflow 中同 runId 数量 | 1 |
| Platform 重启后同 runId 数量 | 1 |
| Platform 重启后 durable conf | 完整、未漂移 |
| prepare/finalize | failed |
| dbt_build/sync_probe | upstream_failed |
| DagRun 最终状态 | failed |
| host tmpfs profile root | 0700、注入后为空 |

## 同轮发现与修复

首次部署的 thin DAG 可直接 import，但 Airflow DAG discovery safe mode 因源码
不含 `airflow` 标记而跳过，表现为“无 import error、DAG 不注册”。renderer
现写入无执行语义的 `# airflow DAG discovery marker`；真实 Airflow 随后只解析
出一个目标 DAG。

GitNexus 对 `buildManagedReleaseDagSource` 判定 HIGH（直接影响 release build
renderer、间接影响 dispatch）。修改未改变 dagId、任务图、checksum 输入或运行
参数；`DbtDagServiceTest` RED→GREEN，并与
`ModelMaterializationDispatchServiceTest` 合并通过。
