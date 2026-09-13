# Certified derivative build evidence

- Candidate `FROM` reference: `<immutable-registry>/dts-dbt@sha256:fe1d15f1b4215e693dadfc1d99be2ae07c7e50e8df144504005feb41cc7686b7`.
- Candidate configuration digest: `sha256:da312997c55425d9a63622221245cee61c0e219ce83234587d2122959e1b640f`.
- Certified derivative platform manifest digest: `sha256:2f6dddb7237fdb7141f452b6d09da0379ef7569f2f82560473f304b265cbbd85`.
- Certified derivative configuration digest: `sha256:aaacff2f8d95d2fa5adaa15d5077cf64a2461b01c826887a0890976063cd2523`.
- Superseded derivative `sha256:423926d8ce77a9bdce23db23501910843e9c7b17476b1c025320a3098e2d33f8` is revoked because it did not bind the actual `FROM` reference.

Reproducible build shape:

```sh
docker build --platform linux/amd64 \
  --build-arg DBT_RUNTIME_CANDIDATE_IMAGE=<immutable-registry>/dts-dbt@sha256:fe1d15f1b4215e693dadfc1d99be2ae07c7e50e8df144504005feb41cc7686b7 \
  --file builds/dts-dbt/Dockerfile.certified \
  --tag <immutable-registry>/dts-dbt:h83-rt01-linux-amd64-certified-r2 \
  builds/dts-dbt
```

Verification evidence:

- Source contract: `certified-runtime-contract: PASS`.
- Malicious mutable base with a no-op candidate verifier: rejected with `DBT_RUNTIME_CANDIDATE_REF_MISMATCH` before that verifier can execute.
- Immutable derivative `--verify-install`: `PASS`.
- Immutable derivative `--gate-materialization`: `PASS`, `candidateStatus=CERTIFIED`.
- Runtime versions: `dbt-core 1.10.22`, `dbt-postgres 1.10.0`.
