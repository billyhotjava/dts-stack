# ABAC Mappers (Keycloak)

This folder contains the default Keycloak realm export (`jhipster-realm.json`). To enable ABAC claims in tokens:

- Add realm roles used by this project: `ROLE_SYS_ADMIN`, `ROLE_AUTH_ADMIN`, `ROLE_SECURITY_AUDITOR`, `ROLE_OP_ADMIN`.
- Ensure users carry a user attribute `person_level` (NON_SECRET|GENERAL|IMPORTANT|CORE).
- Add a protocol mapper that derives `data_levels` array claim from `person_level`.

## Example Script Mapper (Keycloak 22+)

Mapper type: "Script Mapper"; Token Claim Name: `data_levels`; Claim JSON type: `String Array`; Add to access token.

Script:
```javascript
var person = user.getFirstAttribute('person_level');
if (!person) person = 'NON_SECRET';
var map = {
  NON_SECRET: ['DATA_PUBLIC'],
  GENERAL: ['DATA_PUBLIC','DATA_INTERNAL'],
  IMPORTANT: ['DATA_PUBLIC','DATA_INTERNAL','DATA_SECRET'],
  CORE: ['DATA_PUBLIC','DATA_INTERNAL','DATA_SECRET','DATA_TOP_SECRET']
};
var list = map[person] || ['DATA_PUBLIC'];
list;
```

Also add a simple mapper to pass-through `person_level`:
- Mapper type: "User Attribute"; User Attribute: `person_level`; Token claim name: `person_level`; Add to access token; Claim JSON type: `String`.

## Client Access Policy

- To block "三员" from logging into dts-platform client: do not grant any client roles to those users and enable client setting "Full Scope Disabled" with explicit role assignment; or enable "Only allow assigned roles" so unassigned users won't get tokens.

## Notes
- In dev, you can use dts-admin helper endpoints:
  - `PUT /api/keycloak/users/{id}/person-level` with `{ "person_level": "GENERAL" }`
  - `GET /api/keycloak/users/{id}/abac-claims` to preview claims.
