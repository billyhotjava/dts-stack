import { faker } from "@faker-js/faker";
import { HttpResponse, http } from "msw";
import { ResultStatus } from "#/enum";
import type { AccessPolicy, DatasetAsset, MaskingRule, SecurityLevel, SourceType } from "@/types/catalog";

const API_BASE = "/api/catalog";

// ---- In-memory demo data ----

type DomainItem = { id: string; name: string; code: string; owner: string; description?: string };

// Flat domains for list page
let domains: DomainItem[] = [
	{ id: "dom_core", name: "企业核心域", code: "CORE", owner: "陈伟", description: "覆盖企业战略与核心经营指标" },
	{ id: "dom_shared", name: "共享域", code: "SHARED", owner: "刘敏", description: "主/参公共域" },
];

// Tree for domain hierarchy
type TreeNode = { id: string; name: string; owner?: string; description?: string; children?: TreeNode[] };
let domainTree: TreeNode[] = [
	{
		id: "domain-core",
		name: "企业核心域",
		owner: "陈伟",
		children: [
			{
				id: "domain-core-ops",
				name: "经营分析主题",
				owner: "陈伟",
				children: [
					{ id: "domain-core-ops-sales", name: "销售运营", owner: "周丽" },
					{ id: "domain-core-ops-supply", name: "供应链管理", owner: "张强" },
				],
			},
			{ id: "domain-core-risk", name: "风控主题", owner: "李云" },
		],
	},
	{
		id: "domain-shared",
		name: "共享域",
		owner: "刘敏",
		children: [
			{ id: "domain-shared-master", name: "主数据主题", owner: "刘敏" },
			{ id: "domain-shared-ref", name: "参考数据主题", owner: "赵倩" },
		],
	},
];

// Helper to find and move nodes in domainTree
function findNode(
	path: TreeNode[],
	id: string,
	parent: TreeNode | null = null,
): { node?: TreeNode; parent?: TreeNode | null } {
	for (const n of path) {
		if (n.id === id) return { node: n, parent };
		if (n.children?.length) {
			const r = findNode(n.children, id, n);
			if (r.node) return r;
		}
	}
	return {};
}

// Seed datasets
const levels: SecurityLevel[] = ["PUBLIC", "INTERNAL", "SECRET", "TOP_SECRET"];
const srcTypes: SourceType[] = ["HIVE", "TRINO", "EXTERNAL", "API"];

let datasets: DatasetAsset[] = Array.from({ length: 14 }).map((_, i) => {
	const id = `ds_${i + 1}`;
	const domainIds = [
		"domain-core-ops-sales",
		"domain-core-ops-supply",
		"domain-core-risk",
		"domain-shared-master",
		"domain-shared-ref",
	];
	const sourceType = faker.helpers.arrayElement(srcTypes);
	return {
		id,
		name:
			faker.helpers.arrayElement(["ods_customers", "dwd_orders", "dws_gmv_daily", "dim_products", "ads_sales_report"]) +
			`_${(i % 3) + 1}`,
		owner: faker.person.fullName(),
		bizDomainId: faker.helpers.arrayElement(domainIds),
		classification: faker.helpers.arrayElement(levels),
		tags: faker.helpers.arrayElements(["销售", "KPI", "客户", "订单", "主数据"], { min: 0, max: 3 }),
		description: faker.lorem.sentence(),
		source: {
			sourceType,
			hiveDatabase: sourceType === "HIVE" ? "ods" : undefined,
			hiveTable: sourceType === "HIVE" ? `t_${i + 1}` : undefined,
			trinoCatalog: sourceType === "TRINO" ? "hive" : undefined,
		},
		exposure: [faker.helpers.arrayElement(["VIEW", "RANGER", "API", "DIRECT"])],
		table: {
			tableName: `tbl_${i + 1}`,
			columns: [
				{ id: `${id}_c1`, name: "id", dataType: "STRING" },
				{ id: `${id}_c2`, name: "created_at", dataType: "TIMESTAMP" },
				{ id: `${id}_c3`, name: "customer_name", dataType: "STRING", sensitiveTags: ["PII:name"] },
				{ id: `${id}_c4`, name: "mobile", dataType: "STRING", sensitiveTags: ["PII:phone"] },
			],
		},
		createdAt: faker.date.recent({ days: 30 }).toISOString(),
		updatedAt: faker.date.recent({ days: 7 }).toISOString(),
	} satisfies DatasetAsset;
});

