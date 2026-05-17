# Sprint-31B Internal API Version Policy

## Scope

This policy applies to platform internal APIs consumed by service principals such as `dts-metrics`, `dts-analytics`, and `dts-ingestion`.

## Rules

1. New internal contracts use an explicit version segment: `/api/internal/v1/...`.
2. Additive response fields may stay on the same version when existing clients can ignore them safely.
3. Field removal, field type changes, or semantic changes require a new version.
4. Deprecated aliases must remain for at least one sprint unless they expose a security risk.
5. Service-auth allowlists and `/api/internal/capabilities` must be updated in the same change as the client URL switch.

## Current Contracts

| Contract | Long-term path | Deprecated alias | Notes |
|---|---|---|---|
| Asset policy | `/api/internal/v1/asset-permission/policy` | `/api/internal/asset-permission/policy` | v1 returns HTTP 403 on denied access; legacy alias keeps fail-closed `1 = 0` behavior for compatibility. |

## Verification

- `ServiceDependencyAuthenticationFilterTest` covers `dts-metrics` access to the v1 and legacy policy paths.
- `PlatformCapabilityResourceTest` covers the v1 policy endpoint in `requiredPlatformContracts` and permission endpoints.
- `PlatformContractClientTest.resolveRlsPolicyCallsVersionedPlatformContractWithServiceAuth` covers the `dts-metrics -> dts-platform` v1 URL.
