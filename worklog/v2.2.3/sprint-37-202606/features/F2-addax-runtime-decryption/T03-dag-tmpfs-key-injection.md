# T03: DAG 注入 tmpfs mount + TMPDIR + 密钥环境

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

`AirflowDagService` 生成的 Addax `DockerOperator` 增加 tmpfs 挂载、把 `TMPDIR` 指向它、注入解密密钥环境，使 runner 解密产物只在内存。

## TDD 测试先行（RED）

- 扩展 `AirflowDagServiceTest`（dts-ingestion 侧 DAG 生成单测）。
- 断言：渲染出的 DAG 文本，Addax `DockerOperator` 含 tmpfs 配置（如 `tmpfs={"/decrypted": "rw,size=512m,noexec"}` 或 `mounts=[Mount(target="/decrypted", type="tmpfs")]`）。
- 断言：容器 `environment` 含 `TMPDIR=/decrypted` 与 `DTS_INFRA_ENCRYPTION_KEY`（经 `build_addax_environment`）、`DTS_INFRA_KEY_VERSION`。
- 断言：tmpfs 带 `noexec`、容量上限，避免滥用。

## 技术设计（GREEN）

- 改 `AirflowDagService.java` 的 DockerOperator 模板（L507-524 与 L615-632 两处）：增加 tmpfs（DockerOperator `tmpfs` 参数）+ `TMPDIR` env 指向该 tmpfs。
- `build_addax_environment()`（L… 由 `buildAddaxEnvironment` 生成）注入 `DTS_INFRA_ENCRYPTION_KEY`/`DTS_INFRA_KEY_VERSION`（compose 已为 dts-ingestion 提供 L727-728，需同样传给 Addax 容器）。
- compose（`docker-compose-app.yml`）：去掉对 `uploads` 的 `chmod o+r`（L200-201 收敛为不含 uploads，或 uploads 单独 0700）。

## 影响范围

- `source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/AirflowDagService.java`（DAG 模板，改既有 symbol → `gitnexus_impact`）
- `docker-compose-app.yml`（Addax 启动环境经 dag；uploads 权限收敛）
- `source/dts-ingestion/src/test/java/.../AirflowDagServiceTest.java`

## 验证

- [ ] DAG 渲染含 tmpfs + TMPDIR + 密钥环境。
- [ ] tmpfs noexec + 容量限制。
- [ ] uploads 不再 world-readable。

## 完成标准

- [ ] runner 解密产物落在 tmpfs（内存），不落宿主机磁盘。
