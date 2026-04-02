function normalizePathname(pathname: string): string {
	return String(pathname || "").split("?")[0].trim();
}

export function shouldPrefetchPortalMenus(pathname: string, accessToken?: string | null): boolean {
	const normalizedPathname = normalizePathname(pathname);
	const normalizedToken = String(accessToken || "").trim();
	if (!normalizedToken) {
		return false;
	}
	return normalizedPathname !== "/auth/login" && !normalizedPathname.endsWith("/auth/login");
}
