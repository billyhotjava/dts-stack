import apiClient from "../apiClient";
import type { KeycloakRole } from "#/keycloak";

/**
 * List realm roles from the platform directory gateway.
 */
export async function listRealmRoles(): Promise<KeycloakRole[]> {
  const data = await apiClient.get<any[]>({ url: "/directory/roles" });
  if (!Array.isArray(data)) return [];
  return data.map((item) => ({
    id: item?.id,
    name: item?.name,
    description: item?.description,
  }));
}

export default { listRealmRoles };