let policies = new Map<string, AccessPolicy>();
function ensurePolicy(datasetId: string): AccessPolicy {
	const existing = policies.get(datasetId);
	if (existing) return existing;
	const created: AccessPolicy = {
		datasetId,
		allowRoles: ["ROLE_PUBLIC", "ROLE_INTERNAL"],
		rowFilters: [],
		maskingRules: [
			{ id: `${datasetId}_m1`, column: "mobile", strategy: "PARTIAL", params: { keepHead: "3", keepTail: "2" } },
		],
		defaultMasking: "NONE",
	};
	policies.set(datasetId, created);
	return created;
}

// Build SQL with simple masking/filters for preview
function buildViewSQL(ds: DatasetAsset, level: SecurityLevel) {
	const p = ensurePolicy(ds.id);
	const cols = ds.table?.columns || [];
	const maskCols = new Set<string>();
	for (const r of p.maskingRules) maskCols.add(r.column);

	const selectList = cols.map((c) => {
		if (maskCols.has(c.name)) {
			// Render a pseudo masking function
			const rule = p.maskingRules.find((r) => r.column === c.name);
			const fn =
				rule?.strategy === "HASH"
					? `HASH(${c.name})`
					: rule?.strategy === "TOKENIZE"
						? `TOKENIZE(${c.name})`
						: rule?.strategy === "CUSTOM"
							? `CUSTOM(${c.name})`
							: `MASK_PARTIAL(${c.name})`;
			return `${fn} AS ${c.name}`;
		}
		if ((c.sensitiveTags?.length || 0) > 0 && p.defaultMasking && p.defaultMasking !== "NONE") {
			return `${p.defaultMasking}(${c.name}) AS ${c.name}`;
		}
		return c.name;
	});

	const fromTable =
		ds.source?.hiveDatabase && ds.source?.hiveTable
			? `${ds.source.hiveDatabase}.${ds.source.hiveTable}`
			: ds.table?.tableName || ds.name;
	const filter = p.rowFilters[0]?.expression; // MVP: one effective filter
	const where = filter ? `\nWHERE ${filter}` : "";
	return `CREATE OR REPLACE VIEW ${ds.name}_${level.toLowerCase()} AS\nSELECT ${selectList.join(", ")}\nFROM ${fromTable}${where};`;
}

