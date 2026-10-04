import type { WorkbenchPreferenceItem } from "@/api/services/workbenchService";

const WORKBENCH_LOCAL_PREFERENCES_KEY = "dts.platform.workbench.preferences.v1";

type WorkbenchLocalPreferenceRecord = {
	items: WorkbenchPreferenceItem[];
	updatedAt: string;
};

type WorkbenchLocalPreferenceStore = {
	version: 1;
	users: Record<string, WorkbenchLocalPreferenceRecord>;
};

function currentStorage(storage?: Storage | null): Storage | null {
	if (storage) return storage;
	if (typeof window === "undefined") return null;
	try {
		return window.localStorage;
	} catch {
		return null;
	}
}

function emptyStore(): WorkbenchLocalPreferenceStore {
	return {
		version: 1,
		users: {},
	};
}

function readStore(storage?: Storage | null): WorkbenchLocalPreferenceStore {
	const target = currentStorage(storage);
	if (!target) return emptyStore();
	try {
		const raw = target.getItem(WORKBENCH_LOCAL_PREFERENCES_KEY);
		if (!raw) return emptyStore();
		const parsed = JSON.parse(raw) as Partial<WorkbenchLocalPreferenceStore>;
		if (!parsed || typeof parsed !== "object" || typeof parsed.users !== "object" || !parsed.users) {
			return emptyStore();
		}
		return {
			version: 1,
			users: parsed.users as Record<string, WorkbenchLocalPreferenceRecord>,
		};
	} catch {
		return emptyStore();
	}
}

function writeStore(store: WorkbenchLocalPreferenceStore, storage?: Storage | null): void {
	const target = currentStorage(storage);
	if (!target) return;
	try {
		target.setItem(WORKBENCH_LOCAL_PREFERENCES_KEY, JSON.stringify(store));
	} catch {
		// Local persistence is only a compatibility fallback.
	}
}

function normalizeOwner(raw: unknown): string {
	const text = String(raw ?? "").trim().toLowerCase();
	if (!text) return "anonymous";
	return text.replace(/\s+/g, "_").slice(0, 128);
}

export function resolveWorkbenchPreferenceOwner(userInfo: unknown): string {
	if (!userInfo || typeof userInfo !== "object") return "anonymous";
	const info = userInfo as Record<string, unknown>;
	const candidates = [info.username, info.login, info.email, info.id, info.preferredUsername, info.preferred_username];
	for (const candidate of candidates) {
		const owner = normalizeOwner(candidate);
		if (owner !== "anonymous") return owner;
	}
	return "anonymous";
}

function normalizeItems(items: WorkbenchPreferenceItem[]): WorkbenchPreferenceItem[] {
	return items
		.filter((item) => typeof item?.key === "string" && item.key.trim().length > 0)
		.map((item, index) => ({
			key: item.key,
			visible: item.visible !== false,
			order: Number.isFinite(item.order) ? item.order : (index + 1) * 10,
		}));
}

export function readLocalWorkbenchPreferenceItems(owner: string, storage?: Storage | null): WorkbenchPreferenceItem[] | null {
	const record = readStore(storage).users[normalizeOwner(owner)];
	if (!record || !Array.isArray(record.items)) return null;
	return normalizeItems(record.items);
}

export function saveLocalWorkbenchPreferenceItems(
	owner: string,
	items: WorkbenchPreferenceItem[],
	storage?: Storage | null,
): void {
	const store = readStore(storage);
	store.users[normalizeOwner(owner)] = {
		items: normalizeItems(items),
		updatedAt: new Date().toISOString(),
	};
	writeStore(store, storage);
}

export function resetLocalWorkbenchPreferenceItems(owner: string, storage?: Storage | null): void {
	const store = readStore(storage);
	delete store.users[normalizeOwner(owner)];
	writeStore(store, storage);
}
