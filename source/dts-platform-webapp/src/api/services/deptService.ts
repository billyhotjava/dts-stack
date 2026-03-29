import axios from "axios";
import userStore from "@/store/userStore";
import useContextStore from "@/store/contextStore";

// Backward-compatible DTO used by existing pages (code + display names)
export interface DeptDto {
  code: string;
  nameZh?: string;
  nameEn?: string;
  parentId?: number | null; // for root detection (null => root)
  isRoot?: boolean;
}

type OrgNode = {
  id: number;
  name: string;
  parentId?: number;
  children?: OrgNode[];
  isRoot?: boolean;
};

function flattenOrgs(nodes: OrgNode[], out: DeptDto[] = []): DeptDto[] {
  for (const n of nodes || []) {
    out.push({
      code: String(n.id),
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
      const { userToken } = userStore.getState();
      const raw = String(userToken?.accessToken || "").trim();
      if (raw) headers["Authorization"] = raw.startsWith("Bearer ") ? raw : `Bearer ${raw}`;
      const ctx = useContextStore.getState();
      if (ctx.activeDept) headers["X-Active-Dept"] = ctx.activeDept as any;
    } catch {}
    const { data } = await axios.get<any>("/api/directory/orgs", { withCredentials: false, headers });
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
