import { createDomain, deleteDomain, getDomainTree, updateDomain } from "@/api/platformApi";

export type PlanningCatalogDomain = {
	id: string;
	code: string;
	name: string;
	owner: string;
	description: string;
	parentId: string | null;
	parentCode: string | null;
};

export type PlanningCatalogDomainInput = {
	code: string;
	name: string;
	owner?: string;
	description?: string;
	parentId?: string | null;
};

const text = (value: unknown): string => (value == null ? "" : String(value).trim());

const domainNodes = (raw: unknown): unknown[] => {
	if (Array.isArray(raw)) return raw;
	if (!raw || typeof raw !== "object") return [];
	const record = raw as Record<string, unknown>;
	if (Array.isArray(record.tree)) return record.tree;
	if (Array.isArray(record.data)) return record.data;
	return [];
};

export function normalizePlanningCatalogDomains(raw: unknown): PlanningCatalogDomain[] {
	const result: PlanningCatalogDomain[] = [];
	const visit = (nodes: unknown[], parent: PlanningCatalogDomain | null) => {
		for (const value of nodes) {
			if (!value || typeof value !== "object") continue;
			const node = value as Record<string, unknown>;
			const id = text(node.id);
			const code = text(node.code ?? node.key);
			const name = text(node.name ?? node.nameZh ?? node.label);
			if (!id || !code || !name) continue;
			const normalized: PlanningCatalogDomain = {
				id,
				code,
				name,
				owner: text(node.owner),
				description: text(node.description),
				parentId: parent?.id || null,
				parentCode: parent?.code || null,
			};
			result.push(normalized);
			if (Array.isArray(node.children)) visit(node.children, normalized);
		}
	};
	visit(domainNodes(raw), null);
	return result;
}

export const listPlanningCatalogDomains = async (): Promise<PlanningCatalogDomain[]> =>
	normalizePlanningCatalogDomains(await getDomainTree());

const writePayload = (input: PlanningCatalogDomainInput) => ({
	code: input.code.trim(),
	name: input.name.trim(),
	owner: input.owner?.trim() || undefined,
	description: input.description?.trim() || undefined,
	parent: input.parentId ? { id: input.parentId } : undefined,
});

export const createPlanningCatalogDomain = (input: PlanningCatalogDomainInput) => createDomain(writePayload(input));

export const updatePlanningCatalogDomain = (id: string, input: PlanningCatalogDomainInput) =>
	updateDomain(id, writePayload(input));

export const deletePlanningCatalogDomain = (id: string) => deleteDomain(id);
