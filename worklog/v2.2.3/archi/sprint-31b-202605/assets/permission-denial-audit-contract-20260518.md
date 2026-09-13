# Permission Denial Audit Contract (2026-05-18)

## Scope

Sprint-31B F4/T02 closes the Sprint-31A audit gap by turning permission denial reasons into a structured compliance contract.

## Runtime Contract

`PermissionDecision` keeps the legacy `reason` field for existing callers and adds:

- `reasonCode`: machine-readable code, e.g. `NO_GRANT`, `CLASSIFICATION_MISMATCH`, `CLASSIFICATION_REQUIRED`, `INSUFFICIENT_PERMISSION`, `UNSUPPORTED_ACTION`.
- `reasonDetail`: human-readable explanation.
- `suggestedRemediation`: operational next step.
- `deniedAt`: timestamp set only for denied decisions.

`asset_permission_audit` persists:

- `reason_code`
- `reason_detail`

## Internal Read APIs

- `GET /api/internal/v1/asset-permission/audit/denied?since=&reasonCode=&assetId=&limit=`
- `GET /api/internal/v1/asset-permission/audit/denied.csv?since=&reasonCode=&assetId=&limit=`

CSV schema:

```text
actor,assetType,assetId,permission,operator,reasonCode,reasonDetail,deniedAt
```

## Verification

```bash
./mvnw -q -pl dts-platform -Dtest=AssetPermissionServiceTest,AssetPermissionAuditServiceTest,AssetPermissionInternalResourceTest,AssetPermissionAuditQueryResourceTest,ServiceDependencyAuthenticationFilterTest test
```
