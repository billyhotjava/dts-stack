export const BUSINESS_PROCESS_VERSION = 1 as const;
export const BUSINESS_PROCESS_STORAGE_KEY = "dts.business-processes.v1";

export type BusinessProcess = {
	version: typeof BUSINESS_PROCESS_VERSION;
	processId: string;
	domainId: string;
	name: string;
	description?: string;
	createdAt: string;
	updatedAt: string;
};

export type BusinessProcessStorage = Pick<Storage, "getItem" | "setItem" | "removeItem">;

export type BusinessProcessInput = {
	processId: string;
	domainId: string;
	name: string;
	description?: string;
};

type BusinessProcessStore = {
	version: typeof BUSINESS_PROCESS_VERSION;
	items: BusinessProcess[];
};

export const BUSINESS_PROCESS_SEEDS: Omit<BusinessProcessInput, "domainId">[] = [
	{ processId: "node-plan-loop", name: "节点计划闭环", description: "从计划提出、执行跟踪到节点验收的业务过程。" },
	{ processId: "quality-zero", name: "质量问题归零", description: "从质量问题发现、处置到验证关闭的业务过程。" },
	{ processId: "risk-release", name: "风险提出与释放", description: "从风险识别、评估到释放或升级的业务过程。" },
];

const defaultStorage = (): BusinessProcessStorage | undefined => {
	try {
		return typeof window === "undefined" ? undefined : window.sessionStorage;
	} catch {
		return undefined;
	}
};

const normalize = (value: Partial<BusinessProcess>): BusinessProcess | null => {
	if (
		value.version !== BUSINESS_PROCESS_VERSION ||
		!String(value.processId || "").trim() ||
		!String(value.domainId || "").trim() ||
		!String(value.name || "").trim() ||
		!String(value.createdAt || "").trim() ||
		!String(value.updatedAt || "").trim()
	) {
		return null;
	}
	return {
		version: BUSINESS_PROCESS_VERSION,
		processId: String(value.processId).trim(),
		domainId: String(value.domainId).trim(),
		name: String(value.name).trim(),
		description: value.description ? String(value.description).trim() : undefined,
		createdAt: String(value.createdAt),
		updatedAt: String(value.updatedAt),
	};
};

const readStore = (storage: BusinessProcessStorage | undefined): BusinessProcessStore => {
	if (!storage) return { version: BUSINESS_PROCESS_VERSION, items: [] };
	try {
		const raw = storage.getItem(BUSINESS_PROCESS_STORAGE_KEY);
		if (!raw) return { version: BUSINESS_PROCESS_VERSION, items: [] };
		const parsed = JSON.parse(raw) as Partial<BusinessProcessStore>;
		if (parsed.version !== BUSINESS_PROCESS_VERSION || !Array.isArray(parsed.items)) {
			return { version: BUSINESS_PROCESS_VERSION, items: [] };
		}
		return {
			version: BUSINESS_PROCESS_VERSION,
			items: parsed.items.map((item) => normalize(item)).filter(Boolean) as BusinessProcess[],
		};
	} catch {
		return { version: BUSINESS_PROCESS_VERSION, items: [] };
	}
};

export const createBusinessProcess = (
	input: BusinessProcessInput,
	now: () => string = () => new Date().toISOString(),
): BusinessProcess => {
	const timestamp = now();
	return {
		version: BUSINESS_PROCESS_VERSION,
		processId: input.processId.trim(),
		domainId: input.domainId.trim(),
		name: input.name.trim(),
		description: input.description?.trim() || undefined,
		createdAt: timestamp,
		updatedAt: timestamp,
	};
};

export const loadBusinessProcesses = (
	domainId: string,
	storage: BusinessProcessStorage | undefined = defaultStorage(),
): BusinessProcess[] => readStore(storage).items.filter((item) => item.domainId === domainId);

export const saveBusinessProcesses = (
	domainId: string,
	processes: BusinessProcess[],
	storage: BusinessProcessStorage | undefined = defaultStorage(),
): boolean => {
	if (!storage || !domainId.trim()) return false;
	const current = readStore(storage).items.filter((item) => item.domainId !== domainId);
	const valid = processes.map((item) => normalize(item)).filter(Boolean) as BusinessProcess[];
	try {
		storage.setItem(
			BUSINESS_PROCESS_STORAGE_KEY,
			JSON.stringify({ version: BUSINESS_PROCESS_VERSION, items: [...current, ...valid] } satisfies BusinessProcessStore),
		);
		return true;
	} catch {
		return false;
	}
};

export const adoptBusinessProcessSeeds = (
	domainId: string,
	existing: BusinessProcess[] = loadBusinessProcesses(domainId),
	storage: BusinessProcessStorage | undefined = defaultStorage(),
	): BusinessProcess[] => {
	const timestamp = new Date().toISOString();
	const known = new Set(existing.map((item) => item.processId));
	const next = [
		...existing,
		...BUSINESS_PROCESS_SEEDS.filter((seed) => !known.has(seed.processId)).map((seed) => ({
			...seed,
			version: BUSINESS_PROCESS_VERSION,
			domainId,
			createdAt: timestamp,
			updatedAt: timestamp,
		})),
	];
	saveBusinessProcesses(domainId, next, storage);
	return next;
};

export const removeBusinessProcess = (
	domainId: string,
	processId: string,
	storage: BusinessProcessStorage | undefined = defaultStorage(),
): boolean => saveBusinessProcesses(domainId, loadBusinessProcesses(domainId, storage).filter((item) => item.processId !== processId), storage);
