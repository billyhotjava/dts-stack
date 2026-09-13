# RT-01 linux/amd64 rollback evidence template

- Candidate profile ID:
- Platform: `linux/amd64`
- Requirements lock SHA-256: `01d7c02b6bf4fefdfc188cbf9ef8aed4fb243c227c060103f195c4ca45af5f02`
- Candidate platform manifest digest:
- Correlation ID:
- Trigger UTC:
- Trigger reason/failure code:
- Previous platform manifest digest:
- Product gate confirmed `NOT_CERTIFIED`:
- New candidate tasks stopped or isolated:
- Target relations checked for active writes:
- Previous runtime configuration restored:
- Uncertified materialization returned `DBT_RUNTIME_NOT_CERTIFIED`:
- Airflow audit reference:
- DTS public audit reference:
- Retained artifact and relation evidence checksums:
- Completed by / UTC:

Rollback never certifies either digest and never deletes failed evidence. An
ARM64 image or evidence set cannot be used as this linux/amd64 rollback target.
