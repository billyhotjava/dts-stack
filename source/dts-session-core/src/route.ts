export type LocationLike = Pick<Location, "hash" | "pathname" | "search">;

export function currentRoutePath(
	routerHistory: "hash" | "browser",
	location: LocationLike | null | undefined = globalThis.location,
): string {
	if (!location) return "/";
	if (routerHistory === "hash") {
		const hashPath = String(location.hash || "").replace(/^#/, "");
		return hashPath || "/";
	}
	const pathname = String(location.pathname || "/") || "/";
	const search = String(location.search || "");
	return `${pathname}${search}`;
}

export function buildLoginRedirectHref(loginHref: string, returnPath: string): string {
	const separator = loginHref.includes("?") ? "&" : "?";
	return `${loginHref}${separator}redirect=${encodeURIComponent(returnPath)}`;
}
