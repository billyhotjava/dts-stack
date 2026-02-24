# Governance Ops Preflight

generated_utc: 20260223T233444Z

| check | item | result | detail |
|---|---|---|---|
| binary | curl | PASS | found |
| binary | python3 | PASS | found |
| binary | awk | PASS | found |
| binary | sed | PASS | found |
| binary | psql | PASS | docker_fallback:dts-stack-dts-pg-1 |
| filesystem | /opt/prod/s10/dts-stack/worklog/v2.2.1/platform/governance/raw | PASS | writable |
| filesystem | /opt/prod/s10/dts-stack/worklog/v2.2.1/platform/governance/report | PASS | writable |
| filesystem | /opt/prod/s10/dts-stack/worklog/v2.2.1/platform/governance/logs | PASS | writable |
| source | /opt/prod/s10/dts-stack/source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/GovernanceResource.java | PASS | exists |
| source | /opt/prod/s10/dts-stack/source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/GovernanceReferenceCodeResource.java | PASS | exists |
| api | http://localhost:18082 | PASS | http_status=401 |
| database | dts-stack-dts-pg-1 | PASS | connect_ok(docker) |
| token | TOKEN_MAINTAINER | PASS | present |
| token | TOKEN_EMPLOYEE | PASS | present |
| token | TOKEN | PASS | present |

- raw: `/opt/prod/s10/dts-stack/worklog/v2.2.1/platform/governance/raw/preflight-20260223T233444Z.csv`
