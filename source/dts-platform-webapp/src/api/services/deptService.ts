import axios from "axios";
import { GLOBAL_CONFIG } from "@/global-config";
import userStore from "@/store/userStore";

// Backward-compatible DTO used by existing pages (code + display names)
export interface DeptDto {
  code: string;
  nameZh?: string;
  nameEn?: string;
}

type OrgNode = {
  id: number;
  name: string;
  parentId?: number;
  children?: OrgNode[];
};

// Known admin endpoints (by deploy topology):
// - Admin UI domain:  /api/admin/platform/orgs  (most common in current builds)
// - API gateway domain with prefix-strip (/admin): /admin/api/platform/orgs -> /api/platform/orgs inside admin
// - Legacy/public variants may exist; keep a permissive fallback list
const AdminApi = {
  // Prefer platform-friendly endpoint first to work with reverse proxy base "/admin/api"
  // This resolves to:
  //   - Gateway (default):  base "/admin/api" + path "/platform/orgs" => "/admin/api/platform/orgs"
  //   - Admin domain:       base "/api/admin" + path "/platform/orgs" => "/api/admin/platform/orgs"
  OrgsAltGateway: "/platform/orgs",

  // Secondary: admin-scoped path (kept for tolerance with custom bases)
  // If base is "/api", this becomes "/api/admin/platform/orgs"
  // If base is "/api/admin", this becomes "/api/admin/admin/platform/orgs" and will be skipped on failure
  OrgsPrimary: "/admin/platform/orgs",

  // Fallback: legacy/dev-only
  OrgsLegacy: "/orgs",
} as const;

function flattenOrgs(nodes: OrgNode[], out: DeptDto[] = []): DeptDto[] {
  for (const n of nodes || []) {
    out.push({ code: String(n.id), nameZh: n.name, nameEn: n.name });
    if (n.children && n.children.length) flattenOrgs(n.children, out);
  }
  return out;
}

export async function listDepartments(keyword?: string): Promise<DeptDto[]> {
  // Try multiple admin endpoints in order; continue on 404/401/network errors.
  const base = GLOBAL_CONFIG.adminApiBaseUrl.replace(/\/+$/, "");

  const unwrap = (data: any): OrgNode[] => {
    if (Array.isArray(data)) return data as OrgNode[];
    if (data && Array.isArray((data as any).data)) return (data as any).data as OrgNode[];
    return [];
  };

  const authHeaders = (): Record<string, string> => {
    const { userToken } = userStore.getState();
    const raw = String(userToken?.adminAccessToken || "").trim();
    if (!raw) return {};
    const headerValue = raw.startsWith("Bearer ") ? raw : `Bearer ${raw}`;
    return { Authorization: headerValue };
  };

  // In dev we proxy '/admin/api' -> admin '/api', so prefer the explicit admin-scoped path first.
  // Try platform-friendly first to avoid double "/admin" when base is "/admin/api"
const paths = [AdminApi.OrgsAltGateway, AdminApi.OrgsPrimary, AdminApi.OrgsLegacy];
	for (const path of paths) {
		try {
			const { data } = await axios.get<any>(`${base}${path}`, { withCredentials: false, headers: authHeaders() });
			const arr = unwrap(data);
			if (arr && arr.length) {
				let flat = flattenOrgs(arr);
				const kw = (keyword || "").trim().toLowerCase();
				if (kw) {
					flat = flat.filter((d) =>
						d.code.toLowerCase().includes(kw) ||
						(d.nameZh || "").toLowerCase().includes(kw) ||
						(d.nameEn || "").toLowerCase().includes(kw),
					);
				}
				return flat.slice(0, 100);
			}
		} catch (e) {
			// Continue to next candidate on any error
			const msg = (e as any)?.message || String(e);
			console.warn(`[deptService] ${path} failed: ${msg}`);
			continue;
		}
	}
	console.warn("[deptService] All admin org endpoints failed; returning empty org list");
	return [];
}

export default {
  listDepartments,
};
