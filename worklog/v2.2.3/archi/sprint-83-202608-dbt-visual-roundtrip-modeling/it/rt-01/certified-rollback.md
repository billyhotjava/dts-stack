# Certified derivative rollback

- Certified profile: `H83-CERT-RT01-LINUX-AMD64-EVIDENCE-bcd2fc84b05b9508990538c6642be7ac7b35073ec034e6b1980b22f556345a68`.
- Candidate evidence digest remains `sha256:fe1d15f1b4215e693dadfc1d99be2ae07c7e50e8df144504005feb41cc7686b7`; it is not executable for materialization because its immutable metadata is `NOT_CERTIFIED`.
- Certified derivative digest is `sha256:2f6dddb7237fdb7141f452b6d09da0379ef7569f2f82560473f304b265cbbd85`.
- Revoked derivative `sha256:423926d8ce77a9bdce23db23501910843e9c7b17476b1c025320a3098e2d33f8` did not validate its actual `FROM` reference and must never be configured as certified.
- Immediate rollback/revocation sets `DTS_DBT_RUNTIME_CERTIFICATION_STATUS=NOT_CERTIFIED` (or removes any required pin) and restarts only `dts-platform`. New dispatch and runtime-spec consumption then fail with `DBT_RUNTIME_NOT_CERTIFIED` before Airflow submission or lease issue.
- Do not roll back by assigning a mutable image tag, by pointing at the candidate digest, or by setting a container environment override. There is no earlier certified profile in this line; rollback therefore means safe revocation, preserving the last serving Catalog pointer while materialization remains unavailable.
- Promotion to a later runtime requires a new candidate identity, evidence manifest, certification profile and certified derivative digest. None of the pins in this profile may be reused.
