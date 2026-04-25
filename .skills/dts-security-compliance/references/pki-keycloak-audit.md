# PKI, Keycloak, And Audit Notes

## Ownership

- `dts-admin` owns identity administration, users, roles, departments, Keycloak sync, PKI integration, IP whitelist, and audit administration.
- `dts-platform` owns data-service authorization and domain audit events for modeling, dbt, API publishing, and data access.
- Gateway or Traefik config should only enforce transport/routing concerns unless the existing deployment already centralizes an auth check there.

## PKI And Crypto Modes

- Source documents:
  - `docs/intergration/pki/pki-auth.md`
  - `docs/intergration/pki/pki-crypto-modes.md`
  - `docs/intergration/pki/koal-thrift-api-mapping.md`
- Keep common-crypto and commercial-crypto behavior explicit in config and tests.
- Avoid hard-coded vendor endpoints, keys, or certificate paths.

## Keycloak

- DTS user and department data must stay aligned with Keycloak attributes.
- `dept_code` is a contract consumed by BI and downstream apps.
- When adding or changing claims, update both Keycloak mapper assumptions and backend/frontend consumers.

## Audit Architecture

Source document: `docs/implementation/audit-architecture.md`.

Expected properties:

- Immutable audit event record.
- Async writer with fallback path when queue is full.
- Payload HMAC and signature chain for tamper evidence.
- Retention policy, export API, and role-aware access.
- Separate events for background jobs, imports, exports, and async dbt operations.
