# P2-02 指标中心接口性能基线（HTTP）

生成时间(UTC): 20260223T163122Z

## 运行参数

- api_base: http://localhost:18082
- iterations: 100
- threshold_p95_ms: 1500
- indicator_id: 95c5b82f-1c8a-48fe-8c6f-f59348411887
- endpoints: 9

## 汇总

- over_threshold_count: 0
- non200_count: 0

## 明细

| method | endpoint | total | non200 | dominant_status | non200_statuses | avg_ms | p50_ms | p95_ms | p99_ms | max_ms |
|---|---|---:|---:|---:|---|---:|---:|---:|---:|---:|
| GET | /api/governance/dimensions?page=0&size=20 | 100 | 0 | 200 |  | 8 | 8 | 9 | 12 | 19 |
| GET | /api/governance/indicators/95c5b82f-1c8a-48fe-8c6f-f59348411887/references | 100 | 0 | 200 |  | 9 | 9 | 11 | 16 | 19 |
| GET | /api/governance/indicators/95c5b82f-1c8a-48fe-8c6f-f59348411887/versions | 100 | 0 | 200 |  | 9 | 9 | 10 | 11 | 21 |
| GET | /api/governance/indicators/ops/overview?hours=168 | 100 | 0 | 200 |  | 8 | 8 | 10 | 10 | 19 |
| GET | /api/governance/indicators/ops/trend?hours=168&bucketHours=24 | 100 | 0 | 200 |  | 8 | 8 | 9 | 10 | 18 |
| GET | /api/governance/indicators?page=0&size=20 | 100 | 0 | 200 |  | 9 | 9 | 10 | 11 | 12 |
| GET | /api/governance/indicators?status=PUBLISHED&page=0&size=20 | 100 | 0 | 200 |  | 9 | 9 | 12 | 18 | 23 |
| POST | /api/governance/indicators/95c5b82f-1c8a-48fe-8c6f-f59348411887/preview?limit=20 | 100 | 0 | 200 |  | 8 | 8 | 9 | 11 | 17 |
| POST | /api/governance/indicators/95c5b82f-1c8a-48fe-8c6f-f59348411887/publish-preview | 100 | 0 | 200 |  | 19 | 19 | 21 | 22 | 23 |

## 优化建议

- 当前 p95 均在阈值内，建议在更大样本量（>=100）复测。
- 未出现 non200，建议继续做 24h 定时基线回归。

## 产物

- raw_times: `/opt/prod/s10/dts-stack/worklog/v2.2.1/platform/governance/indicator-center/raw/p2-02-indicator-http-times-20260223T163122Z.csv`
- summary: `/opt/prod/s10/dts-stack/worklog/v2.2.1/platform/governance/indicator-center/raw/p2-02-indicator-http-summary-20260223T163122Z.csv`
