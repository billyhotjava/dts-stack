# RT-01 PostgreSQL runtime candidate evidence

This directory contains immutable evidence templates for H83-01. It does not
contain a certification record and it must never contain database credentials,
tokens, private registry credentials, or complete connection strings.

## Fixed linux/amd64 candidate inputs

- Platform: `linux/amd64` only
- Candidate profile: `H83-RT01-LINUX-AMD64-DBT11022-PG1100-LOCK-01d7c02b6bf4fefdfc188cbf9ef8aed4fb243c227c060103f195c4ca45af5f02`
- Candidate status: `NOT_CERTIFIED`
- Materialization failure code before F0/T05: `DBT_RUNTIME_NOT_CERTIFIED`
- Base image index digest: `sha256:db3ff2e1800a8581e2c48a27c3995339d47bdf046da21c7627accd3d51053a93`
- Base image linux/amd64 manifest digest: `sha256:00af38ae2ed311628970782e8a2d7f014d8909dbc63cb97bc0a158187f4db045`
- Runtime lock SHA-256: `01d7c02b6bf4fefdfc188cbf9ef8aed4fb243c227c060103f195c4ca45af5f02`
- Lock generator bootstrap SHA-256: `e4860ac14f16fb4fd6f5b1383017f49f51ff4623174ae8e62458c9ac6c416f63`
- Lock generator base tooling: `pip==24.0`, `setuptools==79.0.1`
- Direct dependencies: `dbt-core==1.10.22`, `dbt-postgres==1.10.0`
- Explicitly observed transitives: `dbt-adapters==1.24.5`, `dbt-common==1.38.0`

The immutable templates for this candidate live at:

```text
it/rt-01/linux/amd64/H83-RT01-LINUX-AMD64-DBT11022-PG1100-LOCK-01d7c02b6bf4fefdfc188cbf9ef8aed4fb243c227c060103f195c4ca45af5f02/
```

The platform and complete lock digest are part of the candidate identity. A
different lock or `linux/arm64` build must use a new ID and separate evidence.

Run the offline repository contract first:

```sh
sh builds/dts-dbt/tests/runtime-contract-test.sh
```

Build the candidate without changing any deployed tag:

```sh
docker build \
  --platform linux/amd64 \
  --file builds/dts-dbt/Dockerfile \
  --tag dts-dbt:h83-rt01-linux-amd64-candidate-r1 \
  builds/dts-dbt
```

The registry image digest, not the local tag or mutable image name, is the
runtime identity. Copy the templates into a new directory named with the final
candidate profile ID and image digest. Never overwrite evidence from a failed
attempt.

## Required execution sequence

Every command must run against the same immutable candidate image digest and
the same isolated PostgreSQL instance:

1. `verify-dbt-runtime --verify-install` and `python -m pip list --format=json`.
2. Use the default entrypoint only for help/version/parse/list/ls. `debug` is
   blocked because it can test a database connection.
3. Verify that default image invocations of `run`, `build`, `seed`, `snapshot`
   `test`, `compile`, `docs generate`, `debug`, `run-operation`, `show` and
   `retry` exit 42 with `DBT_RUNTIME_NOT_CERTIFIED`, including when the
   container environment overrides certification status or global CLI options
   precede the command.
4. In an isolated certification lab only, an authorized operator may override
   the image entrypoint to execute `dbt compile`, `dbt build`, `dbt run` and
   `dbt docs generate` against RT-01. This operator-level action must be
   separately audited; it is never available to application callers or normal
   Airflow DAGs. Use
   `services/dts-dbt/rt01` and `profiles.yml.example` mounted as
   `/tmp/rt01-profile/profiles.yml`.
5. Query PostgreSQL metadata for the view, table and incremental relation and
   verify the deterministic three-row result.
6. Hash `manifest.json`, `catalog.json`, `run_results.json`, redacted logs and
   relation evidence, then complete `evidence-manifest.json`.
7. Exercise `verify-dbt-runtime --gate-materialization`; it must exit 42 with
   `DBT_RUNTIME_NOT_CERTIFIED` until F0/T05 registers certification.

All evidence records UTC time, source commit, actor, PostgreSQL server version,
image digest, invocation ID, exit status and correlation ID. Logs must redact
passwords, tokens, registry credentials and full connection strings.

## Handoff boundary

H83-01 may hand an evidence-complete candidate to F0/T05, but the candidate
remains `NOT_CERTIFIED`. Only F0/T05 may create `certificationProfileId` and
change the product control-plane status. Missing or mismatched evidence keeps
materialization fail-closed.

The image entrypoint is defense in depth, not the authorization owner. Docker
socket administrators can technically replace an entrypoint, so the platform
control-plane certification gate remains the final authorization layer and
must reject the candidate before Airflow submission. Certification later
requires a new immutable image digest and deployment switch; changing runtime
environment variables cannot authorize this image.
