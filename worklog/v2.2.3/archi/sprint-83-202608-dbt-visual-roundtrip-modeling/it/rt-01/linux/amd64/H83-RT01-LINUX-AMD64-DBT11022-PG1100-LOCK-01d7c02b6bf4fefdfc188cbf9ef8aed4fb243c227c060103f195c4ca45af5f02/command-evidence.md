# H83-01 command evidence

- Window: `2026-08-01T22:22:00Z` to `2026-08-01T22:33:31Z`
- Actor: `billy`
- Source HEAD: `8a751455b22ed0870c62bc2712bfb8c59a8593b0`
- Source-input bundle SHA-256: `1f85b79bb4765f191f3c51de7316372e1fe65a76b557dfcca47a2e580145a684`
- Correlation ID: `H83-RT01-20260801T2222Z-da312997`
- Tested image configuration digest: `sha256:da312997c55425d9a63622221245cee61c0e219ce83234587d2122959e1b640f`
- Tested registry manifest digest: `sha256:fe1d15f1b4215e693dadfc1d99be2ae07c7e50e8df144504005feb41cc7686b7`
- Registry used only as the isolated certification-lab distribution endpoint; deployment must supply its own repository with the same manifest digest.

All database credentials were ephemeral lab values supplied through environment variables and are omitted here.

| Check | Exit | Result |
|---|---:|---|
| repository runtime contract | 0 | PASS |
| entrypoint command matrix | 0 | PASS; all connection-capable commands blocked |
| cold linux/amd64 image build | 0 | PASS |
| `verify-dbt-runtime --verify-install` by registry digest | 0 | PASS; 53 locked distributions |
| `dbt --version` | 0 | core 1.10.22, postgres 1.10.0 |
| default-entrypoint `parse` | 0 | PASS |
| authorized-lab `compile` | 0 | PASS |
| authorized-lab `build` | 0 | PASS=12, WARN=0, ERROR=0, SKIP=0 |
| authorized-lab `run` | 0 | PASS=3, WARN=0, ERROR=0, SKIP=0 |
| authorized-lab `docs generate` | 0 | catalog generated |
| PostgreSQL relation query | 0 | view/table/incremental each contain 3 deterministic rows |
| `verify-dbt-runtime --gate-materialization` | 42 | `DBT_RUNTIME_NOT_CERTIFIED` |
| runtime env override to `CERTIFIED` followed by `run` | 42 | `DBT_RUNTIME_NOT_CERTIFIED` |
| corrupted lock mounted over immutable lock | 1 | `DBT_RUNTIME_LOCK_SHA256_MISMATCH` |

The normal product entrypoint was never bypassed by an application caller. The
compile/build/run/docs override was used only by the isolated certification
operator to produce evidence.
