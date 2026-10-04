# P3-01 指标中心 E2E 回归门禁（最新）

生成时间(UTC): 20260223T163015Z

## 结果

- overall: PASS
- required_failed: 0
- optional_failed: 0

## 明细

| step | required | method | path | status | ok | duration_ms | message |
|---|---:|---|---|---:|---:|---:|---|
| list_indicators | 1 | GET | /api/governance/indicators?page=0&size=5 | 200 | 1 | 57 | OK |
| create_indicator | 1 | POST | /api/governance/indicators | 200 | 1 | 45 | OK |
| update_indicator | 1 | PUT | /api/governance/indicators/246b94a5-fc6a-43e9-9f74-bbf17643c36e | 200 | 1 | 20 | OK |
| list_versions | 1 | GET | /api/governance/indicators/246b94a5-fc6a-43e9-9f74-bbf17643c36e/versions | 200 | 1 | 25 | OK |
| list_references | 1 | GET | /api/governance/indicators/246b94a5-fc6a-43e9-9f74-bbf17643c36e/references | 200 | 1 | 26 | OK |
| validate_indicator | 0 | POST | /api/governance/indicators/246b94a5-fc6a-43e9-9f74-bbf17643c36e/validate | 200 | 1 | 27 | OK |
| preview_indicator | 0 | POST | /api/governance/indicators/246b94a5-fc6a-43e9-9f74-bbf17643c36e/preview?limit=20 | 200 | 1 | 23 | OK |
| publish_preview | 0 | POST | /api/governance/indicators/246b94a5-fc6a-43e9-9f74-bbf17643c36e/publish-preview | 200 | 1 | 26 | OK |
| delete_indicator | 1 | DELETE | /api/governance/indicators/246b94a5-fc6a-43e9-9f74-bbf17643c36e | 200 | 1 | 39 | OK |

## 产物

- raw: `/opt/prod/s10/dts-stack/worklog/v2.2.1/platform/governance/indicator-center/raw/p3-01-indicator-e2e-20260223T163015Z.csv`
- summary: `/opt/prod/s10/dts-stack/worklog/v2.2.1/platform/governance/indicator-center/raw/p3-01-indicator-e2e-summary-20260223T163015Z.txt`
