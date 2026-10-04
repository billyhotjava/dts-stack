export const BUSINESS_PROCESS_VERSION = 1 as const;
export const BUSINESS_PROCESS_STORAGE_KEY = "dts.business-processes.v1";

export type BusinessProcess = {
	version: typeof BUSINESS_PROCESS_VERSION;
	processId: string;
	domainId: string;
	name: string;
	description?: string;
	sourceType?: string;
	sourceId?: string;
	sourceVersion?: string;
	confirmed?: boolean;
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
		...(value.sourceType ? { sourceType: String(value.sourceType).trim() } : {}),
		...(value.sourceId ? { sourceId: String(value.sourceId).trim() } : {}),
		...(value.sourceVersion ? { sourceVersion: String(value.sourceVersion).trim() } : {}),
		...(typeof value.confirmed === "boolean" ? { confirmed: value.confirmed } : {}),
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
			JSON.stringify({
				version: BUSINESS_PROCESS_VERSION,
				items: [...current, ...valid],
			} satisfies BusinessProcessStore),
		);
		return true;
	} catch {
		return false;
	}
};

export const removeBusinessProcess = (
	domainId: string,
	processId: string,
	storage: BusinessProcessStorage | undefined = defaultStorage(),
): boolean =>
	saveBusinessProcesses(
		domainId,
		loadBusinessProcesses(domainId, storage).filter((item) => item.processId !== processId),
		storage,
	);
