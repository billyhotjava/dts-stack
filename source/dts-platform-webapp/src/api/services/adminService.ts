import axios from "axios";
import { GLOBAL_CONFIG } from "@/global-config";
import userStore from "@/store/userStore";

export type WhoAmI = {
  allowed: boolean;
  role: string | null;
  username: string | null;
  email: string | null;
};

type ApiEnvelope<T> = { status?: any; message?: string; data?: T } | T;

const base = () => GLOBAL_CONFIG.adminApiBaseUrl.replace(/\/+$/, "");

const authHeaders = (): Record<string, string> => {
  const { userToken } = userStore.getState();
  const raw = String(userToken?.adminAccessToken || "").trim();
  if (!raw) return {};
  const headerValue = raw.startsWith("Bearer ") ? raw : `Bearer ${raw}`;
  return { Authorization: headerValue };
};

function unwrap<T>(resp: { data: ApiEnvelope<T> }): T {
  const body: any = resp.data;
  if (body && typeof body === "object" && "data" in body) {
    return (body as any).data as T;
  }
  return body as T;
}

export async function whoAmI(): Promise<WhoAmI> {
  const url = `${base()}/admin/whoami`;
  const { userToken } = userStore.getState();
  const headers: Record<string, string> = {};
  Object.assign(headers, authHeaders());
  if (!headers.Authorization) {
    const raw = String(userToken?.accessToken || "").trim();
    if (raw) {
      headers.Authorization = raw.startsWith("Bearer ") ? raw : `Bearer ${raw}`;
    }
  }
  const resp = await axios.get<ApiEnvelope<WhoAmI>>(url, { withCredentials: false, headers });
  return unwrap<WhoAmI>(resp) ?? { allowed: false, role: null, username: null, email: null };
}

export type OrgNode = { id: number; name: string; parentId?: number; children?: OrgNode[] };

export async function listOrgs(): Promise<OrgNode[]> {
  // Prefer platform-friendly endpoint that is permitted without triad token
  const url = `${base()}/platform/orgs`;
  const resp = await axios.get<ApiEnvelope<OrgNode[]>>(url, { withCredentials: false, headers: authHeaders() });
  const arr = unwrap<OrgNode[]>(resp);
  return Array.isArray(arr) ? arr : [];
}

export default { whoAmI, listOrgs };
