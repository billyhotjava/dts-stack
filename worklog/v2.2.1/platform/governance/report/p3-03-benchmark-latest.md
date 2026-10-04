# P3-03 治理接口性能基线（HTTP）

生成时间(UTC): 20260222T235428Z

## 运行参数

- api_base: http://localhost:18080
- iterations: 1
- threshold_p95_ms: 1500
- endpoints: 6

## 汇总

- over_threshold_count: 0
- non200_count: 6

## 明细

| endpoint | total | non200 | avg_ms | p50_ms | p95_ms | p99_ms | max_ms |
|---|---:|---:|---:|---:|---:|---:|---:|
| /api/governance/compliance/batches?limit=20 | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| /api/governance/issues?limit=20 | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| /api/governance/ops/overview?days=14 | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| /api/governance/quality/rules | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| /api/governance/quality/runs?limit=20 | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| /api/governance/reference-codes?page=0&size=20 | 1 | 1 | 0 | 0 | 0 | 0 | 0 |

## 产物

- raw_times: `/opt/prod/s10/dts-stack/worklog/v2.2.1/platform/governance/raw/p3-03-http-times-20260222T235428Z.csv`
- summary: `/opt/prod/s10/dts-stack/worklog/v2.2.1/platform/governance/raw/p3-03-http-summary-20260222T235428Z.csv`
