import apiClient from "../apiClient";

export interface DirectoryRole {
  id?: string;
  name: string;
  description?: string;
}

export interface OrgNode {
  id: number;
  name: string;
  parentId?: number;
  children?: OrgNode[];
}

const DirectoryApi = {
  Roles: "/directory/roles",
  Orgs: "/directory/orgs",
};

export async function listRoles(): Promise<DirectoryRole[]> {
  // platform backend proxies to admin; returns raw list
  return apiClient.get<DirectoryRole[]>({ url: DirectoryApi.Roles });
}

export async function getOrgTree(): Promise<OrgNode[]> {
  return apiClient.get<OrgNode[]>({ url: DirectoryApi.Orgs });
}

export default {
  listRoles,
  getOrgTree,
};