export const catalogHandlers = [
	// ---- Domains: list (paged) ----
	http.get(`${API_BASE}/domains`, ({ request }) => {
		const url = new URL(request.url);
		const page = Number(url.searchParams.get("page") || 0);
		const size = Number(url.searchParams.get("size") || 10);
		const keyword = (url.searchParams.get("keyword") || "").toLowerCase();
		const filtered = keyword
			? domains.filter(
					(d) =>
						d.name.toLowerCase().includes(keyword) ||
						d.code.toLowerCase().includes(keyword) ||
						d.owner.toLowerCase().includes(keyword),
				)
			: domains;
		const start = page * size;
		const content = filtered.slice(start, start + size);
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: { content, total: filtered.length, page, size } });
	}),

	// ---- Domains: create/update/delete ----
	http.post(`${API_BASE}/domains`, async ({ request }) => {
		const body = (await request.json()) as Partial<DomainItem>;
		const id = `dom_${Date.now()}`;
		const item: DomainItem = {
			id,
			name: body.name || "",
			code: body.code || `D_${Date.now()}`,
			owner: body.owner || "",
		};
		domains = [item, ...domains];
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: item });
	}),
	http.put(`${API_BASE}/domains/:id`, async ({ params, request }) => {
		const id = String(params.id);
		const body = (await request.json()) as Partial<DomainItem>;
		domains = domains.map((d) => (d.id === id ? { ...d, ...body } : d));
		const found = domains.find((d) => d.id === id);
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: found });
	}),
	http.delete(`${API_BASE}/domains/:id`, ({ params }) => {
		const id = String(params.id);
		domains = domains.filter((d) => d.id !== id);
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: true });
	}),

	// ---- Domain tree and moving nodes ----
	http.get(`${API_BASE}/domains/tree`, () => {
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: domainTree });
	}),
	http.post(`${API_BASE}/domains/:id/move`, async ({ params, request }) => {
		const id = String(params.id);
		const body = (await request.json()) as { newParentId?: string | null };
		const from = findNode(domainTree, id);
		if (!from.node) return HttpResponse.json({ status: ResultStatus.ERROR, message: "not found" }, { status: 404 });
		// Remove from old parent
		if (from.parent) from.parent.children = (from.parent.children || []).filter((n) => n.id !== id);
		else domainTree = domainTree.filter((n) => n.id !== id);
		// Attach to new parent
		if (body.newParentId) {
			const to = findNode(domainTree, body.newParentId);
			if (to.node) to.node.children = [...(to.node.children || []), from.node];
			else domainTree.push(from.node);
		} else {
			domainTree.push(from.node);
		}
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: true });
	}),

	// ---- Datasets list (paged) ----
	http.get(`${API_BASE}/datasets`, ({ request }) => {
		const url = new URL(request.url);
		const page = Number(url.searchParams.get("page") || 0);
		const size = Number(url.searchParams.get("size") || 10);
		const keyword = (url.searchParams.get("keyword") || "").toLowerCase();
		const filtered = keyword
			? datasets.filter((d) => d.name.toLowerCase().includes(keyword) || d.owner.toLowerCase().includes(keyword))
			: datasets;
		const start = page * size;
		const content = filtered.slice(start, start + size).map((d) => ({
			id: d.id,
			name: d.name,
			domainId: d.bizDomainId,
			owner: d.owner,
			classification: d.classification,
			type: d.source.sourceType,
		}));
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: { content, total: filtered.length, page, size } });
	}),

	// Dataset CRUD
	http.get(`${API_BASE}/datasets/:id`, ({ params }) => {
		const id = String(params.id);
		const found = datasets.find((d) => d.id === id);
		if (!found) return HttpResponse.json({ status: ResultStatus.ERROR, message: "not found" }, { status: 404 });
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: found });
	}),
	http.post(`${API_BASE}/datasets`, async ({ request }) => {
		const body = (await request.json()) as Partial<DatasetAsset>;
		const id = body.id || `ds_${Date.now()}`;
		const now = new Date().toISOString();
		const item: DatasetAsset = {
			id,
			name: body.name || id,
			owner: body.owner || "",
			bizDomainId: body.bizDomainId || "domain-shared-ref",
			classification: (body.classification as SecurityLevel) || "INTERNAL",
			tags: Array.isArray(body.tags) ? body.tags : [],
			description: body.description || "",
			source: body.source || { sourceType: "EXTERNAL" },
			exposure: Array.isArray(body.exposure) ? (body.exposure as any) : ["VIEW"],
			table: body.table || { tableName: body.name || id, columns: [] },
			createdAt: now,
			updatedAt: now,
		};
		datasets = [item, ...datasets];
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: item });
	}),
	http.put(`${API_BASE}/datasets/:id`, async ({ params, request }) => {
		const id = String(params.id);
		const body = (await request.json()) as Partial<DatasetAsset> & { domain?: { id?: string | number } };
		datasets = datasets.map((d) => {
			if (d.id !== id) return d;
			const domainUpdated = body?.domain?.id ? { bizDomainId: String(body.domain.id) } : {};
			return { ...d, ...body, ...domainUpdated, updatedAt: new Date().toISOString() } as DatasetAsset;
		});
		const found = datasets.find((d) => d.id === id);
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: found });
	}),
	http.delete(`${API_BASE}/datasets/:id`, ({ params }) => {
		const id = String(params.id);
		datasets = datasets.filter((d) => d.id !== id);
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: true });
	}),

	// Access policies
	http.get(`${API_BASE}/access-policies`, ({ request }) => {
		const url = new URL(request.url);
		const datasetId = String(url.searchParams.get("datasetId") || "");
		if (!datasetId)
			return HttpResponse.json({ status: ResultStatus.ERROR, message: "datasetId required" }, { status: 400 });
		const p = ensurePolicy(datasetId);
		// Flatten for UI: allowRoles CSV and single rowFilter for MVP
		const resp = {
			datasetId,
			allowRoles: p.allowRoles.join(","),
			rowFilter: p.rowFilters[0]?.expression || "",
			defaultMasking: p.defaultMasking || "NONE",
		};
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: resp });
	}),
	http.put(`${API_BASE}/access-policies/:datasetId`, async ({ params, request }) => {
		const datasetId = String(params.datasetId);
		const body = (await request.json()) as { allowRoles?: string; rowFilter?: string; defaultMasking?: string };
		const p = ensurePolicy(datasetId);
		p.allowRoles = (body.allowRoles || "")
			.split(",")
			.map((s) => s.trim())
			.filter(Boolean);
		p.rowFilters =
			body.rowFilter && body.rowFilter.trim()
				? [{ id: `${datasetId}_rf_1`, expression: body.rowFilter.trim(), roles: p.allowRoles }]
				: [];
		if (body.defaultMasking) p.defaultMasking = body.defaultMasking as any;
		policies.set(datasetId, p);
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: true });
	}),

	// Security views preview
	http.get(`${API_BASE}/security-views/:datasetId/preview`, ({ params }) => {
		const id = String(params.datasetId);
		const ds = datasets.find((d) => d.id === id);
		if (!ds) return HttpResponse.json({ status: ResultStatus.ERROR, message: "not found" }, { status: 404 });
		const result = {
			public: buildViewSQL(ds, "PUBLIC"),
			internal: buildViewSQL(ds, "INTERNAL"),
			secret: buildViewSQL(ds, "SECRET"),
			top_secret: buildViewSQL(ds, "TOP_SECRET"),
		};
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: result });
	}),

	// Masking rules registry (global)
	http.get(`${API_BASE}/masking-rules`, () => {
		const all: MaskingRule[] = [];
		for (const p of policies.values()) all.push(...p.maskingRules);
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: all });
	}),
	http.post(`${API_BASE}/masking-rules`, async ({ request }) => {
		const rule = (await request.json()) as MaskingRule & { datasetId?: string };
		const dsId = rule?.datasetId || datasets[0]?.id;
		if (!dsId) return HttpResponse.json({ status: ResultStatus.ERROR, message: "no dataset" }, { status: 400 });
		const p = ensurePolicy(dsId);
		const id = `mr_${Date.now()}`;
		const created: MaskingRule = { id, column: rule.column, strategy: rule.strategy, params: rule.params };
		p.maskingRules.push(created);
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: created });
	}),
	http.put(`${API_BASE}/masking-rules/:id`, async ({ params, request }) => {
		const id = String(params.id);
		const patch = (await request.json()) as Partial<MaskingRule>;
		for (const p of policies.values()) {
			p.maskingRules = p.maskingRules.map((r) => (r.id === id ? ({ ...r, ...patch } as MaskingRule) : r));
		}
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: true });
	}),
	http.delete(`${API_BASE}/masking-rules/:id`, ({ params }) => {
		const id = String(params.id);
		for (const p of policies.values()) p.maskingRules = p.maskingRules.filter((r) => r.id !== id);
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: true });
	}),
	http.post(`${API_BASE}/masking-rules/preview`, async ({ request }) => {
		const { value, strategy } = (await request.json()) as { value: string; strategy: MaskingRule["strategy"] };
		const masked =
			strategy === "HASH"
				? `hash(${value})`
				: strategy === "TOKENIZE"
					? `tk(${value})`
					: strategy === "PARTIAL"
						? `${value.slice(0, 3)}***${value.slice(-2)}`
						: strategy === "CUSTOM"
							? `custom(${value})`
							: value;
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: { original: value, masked } });
	}),

	// Classification mapping placeholders
	http.get(`${API_BASE}/classification-mapping`, () => {
		const mapping = datasets.map((d) => ({ id: d.id, name: d.name, classification: d.classification }));
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: mapping });
	}),
	http.put(`${API_BASE}/classification-mapping`, async ({ request }) => {
		const next = (await request.json()) as { id: string; classification: SecurityLevel }[];
		datasets = datasets.map((d) => {
			const change = next.find((x) => x.id === d.id);
			return change ? { ...d, classification: change.classification, updatedAt: new Date().toISOString() } : d;
		});
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: true });
	}),
	http.post(`${API_BASE}/classification-mapping/import`, async ({ request }) => {
		const list = (await request.json()) as any[];
		// noop in mock
		return HttpResponse.json({ status: ResultStatus.SUCCESS, data: { imported: list.length } });
	}),
	http.get(`${API_BASE}/classification-mapping/export`, () => {
		const csv = ["id,name,classification", ...datasets.map((d) => `${d.id},${d.name},${d.classification}`)].join("\n");
		return new HttpResponse(csv, { headers: { "Content-Type": "text/csv" } });
	}),
];
