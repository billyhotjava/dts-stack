import axios from "axios";
import type { KeycloakRole } from "#/keycloak";
import { GLOBAL_CONFIG } from "@/global-config";

/**
 * List realm roles from the admin service (platform-friendly endpoint).
 * Endpoint: GET {ADMIN_BASE}/keycloak/platform/roles
 * Security: permitted by admin service for platform audience (no triad token required).
 */
export async function listRealmRoles(): Promise<KeycloakRole[]> {
  const base = GLOBAL_CONFIG.adminApiBaseUrl.replace(/\/+$/, "");
  const url = `${base}/keycloak/platform/roles`;
  const { data } = await axios.get<KeycloakRole[]>(url, { withCredentials: false });
  if (Array.isArray(data)) return data;
  // Some admin endpoints return { status, data } envelope; unwrap best-effort
  const inner: any = (data as any)?.data;
  return Array.isArray(inner) ? (inner as KeycloakRole[]) : [];
}

export default { listRealmRoles };

