import axios from "axios";
import { GLOBAL_CONFIG } from "@/global-config";
import userStore from "@/store/userStore";
import useContextStore from "@/store/contextStore";

export interface UserDirectoryEntry {
  id: string;
  username: string;
  displayName?: string;
  fullName?: string;
  deptCode?: string;
}

const API_BASE = GLOBAL_CONFIG.apiBaseUrl.replace(/\/+$/, "");

const buildHeaders = (): Record<string, string> => {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  try {
    const { userToken } = userStore.getState();
    const raw = String(userToken?.accessToken || "").trim();
    if (raw) {
      headers.Authorization = raw.startsWith("Bearer ") ? raw : `Bearer ${raw}`;
    }
    const ctx = useContextStore.getState();
    if (ctx.activeDept) {
      headers["X-Active-Dept"] = String(ctx.activeDept);
    }
  } catch {
    // ignore store access issues
  }
  return headers;
};

const unwrap = (payload: any): any[] => {
  if (Array.isArray(payload)) return payload;
  if (payload && Array.isArray(payload.data)) return payload.data;
  return [];
};

export async function searchUsers(keyword?: string): Promise<UserDirectoryEntry[]> {
  try {
    const params: Record<string, string> = {};
    if (keyword && keyword.trim()) {
      params.keyword = keyword.trim();
    }
    const { data } = await axios.get(`${API_BASE}/directory/users`, {
      headers: buildHeaders(),
      params,
      withCredentials: false,
    });
    const list = unwrap(data);
    return list
      .map((item: any) => {
        const id = String(item?.id ?? item?.userId ?? item?.username ?? "").trim();
        const username = String(item?.username ?? "").trim();
        if (!id || !username) {
          return null;
        }
        const rawFullName = String(item?.fullName ?? item?.name ?? "").trim();
        const fallbackDisplay = rawFullName || username;
        const displayName = String((item?.displayName ?? fallbackDisplay) || username).trim();
        const deptCodeRaw = item?.deptCode ?? item?.department ?? item?.dept_code;
        const deptCode = typeof deptCodeRaw === "string" ? deptCodeRaw.trim() : "";
        return {
          id,
          username,
          displayName,
          fullName: rawFullName || undefined,
          deptCode: deptCode || undefined,
        } as UserDirectoryEntry;
      })
      .filter((it: UserDirectoryEntry | null): it is UserDirectoryEntry => Boolean(it));
  } catch (error) {
    console.warn("[userDirectoryService] searchUsers failed:", (error as any)?.message || error);
    return [];
  }
}

export default {
  searchUsers,
};
