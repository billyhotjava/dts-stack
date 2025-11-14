Keycloak Script: Per‑User IP Allowlist

Overview
- Enforces login source IP for specific administrative users (default: sysadmin, authadmin, auditadmin).
- Reads user attributes allowed-ip (single) and allowed-ips (comma-separated). If a protected user has no allowed IP set, deny login.
- Matches exact IPv4 strings. Keep values as plain IPs for simplicity.

Files
- services/dts-keycloak/scripts/user-ip-allowlist-authenticator.js

Prerequisites
- Keycloak scripts feature enabled (already set in compose: KC_FEATURES includes scripts).
- Reverse proxy forwards client IP via X-Forwarded-For and is configured as a trusted proxy in Keycloak (KC_PROXY=edge in our compose).

Setup Steps
1) Add user attributes on target accounts
   - Users → select sysadmin/authadmin/auditadmin → Attributes:
     - Key: allowed-ip  Value: 203.0.113.5 (single IP)
     - Key: allowed-ips Value: 198.51.100.7,203.0.113.8 (optional, comma-separated)

2) Create Script Authenticator
   - Realm settings → Scripts → Create → Type: Authenticator
   - Name: user-ip-allowlist (arbitrary)
   - Paste contents of services/dts-keycloak/scripts/user-ip-allowlist-authenticator.js
   - Save

3) Attach to Browser flow
   - Authentication → Flows → Copy “Browser” to “Browser-IP” (recommended)
   - In “Browser-IP” add Execution: + → Script → choose user-ip-allowlist → Requirement: REQUIRED
   - Bindings → Browser: select “Browser-IP”

Notes
- Scope: Only sysadmin/authadmin/auditadmin are enforced (configurable at top of the script). Other users pass through.
- Error behavior: If IP not allowed, the authenticator fails with ACCESS_DENIED and the login is rejected.
- Security: Keep TLS enabled. This check supplements, but does not replace, normal authentication.

Troubleshooting
- If all logins are denied for protected users, ensure allowed-ip/allowed-ips are set and the proxy is forwarding client IP (check the first X-Forwarded-For entry).
- For multi-proxy chains, verify KC_PROXY=edge (compose) and Traefik’s trusted IPs cover your proxy hops.

