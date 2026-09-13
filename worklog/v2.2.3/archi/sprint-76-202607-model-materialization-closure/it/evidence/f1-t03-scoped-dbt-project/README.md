# F1/T03 Candidate scoped dbt project 证据

**日期**：2026-07-27  
**范围**：candidate artifact overlay、workspace dependency 合并、bundle 幂等、
资源限制、active marker 与运行目录清理。

## RED

1. 旧 `prepare(selector)` 只能从 `ModelingSqlModel`/workspace 查 node，无法消费普通
   lifecycle artifact。
2. 首次真实运行把 `target` 写入 immutable candidate project，重放时 bundle checksum
   发现污染并拒绝复用。
3. 改为非 root UID 后，dbt 镜像默认 `/opt/dbt-logs/dbt.log` 不可写，真实 compile
   退出码为 2。

## GREEN

- `prepareCandidate(entries)` 从 current model/implementation revision、checksum 与
  artifact 构造 deterministic bundle；重复 prepare 返回相同 path/checksum。
- 既有 ODS sources、macros、packages、workspace ref 依赖被合并；依赖内容改变时产生
  新 bundle checksum。
- conflict、missing selected node/ref、stale checksum、path traversal、单文件/总量超限、
  重复 ModelSpec/selector 与 dependency cycle 均稳定 fail-closed。
- `.dts-active` 在运行期保护目录，显式 release 后才允许 TTL/count cleanup。
- 真实 dbt 以 `1000:1000` 执行；`target-path`、`DBT_LOG_PATH` 均位于独立
  `.dts-runtime/<run>/`，最终由平台进程完整删除。immutable candidate 不再被运行产物
  污染。
- Mockito `verifyNoInteractions(ModelingSqlModelRepository)` 证明普通制品没有创建平行
  SQL model owner。

## 生产边界

测试 UID/GID 只反映当前验收环境。F2/T04 必须由服务端生成 task-scoped runtime/profile
lease，并把 UID/GID、固定 host root、0700/0600、只读 profile mount 与清理策略写入
共享 Airflow task factory；不得把测试常量直接当生产配置。

本证据关闭 F1/T03，不证明 durable pipeline run、Airflow dispatch 或 relation probe
已经完成。
