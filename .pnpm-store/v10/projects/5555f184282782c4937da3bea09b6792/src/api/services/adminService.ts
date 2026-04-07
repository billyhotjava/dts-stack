import apiClient from "../apiClient";

export type WhoAmI = {
	allowed: boolean;
	role: string | null;
	username: string | null;
	email: string | null;
};

export type OrgNode = { id: number; name: string; parentId?: number; children?: OrgNode[] };

export async function whoAmI(): Promise<WhoAmI> {
	const data = await apiClient.get<WhoAmI>({ url: "/admin/whoami" });
	return data ?? { allowed: false, role: null, username: null, email: null };
}

export async function listOrgs(): Promise<OrgNode[]> {
	const data = await apiClient.get<OrgNode[]>({ url: "/directory/orgs" });
	return Array.isArray(data) ? data : [];
}

export default { whoAmI, listOrgs };
