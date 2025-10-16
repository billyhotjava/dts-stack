import type { KeycloakRole } from "#/keycloak";

// Reserved business roles to hide from assignment UIs (canonical names only)
const RESERVED_ROLE_PREFIXES = ["ROLE_SYS_ADMIN", "ROLE_AUTH_ADMIN", "ROLE_OP_ADMIN", "ROLE_SECURITY_AUDITOR"];
const RESERVED_BUSINESS_ROLE_CODES = new Set(["SYSADMIN", "AUTHADMIN", "OPADMIN", "AUDITADMIN", "SECURITYAUDITOR"]);

const normalizeReservedRole = (name: string | undefined | null): string => {
  if (!name) return "";
  const trimmed = name.trim();
  if (!trimmed) return "";
  const upper = trimmed.toUpperCase().replace(/[-\s]+/g, "_");
  const withoutPrefix = upper.startsWith("ROLE_") ? upper.substring(5) : upper;
  return withoutPrefix.replace(/_/g, "");
};

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
  const name = (role?.name || "").toString().trim().toUpperCase();
  if (!name) return true;
  if (RESERVED_ROLE_PREFIXES.some((prefix) => name === prefix || name === prefix.replace(/^ROLE_/, ""))) {
    return true;
  }
  if (name.startsWith("ROLE_")) {
    const body = name.slice(5);
    if (body === body.toUpperCase()) return true;
  }
  return isKeycloakBuiltInRole(role);
}

export function isReservedBusinessRoleName(name: string | undefined | null): boolean {
  const canonical = normalizeReservedRole(name);
  if (!canonical) return false;
  return RESERVED_BUSINESS_ROLE_CODES.has(canonical);
}
