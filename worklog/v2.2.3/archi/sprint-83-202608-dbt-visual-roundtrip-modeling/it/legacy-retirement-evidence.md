# Sprint-83 legacy dbt surface retirement evidence

**Observed at**: 2026-08-02 (Asia/Shanghai)

## Caller and runtime evidence

- GitNexus upstream impact for `DbtGitResource` was `LOW`, direct callers `0`, affected processes `0`.
- GitNexus upstream impact for `DbtGitService` was `LOW`, with only `DbtGitResource.java` as its direct importer.
- The six `/etl/dbt/git/*` exports had no frontend callers; each function impact was `LOW`, direct callers `0`.
- `airflow dags list-runs -d dwh_dbt_dbt_manual -o json` returned `[]`.
- `airflow dags list-runs -d dwh_project_management_dbt_manual -o json` returned `[]`.
- After source retirement, both DAG files were absent from `/opt/airflow/dags/dwh/`, and `airflow dags list` returned neither DAG id.

## Physically retired surfaces

- `bin/dts-deploy`, `bin/dts-deploy-env.sh`, `bin/dts-dbt-import`, and the legacy TSV `bin/dts-pack` producer.
- `dwh_dbt_dbt_manual.py` and `dwh_project_management_dbt_manual.py` mutable/privileged BashOperator DAGs.
- `DbtGitResource`, `DbtGitService`, their six frontend exports, and the now-unused JGit dependency.
- The current CLI import sample and external LLM TSV import guide that instructed users to use the retired path.
- The obsolete ELT modeling smoke/page object and its mock handlers for `/etl/dbt/dag/ready`, direct compile/test/run, and the retired manual DAG id.

The canonical `DbtDagService.ensureReleaseBuildDag` and `ensurePlanDag` factories remain. Release materialization continues through the pinned ReleaseCandidate/runtime-spec path; no retired control plane is retained behind a feature flag or tombstone route.

## Source contracts

- `test_sprint83_legacy_dbt_surface_retirement.py` fixes the physical deletion list and frontend route absence.
- `platformApi.source-contract.test.ts` fixes all shared Git routes as retired exports.
- `EtlResourceLegacyDbtPreviewRetirementTest` fixes the backend shared Git controller/service as absent.

## Rollback boundary

Rollback restores the canonical platform, Airflow Release/Plan factory, and certified runtime deployment from the release artifact. It must not restore the retired BashOperator DAGs, shared project Git controller, direct shared-tree writer, or `/api/etl/dbt/run` caller. The deleted source remains recoverable from Git history for incident forensics only.

## Residual risk

`R-DBT-OPERATIONAL-MUTABLE-RUNTIME` remains explicitly scoped to non-release `OPERATIONAL_RUN`: it may use `DBT_IMAGE`, but it cannot publish a ReleaseCandidate or advance `servingRef`. Pinning an operational-only digest is follow-up work and does not weaken the certified `RELEASE_BUILD` boundary.
