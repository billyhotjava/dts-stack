import type { KeycloakRole } from "#/keycloak";

// Reserved business roles to hide from assignment UIs (canonical names only)
const RESERVED_ROLE_NAMES = new Set<string>([
  "ROLE_SYS_ADMIN",
  "ROLE_AUTH_ADMIN",
  "ROLE_OP_ADMIN",
  "ROLE_SECURITY_AUDITOR",
]);

// Legacy code aliases kept only for filtering compatibility; UI will display canonical names.
const RESERVED_ROLE_CODES = new Set<string>([
  "SYSADMIN",
  "AUTHADMIN",
  "OPADMIN",
  "SECURITYAUDITOR",
  "AUDITORADMIN",
]);

function canonicalRole(value: string | null | undefined): string {
  if (!value) return "";
  return value.trim().toUpperCase().replace(/^ROLE[_-]?/, "").replace(/_/g, "");
}

export function isReservedBusinessRoleName(name: string | null | undefined): boolean {
  if (!name) return false;
  const upper = name.trim().toUpperCase();
  if (RESERVED_ROLE_NAMES.has(upper)) return true;
  return RESERVED_ROLE_CODES.has(canonicalRole(upper));
}

export function isKeycloakBuiltInRole(role: Pick<KeycloakRole, "name" | "clientRole" | "containerId">): boolean {
  const name = (role.name || "").trim();
  const lower = name.toLowerCase();
  if (!name) return false;
  if (lower === "offline_access" || lower === "uma_authorization") return true;
  if (lower.startsWith("default-roles-")) return true;
  // realm-management client roles
  const container = (role as any).clientId || role.containerId || "";
  if ((role.clientRole && String(container).toLowerCase() === "realm-management") ||
      lower.startsWith("realm-management") || lower.startsWith("realm_management")) {
    return true;
  }
  return false;
}

export function shouldHideRole(role: KeycloakRole): boolean {
  return isReservedBusinessRoleName(role.name) || isKeycloakBuiltInRole(role);
}
