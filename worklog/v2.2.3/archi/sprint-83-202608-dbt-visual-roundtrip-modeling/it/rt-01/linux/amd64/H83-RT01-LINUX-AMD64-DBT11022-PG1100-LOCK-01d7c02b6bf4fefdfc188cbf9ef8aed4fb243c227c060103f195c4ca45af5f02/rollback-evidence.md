# H83-01 rollback evidence

- Candidate profile: `H83-RT01-LINUX-AMD64-DBT11022-PG1100-LOCK-01d7c02b6bf4fefdfc188cbf9ef8aed4fb243c227c060103f195c4ca45af5f02`
- Candidate manifest digest: `sha256:fe1d15f1b4215e693dadfc1d99be2ae07c7e50e8df144504005feb41cc7686b7`
- Correlation ID: `H83-RT01-20260801T2222Z-da312997`
- Exercise UTC: `2026-08-01T22:33:31Z`
- Mode: first-certification rollback; disable certification and retain the previously configured image.
- Candidate was not switched into the running DTS stack during H83-01.
- Product/runtime gate remained `NOT_CERTIFIED`; materialization returned `DBT_RUNTIME_NOT_CERTIFIED` with exit 42.
- Runtime environment override to `CERTIFIED` did not authorize execution.
- No Airflow DagRun was submitted by the candidate exercise.
- The existing `dts-dbt:1.10.0` deployment remains untrusted because its observed core was a prerelease; rollback never grants it certification.
- Retained evidence: installed distribution set, artifact checksums, relation evidence and command evidence in this directory.
- Completed by: `billy` at `2026-08-01T22:33:31Z`.

For a deployed certified profile, rollback is: disable the exact certification
profile first, confirm zero new Airflow submission, then restore the previous
image reference. Neither old nor new digest is inferred to be certified.
