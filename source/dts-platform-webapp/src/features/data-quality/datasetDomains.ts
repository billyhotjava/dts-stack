export const UNASSIGNED_DOMAIN_ID = "UNASSIGNED";

const text = (value: unknown) => String(value ?? "").trim();

export const normalizeDatasetDomain = (dataset: Record<string, unknown>) => {
	const rawDomain = dataset.domain;
	const domain = rawDomain && typeof rawDomain === "object" ? (rawDomain as Record<string, unknown>) : undefined;
	const domainId = text(dataset.domainId ?? domain?.id);
	const domainName = text(
		dataset.domainName ?? domain?.name ?? (typeof rawDomain === "string" ? rawDomain : undefined),
	);
	if (!domainId && !domainName) {
		return { domainId: UNASSIGNED_DOMAIN_ID, domainName: "未归属业务域" };
	}
	return { domainId: domainId || domainName, domainName: domainName || domainId };
};

export const filterDatasetsByDomain = <T extends { domainId?: string }>(datasets: T[], domainId?: string) =>
	domainId ? datasets.filter((dataset) => (dataset.domainId || UNASSIGNED_DOMAIN_ID) === domainId) : datasets;
