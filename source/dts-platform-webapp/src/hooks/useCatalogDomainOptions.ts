import { useEffect, useMemo, useState } from "react";
import { getDomainTree } from "@/api/platformApi";

export type CatalogDomainNode = {
	key: string;
	name: string;
	children?: CatalogDomainNode[];
};

export type CatalogDomainOption = {
	key: string;
	name: string;
	label: string;
	pathNames: string[];
};

let cachedTree: CatalogDomainNode[] | null = null;
let inFlight: Promise<CatalogDomainNode[]> | null = null;

function normalizeNodeKey(value: any): string {
	if (!value || typeof value !== "object") return "";
	if (value.key != null) return String(value.key);
	if (value.id != null) return String(value.id);
	return "";
}

function isDomainNode(value: any): value is { key?: unknown; id?: unknown; name: unknown; children?: unknown } {
	if (!value || typeof value !== "object") return false;
	const k = normalizeNodeKey(value);
	return !!k && typeof (value as any).name === "string";
}

function normalizeTree(raw: any): CatalogDomainNode[] {
	const list = Array.isArray(raw) ? raw : [];
	return list
		.filter(isDomainNode)
		.map((node) => ({
			key: normalizeNodeKey(node),
			name: String((node as any).name),
			children: (node as any).children ? normalizeTree((node as any).children) : undefined,
		}));
}

function flattenTree(nodes: CatalogDomainNode[], pathNames: string[] = [], out: CatalogDomainOption[] = []): CatalogDomainOption[] {
	nodes.forEach((node) => {
		const nextPath = [...pathNames, node.name];
		out.push({
			key: node.key,
			name: node.name,
			label: nextPath.join(" / "),
			pathNames: nextPath,
		});
		if (node.children?.length) {
			flattenTree(node.children, nextPath, out);
		}
	});
	return out;
}

async function loadDomainTree(): Promise<CatalogDomainNode[]> {
	if (cachedTree) return cachedTree;
	if (!inFlight) {
		inFlight = (async () => {
			const raw = await getDomainTree();
			const tree = normalizeTree(raw);
			cachedTree = tree;
			return tree;
		})().finally(() => {
			inFlight = null;
		});
	}
	return inFlight;
}

export function useCatalogDomainOptions() {
	const [tree, setTree] = useState<CatalogDomainNode[]>(cachedTree ?? []);
	const [loading, setLoading] = useState(!cachedTree);
	const [error, setError] = useState<any>(null);

	useEffect(() => {
		let alive = true;
		if (cachedTree) return;
		setLoading(true);
		void loadDomainTree()
			.then((t) => {
				if (!alive) return;
				setTree(t);
				setError(null);
			})
			.catch((e) => {
				if (!alive) return;
				setError(e);
			})
			.finally(() => {
				if (!alive) return;
				setLoading(false);
			});
		return () => {
			alive = false;
		};
	}, []);

	const options = useMemo(() => flattenTree(tree), [tree]);

	const nameByKey = useMemo(() => {
		const map: Record<string, string> = {};
		options.forEach((opt) => {
			map[opt.key] = opt.name;
		});
		return map;
	}, [options]);

	const labelByKey = useMemo(() => {
		const map: Record<string, string> = {};
		options.forEach((opt) => {
			map[opt.key] = opt.label;
		});
		return map;
	}, [options]);

	const keyByName = useMemo(() => {
		const map: Record<string, string> = {};
		options.forEach((opt) => {
			if (!map[opt.name]) map[opt.name] = opt.key;
		});
		return map;
	}, [options]);

	return { tree, options, nameByKey, labelByKey, keyByName, loading, error };
}
