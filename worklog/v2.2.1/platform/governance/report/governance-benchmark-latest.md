# Governance HTTP Benchmark

generated_utc: 20260223T233447Z

- api_base: http://localhost:18082
- iterations: 20
- threshold_p95_ms: 1500
- endpoints: 6
- over_threshold_count: 0
- non200_count: 0

| endpoint | total | non200 | avg_ms | p50_ms | p95_ms | p99_ms | max_ms |
|---|---:|---:|---:|---:|---:|---:|---:|
| /api/governance/compliance/batches?limit=20 | 20 | 0 | 8 | 8 | 9 | 15 | 15 |
| /api/governance/issues?limit=20 | 20 | 0 | 8 | 8 | 11 | 11 | 11 |
| /api/governance/ops/overview?days=14 | 20 | 0 | 11 | 10 | 12 | 17 | 17 |
| /api/governance/quality/rules | 20 | 0 | 11 | 11 | 12 | 14 | 14 |
| /api/governance/quality/runs?limit=20 | 20 | 0 | 8 | 8 | 10 | 17 | 17 |
| /api/governance/reference-codes?page=0&size=20 | 20 | 0 | 8 | 8 | 10 | 16 | 16 |

- raw_times: `/opt/prod/s10/dts-stack/worklog/v2.2.1/platform/governance/raw/governance-http-times-20260223T233447Z.csv`
- summary: `/opt/prod/s10/dts-stack/worklog/v2.2.1/platform/governance/raw/governance-http-summary-20260223T233447Z.csv`
