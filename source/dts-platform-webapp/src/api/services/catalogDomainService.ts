import { getDomainTree, listDomains } from "@/api/platformApi";

/**
 * Lightweight catalog-domain service used by the leader workbench filter bar
 * (sprint-15/F3). Wraps the existing `platformApi.getDomainTree` call and
 * normalizes the raw response into `{ code, name }` pairs.
 *
 * Tree API response (raw) follows `{ key | id, name, children? }` — see
 * `useCatalogDomainOptions.ts` for the reference normalization.
 */

export interface CatalogDomain {
	id: string;
	code: string;
	name: string;
	parentCode?: string | null;
}

function pickKey(raw: unknown): string {
	if (!raw || typeof raw !== "object") return "";
	const obj = raw as Record<string, unknown>;
	const candidate = obj.code ?? obj.key ?? obj.id;
	if (candidate == null) return "";
	const s = String(candidate).trim();
	return s;
}

function pickId(raw: unknown): string {
	if (!raw || typeof raw !== "object") return "";
	const obj = raw as Record<string, unknown>;
	const candidate = obj.id ?? obj.key ?? obj.code;
	if (candidate == null) return "";
	const s = String(candidate).trim();
	return s;
}

function pickName(raw: unknown): string {
	if (!raw || typeof raw !== "object") return "";
	const obj = raw as Record<string, unknown>;
	const candidate = obj.name ?? obj.nameZh ?? obj.label;
	if (typeof candidate === "string") return candidate.trim();
	return "";
}

function flattenTree(nodes: unknown, parentCode: string | null, out: CatalogDomain[]): void {
	if (!Array.isArray(nodes)) return;
	for (const node of nodes) {
		const code = pickKey(node);
		const name = pickName(node);
		if (!code || !name) continue;
		out.push({ id: pickId(node), code, name, parentCode });
		const children = (node as Record<string, unknown>).children;
		if (Array.isArray(children) && children.length) {
			flattenTree(children, code, out);
		}
	}
}

function extractListPayload(raw: unknown): unknown {
	if (Array.isArray(raw)) return raw;
	if (raw && typeof raw === "object") {
		const obj = raw as Record<string, unknown>;
		if (Array.isArray(obj.data)) return obj.data;
		const inner = obj.data;
		if (inner && typeof inner === "object" && Array.isArray((inner as Record<string, unknown>).content)) {
			return (inner as Record<string, unknown>).content;
		}
		if (Array.isArray(obj.content)) return obj.content;
	}
	return [];
}

async function fetchFromTree(): Promise<CatalogDomain[]> {
	const raw = await getDomainTree();
	const payload = extractListPayload(raw);
	const out: CatalogDomain[] = [];
	flattenTree(payload, null, out);
	return out;
}

async function fetchFromList(): Promise<CatalogDomain[]> {
	const raw = await listDomains(0, 500, "");
	const payload = extractListPayload(raw);
	const out: CatalogDomain[] = [];
	if (Array.isArray(payload)) {
		for (const node of payload) {
			const code = pickKey(node);
			const name = pickName(node);
			if (!code || !name) continue;
			out.push({ id: pickId(node), code, name, parentCode: null });
		}
	}
	return out;
}

async function list(): Promise<CatalogDomain[]> {
	try {
		const tree = await fetchFromTree();
		if (tree.length > 0) return tree;
		// Tree call succeeded but returned no items — try the flat list as a fallback.
		return await fetchFromList();
	} catch (treeError) {
		// Fallback to the flat list once before surfacing the failure.
		try {
			return await fetchFromList();
		} catch {
			throw treeError;
		}
	}
}

const catalogDomainService = {
	list,
};

export default catalogDomainService;
