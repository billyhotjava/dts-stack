import apiClient from "../apiClient";

export interface UserDirectoryEntry {
	id: string;
	username: string;
	displayName?: string;
	fullName?: string;
	deptCode?: string;
	deptName?: string;
}

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
		const payload = await apiClient.get<any>({ url: "/directory/users", params });
		const list = unwrap(payload);
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
				const deptNameRaw = item?.deptName ?? item?.departmentName ?? item?.dept_name ?? item?.org_name;
				const deptName = typeof deptNameRaw === "string" ? deptNameRaw.trim() : "";
				return {
					id,
					username,
					displayName,
					fullName: rawFullName || undefined,
					deptCode: deptCode || undefined,
					deptName: deptName || undefined,
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
