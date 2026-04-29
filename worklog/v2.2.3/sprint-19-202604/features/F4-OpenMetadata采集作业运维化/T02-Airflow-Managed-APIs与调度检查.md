# T02: Airflow Managed APIs 与调度检查

**优先级**: P1
**状态**: DONE
**依赖**: T01

## 目标

确认 OpenMetadata managed APIs 与 Airflow 调度相关依赖在当前镜像和运行时中可用。

## 范围

- 检查 Airflow 依赖中的 `openmetadata-managed-apis` 和 `openmetadata-ingestion`。
- 验证 pipeline trigger 所需的 endpoint、DAG 或内部 API。
- 明确 one-shot ingestion 与 Airflow pipeline 的边界。
- 记录不启用 Airflow-managed pipeline 时的降级策略。

## 完成标准

- [x] 采集触发路径不会依赖不存在的 Airflow API。
- [x] one-shot 与 scheduled ingestion 的职责清晰。
- [x] 运行时缺依赖时有明确诊断。
- [x] runbook 包含调度检查步骤。

## 验收记录（2026-04-29）

当前 compose 服务包含 `dts-airflow-webserver`、`dts-airflow-scheduler`、`dts-airflow-triggerer` 和 `dts-airflow-init`。运行时检查结果：

- `dts-airflow-webserver` 处于 `healthy`，scheduler/triggerer 处于 `Up`。
- `openmetadata-managed-apis=1.11.5.0` 和 `openmetadata-ingestion=1.11.5.0` 已安装在 Airflow 镜像中。
- `airflow dags list` 可列出当前 DAG，说明 Webserver 到元数据库的 Airflow 基线可用。

边界说明：

- 本 Sprint 的现场冒烟优先使用 `dts-openmetadata-ingestion` one-shot 容器执行 `run-postgres-ingestion.sh` 与 `run-dbt-ingestion.sh`。
- Airflow managed APIs 用于 OpenMetadata pipeline service 或已有 DAG 调度路径的运行时依赖校验，不作为 one-shot 冒烟的硬依赖。
- 若 Airflow 运行时缺少 `openmetadata-managed-apis` 或 `openmetadata-ingestion`，应先修复 `AIRFLOW_PIP_ADDITIONAL_REQUIREMENTS` 或镜像构建，再启用 OpenMetadata managed pipeline 调度。
