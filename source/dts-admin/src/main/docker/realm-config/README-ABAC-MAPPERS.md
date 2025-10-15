# ABAC Mappers (Keycloak)

This folder contains the default Keycloak realm export (`jhipster-realm.json`). To enable ABAC claims in tokens:

- Add realm roles used by this project: `ROLE_SYS_ADMIN`, `ROLE_AUTH_ADMIN`, `ROLE_SECURITY_AUDITOR`, `ROLE_OP_ADMIN`.
- Ensure users carry a user attribute `person_level` (NON_SECRET|GENERAL|IMPORTANT|CORE).

> 自 2025-12 起，平台不再使用 `data_levels` Claim，Keycloak Token 中仅需保留 `person_level`。旧版脚本映射可安全移除。
Add a simple mapper to pass-through `person_level`:
- Mapper type: "User Attribute"; User Attribute: `person_level`; Token claim name: `person_level`; Add to access token; Claim JSON type: `String`.

## Client Access Policy

- To block "三员" from logging into dts-platform client: do not grant any client roles to those users and enable client setting "Full Scope Disabled" with explicit role assignment; or enable "Only allow assigned roles" so unassigned users won't get tokens.

## Notes
- In dev, you can use dts-admin helper endpoints:
  - `PUT /api/keycloak/users/{id}/person-level` with `{ "person_level": "GENERAL" }`
  - `GET /api/keycloak/users/{id}/abac-claims` to preview claims.
