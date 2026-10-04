---
name: dts-security-compliance
description: Use when changing or reviewing DTS security-sensitive behavior including PKI, USBKey, commercial/common cryptography modes, Keycloak claims, users, roles, departments, three-admin separation, IP whitelist, audit events, secrets, certificates, tokens, authorization, or sensitive imports/exports.
---

# DTS Security Compliance

Use this skill for security-sensitive DTS work.

## Workflow

1. Load `references/security-checklist.md`.
2. Load `references/pki-keycloak-audit.md` when PKI, Keycloak, roles, or audit behavior is involved.
3. Identify the actor, resource, action, tenant/department scope, and audit obligation.
4. Confirm whether behavior belongs in `dts-admin`, `dts-platform`, gateway/proxy config, or shared code.
5. Keep authentication, authorization, audit, and data masking changes together in the design even if implemented in separate modules.
6. Add tests for deny paths, not only allow paths.

## Guardrails

- Never print tokens, private keys, certificates, passwords, or USBKey secrets in logs or test output.
- Do not bypass server-side authorization because the frontend hides an action.
- Keep Keycloak attributes and DTS user/department fields consistent.
- Security configuration changes must include upgrade/deploy notes.
- Audit-sensitive operations must capture actor, action, module, resource, result, client metadata, and timestamp.
