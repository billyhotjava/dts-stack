const RETURN_TO_PREFIXES = [
	"/data-modeling",
	"/modeling",
	"/governance",
	"/studio",
	"/ops",
	"/workbench",
	"/foundation",
	"/catalog",
	"/services",
	"/bi",
] as const;

export const sanitizeModelingReturnTo = (input: string | null | undefined): string | undefined => {
	const candidate = input?.trim();
	if (!candidate || !candidate.startsWith("/") || candidate.startsWith("//") || candidate.includes("\\")) {
		return undefined;
	}
	try {
		const url = new URL(candidate, "http://dts.local");
		if (url.origin !== "http://dts.local") return undefined;
		if (!RETURN_TO_PREFIXES.some((prefix) => url.pathname === prefix || url.pathname.startsWith(`${prefix}/`))) {
			return undefined;
		}
		return `${url.pathname}${url.search}${url.hash}`;
	} catch {
		return undefined;
	}
};
