# dbt 发布门禁契约

## 目标

Sprint-31 F4 将 dbt 发布从“确认 warning 后仍可上线”升级为企业级 release gate：

- blockers 必须阻断发布。
- warnings 可以由工程师确认后继续。
- 发布请求必须带出构建证据、Git 元数据和 dbt run_results 路径。
- schema.yml 合约缺失必须可阻断。

## 阻断规则

| 检查项 | strict/prod 行为 | 非 strict 行为 |
|---|---|---|
| 缺少构建证据 | BLOCK | WARN |
| 最近构建失败 | BLOCK | BLOCK |
| 最近命令不是 compile/test/build 且无兼容证据 | BLOCK | BLOCK |
| 构建证据超过 24h | BLOCK | WARN |
| selector 与构建命令不一致 | BLOCK | WARN |
| schema.yml 缺测试 | BLOCK | BLOCK |
| 缺 `expected_data_type` | BLOCK | BLOCK |
| 缺 `owner` | BLOCK | BLOCK |
| 缺 `classification` | BLOCK | BLOCK |

## 发布证据

发布触发 Airflow 时写入 DAG conf：

```json
{
  "operation": "build",
  "models": "...",
  "dagSelector": "...",
  "gitRef": "...",
  "commitSha": "...",
  "buildInvocationId": "..."
}
```

发布接口返回：

- `blockers`
- `warnings`
- `qualityGate`
- `releaseGate`
- `buildEvidence.invocationId`
- `buildEvidence.runResultsPath`

## 验收

- release/quality blockers 不允许通过 `confirmWarnings=true` 绕过。
- warnings 仍保留确认流程，避免非生产提示阻断工作。
- 工程师能从返回体看到阻断原因和构建证据。
