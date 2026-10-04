# Sprint-32 dbt 候选 Artifact 与发布门禁证据

## 状态

**READY**：`dts-metrics` 已能生成候选 artifact；platform 服务鉴权已允许 `dts-metrics` 调用 dbt release gate 和 release submit。真实发布调用待最终统一 IT。

## 覆盖范围

- `POST /api/metrics/packs/preview-artifacts` 返回：
  - `dbtModelSql`
  - `schemaYml`
  - `metricDoc`
- 候选 artifact 明确标注必须由 platform/dbt release gate 审核发布。
- platform allowlist 允许 `dts-metrics` 调用：
  - `POST /api/etl/dbt/release-gate/check`
  - `POST /api/etl/dbt/release/submit`
- platform 仍负责 dbt 发布门禁和审计动作，不把发布事实源转移给 metrics。

## 待执行命令

```bash
curl -sS -X POST http://127.0.0.1:18082/api/metrics/packs/preview-artifacts \
  -H 'Content-Type: text/yaml' \
  --data-binary @worklog/v2.2.3/sprint-32-202605/it/fixtures/inline-flower-rental-pack.yml
```

```bash
curl -sS -X POST http://127.0.0.1:18082/api/etl/dbt/release-gate/check \
  -H 'Content-Type: application/json' \
  -H 'X-DTS-Service: dts-metrics' \
  -H 'X-DTS-Service-Token: <token>' \
  -d '{"models":"tag:dts-metrics"}'
```

## 阻断条件

- `dts-metrics` 直接写 dbt 生产目录。
- `dts-metrics` 绕过 platform release gate。
- 生成物没有指标版本、来源资产或审核提示。
