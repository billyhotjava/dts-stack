# dbt 发布门禁证据目录

本目录用于最终统一测试阶段归档 Sprint-31 F4 的证据。当前按用户要求暂不执行中途编译、测试和容器重建。

## 待最终执行

```bash
dbt compile --project-dir services/dts-dbt --profiles-dir services/dts-dbt/profiles
dbt test --project-dir services/dts-dbt --profiles-dir services/dts-dbt/profiles
dbt build --project-dir services/dts-dbt --profiles-dir services/dts-dbt/profiles --select tag:dbt
```

```bash
curl -sS -X POST http://127.0.0.1:18082/api/etl/dbt/release/submit \
  -H 'Content-Type: application/json' \
  -d '{"models":"tag:dbt","gitRef":"release/v2.2.3","commitSha":"0000000","strictMode":true}'
```

## 验收点

- 构建失败、缺 schema 合约、缺治理元信息返回 `status=BLOCKED`。
- `confirmWarnings=true` 不能绕过 blockers。
- 返回体包含 `qualityGate`、`releaseGate` 和 `buildEvidence`。
