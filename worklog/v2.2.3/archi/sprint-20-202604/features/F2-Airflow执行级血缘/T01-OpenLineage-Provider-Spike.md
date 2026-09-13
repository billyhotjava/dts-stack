# T01: OpenLineage Provider Spike & 版本矩阵

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标

在引入代码之前，先做一次 spike，确认 `apache-airflow-providers-openlineage` 与现网 Airflow 版本兼容，并明确事件 payload 格式与传输协议（HTTP / Kafka），避免后续改造方向反复。

## 技术设计

### 调研项

| 项 | 内容 |
|---|---|
| Airflow 版本 | 读 `dts-airflow-om/` 与 `imgversion.conf`，确认现网用的 Airflow X.Y.Z |
| 兼容矩阵 | 在 [openlineage docs](https://openlineage.io/docs/integrations/airflow/) 查对应 provider 包版本 |
| 传输 | HTTP（`OPENLINEAGE_URL`）vs Kafka，本项目用 HTTP 直接打到 dts-platform |
| Auth | 是否需要 API Key / Bearer Token；platform 端如何鉴权 |
| Payload 字段 | RunEvent (eventType, run.runId, job.namespace/name, inputs[], outputs[], facets) |
| facets | column-level lineage facet（schema/columnLineage）能否原生采到 |
| Inceptor 兼容 | DockerOperator + Addax 镜像下，OpenLineage Hook 是否能识别（关键：DockerOperator 默认不抽 lineage） |

### 输出

`worklog/v2.2.3/sprint-20-202604/assets/openlineage-spike.md`，包含：

1. 推荐版本组合（Airflow X + provider Y）
2. 推荐事件传输（HTTP）
3. 一份样例 RunEvent payload
4. 已知坑：DockerOperator/KubernetesPodOperator 的限制和绕过方案
5. 决策：如果 DockerOperator 不抽，改用 PythonOperator wrapper 显式发 OpenLineage 事件

### POC

在本地 docker-compose 起一个最小 Airflow + 一个发往 wiremock 的 OpenLineage URL，跑一个 demo DAG，看到至少 START / COMPLETE 两个事件。

## 影响范围

仅文档与本地 POC，不改动现网代码。

## 验证

- [ ] 文档 `assets/openlineage-spike.md` 已完成
- [ ] POC 可演示：本地 Airflow 跑一次 DAG，wiremock 收到 OpenLineage 事件
- [ ] 与团队内对齐选定方案（HTTP / 显式 emit / 隐式 hook）

## 完成标准

- [ ] 决定版本组合并写到 spike 文档
- [ ] DockerOperator 限制有明确绕过方案
- [ ] 团队会议中确认本方向，T02、T03 可启动
