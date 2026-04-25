import apiClient from "../apiClient";

export interface DeptDto {
	code: string;
	id?: number;
	nameZh?: string;
	nameEn?: string;
	parentId?: number | null;
	isRoot?: boolean;
	aliases?: string[];
}

type OrgNode = {
	id: number;
	name: string;
	deptCode?: string;
	parentId?: number;
	children?: OrgNode[];
	isRoot?: boolean;
};

function flattenOrgs(nodes: OrgNode[], out: DeptDto[] = []): DeptDto[] {
	for (const n of nodes || []) {
		const nodeId = Number.isFinite(Number(n.id)) ? Number(n.id) : undefined;
		const idCode = nodeId == null ? "" : String(nodeId);
		const deptCode = typeof n.deptCode === "string" && n.deptCode.trim() ? n.deptCode.trim() : idCode;
		const name = typeof n.name === "string" ? n.name.trim() : "";
		const aliases = Array.from(new Set([deptCode, idCode, name].map((item) => item.trim()).filter(Boolean)));
		out.push({
			code: deptCode,
			id: nodeId,
			nameZh: n.name,
			nameEn: n.name,
			parentId: n.parentId ?? null,
			isRoot: Boolean(n.isRoot),
			aliases,
		});
		if (n.children && n.children.length) flattenOrgs(n.children, out);
	}
	return out;
}

export async function listDepartments(keyword?: string): Promise<DeptDto[]> {
	try {
		const data = await apiClient.get<any>({ url: "/directory/orgs" });
		const arr: OrgNode[] = Array.isArray(data)
			? (data as OrgNode[])
			: Array.isArray((data as any)?.data)
				? ((data as any).data as OrgNode[])
			: [];
		let flat = flattenOrgs(arr);
		const kw = (keyword || "").trim().toLowerCase();
		if (kw) {
			flat = flat.filter(
				(d) =>
					d.code.toLowerCase().includes(kw) ||
					(d.nameZh || "").toLowerCase().includes(kw) ||
					(d.nameEn || "").toLowerCase().includes(kw) ||
					(d.aliases || []).some((alias) => alias.toLowerCase().includes(kw)),
			);
		}
		return flat.slice(0, 100);
	} catch (e) {
		console.warn(`[deptService] platform /api/directory/orgs failed: ${(e as any)?.message || e}`);
		return [];
	}
}

export default {
	listDepartments,
};
