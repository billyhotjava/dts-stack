import axios from "axios";
import useContextStore from "@/store/contextStore";

export interface DeptDto {
	code: string;
	nameZh?: string;
	nameEn?: string;
	parentId?: number | null;
	isRoot?: boolean;
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
		const deptCode = typeof n.deptCode === "string" && n.deptCode.trim() ? n.deptCode.trim() : String(n.id);
		out.push({
			code: deptCode,
			nameZh: n.name,
			nameEn: n.name,
			parentId: n.parentId ?? null,
			isRoot: Boolean(n.isRoot),
		});
		if (n.children && n.children.length) flattenOrgs(n.children, out);
	}
	return out;
}

export async function listDepartments(keyword?: string): Promise<DeptDto[]> {
	try {
		const headers: Record<string, string> = {};
		try {
			const ctx = useContextStore.getState();
			if (ctx.activeDept) headers["X-Active-Dept"] = ctx.activeDept as any;
		} catch {}
		const { data } = await axios.get<any>("/api/directory/orgs", { withCredentials: true, headers });
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
					(d.nameEn || "").toLowerCase().includes(kw),
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
