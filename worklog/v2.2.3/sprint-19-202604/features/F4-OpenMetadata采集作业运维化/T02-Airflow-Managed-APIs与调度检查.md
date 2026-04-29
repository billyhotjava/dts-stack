# T02: Airflow Managed APIs 与调度检查

**优先级**: P1  
**状态**: READY  
**依赖**: T01

## 目标

确认 OpenMetadata managed APIs 与 Airflow 调度相关依赖在当前镜像和运行时中可用。

## 范围

- 检查 Airflow 依赖中的 `openmetadata-managed-apis` 和 `openmetadata-ingestion`。
- 验证 pipeline trigger 所需的 endpoint、DAG 或内部 API。
- 明确 one-shot ingestion 与 Airflow pipeline 的边界。
- 记录不启用 Airflow-managed pipeline 时的降级策略。

## 完成标准

- [ ] 采集触发路径不会依赖不存在的 Airflow API。
- [ ] one-shot 与 scheduled ingestion 的职责清晰。
- [ ] 运行时缺依赖时有明确诊断。
- [ ] runbook 包含调度检查步骤。
