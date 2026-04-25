# DTS Security Checklist

## Authentication

- Confirm the source of identity: Keycloak token, PKI/USBKey assertion, session, or service token.
- Validate token issuer, audience, expiration, and mapped claims.
- Do not trust frontend-provided identity fields when backend security context is available.

## Authorization

- Check role, department, data scope, and resource ownership server-side.
- Include deny-path tests for missing role, wrong department, disabled user, and expired credentials.
- Keep menu visibility, route guards, and backend authorization aligned.

## Secrets

- Treat `.env`, certificate files, private keys, database credentials, and bearer tokens as sensitive.
- Do not commit generated private material or customer-specific secrets.
- Redact sensitive values from logs and diagnostics.

## Audit

Audit these classes of action:

- Login, logout, failed authentication.
- User, role, department, permission, Keycloak sync changes.
- PKI and IP whitelist changes.
- Data source, SQL model, dbt run/release, API publish/unpublish.
- Import, export, download, and destructive data operations.

Minimum fields:

- Actor, actor role, module, action, resource type, resource id, client IP, user agent, request URI, method, result, latency, timestamp.

## Data Protection

- Mask secrets in API responses and UI.
- Avoid exporting fields that are not required by the user's role.
- For audit payloads, prefer encrypted payload plus HMAC/signature metadata.
