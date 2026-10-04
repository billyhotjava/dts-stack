import type { CreateDimensionModelCommand } from "@/api/modelSpecApi";

const SCHEMA_VERSION = 2 as const;
const STORAGE_PREFIX = "dts:dimension-model:v2:";
const DEFAULT_TTL_MS = 15 * 60 * 1000;

type DraftRecord = {
	schemaVersion: typeof SCHEMA_VERSION;
	operationId: string;
	actorScopeHash: string;
	requestFingerprint: string;
	createdAt: number;
	expiresAt: number;
	command: CreateDimensionModelCommand;
};

export type DimensionModelDraftReadResult =
	| { kind: "FOUND"; command: CreateDimensionModelCommand; requestFingerprint: string }
	| { kind: "MISSING" | "EXPIRED" | "ACTOR_MISMATCH" | "INVALID" };

export type DimensionModelDraftSessionOptions = {
	storage?: Pick<Storage, "getItem" | "setItem" | "removeItem">;
	now?: () => number;
	ttlMs?: number;
	digest?: (value: string) => Promise<string>;
};

const storageKey = (operationId: string) => `${STORAGE_PREFIX}${operationId}`;

const resolveStorage = (storage?: DimensionModelDraftSessionOptions["storage"]) => {
	if (storage) return storage;
	if (typeof window === "undefined") throw new Error("DIMENSION_MODEL_DRAFT_STORAGE_UNAVAILABLE");
	return window.sessionStorage;
};

const stableValue = (value: unknown): unknown => {
	if (Array.isArray(value)) return value.map(stableValue);
	if (value && typeof value === "object") {
		return Object.fromEntries(
			Object.entries(value as Record<string, unknown>)
				.sort(([left], [right]) => left.localeCompare(right))
				.map(([key, nested]) => [key, stableValue(nested)]),
		);
	}
	return value;
};

const defaultDigest = async (value: string) => {
	const bytes = new TextEncoder().encode(value);
	const digest = await crypto.subtle.digest("SHA-256", bytes);
	return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
};

export const fingerprintDimensionModelCommand = (
	command: CreateDimensionModelCommand,
	digest: (value: string) => Promise<string> = defaultDigest,
) => digest(JSON.stringify(stableValue(command)));

const hashActorScope = (actorScope: string, digest: (value: string) => Promise<string>) =>
	digest(`dimension-model-actor-v2:${actorScope}`);

const isRecord = (value: unknown): value is Record<string, unknown> =>
	Boolean(value && typeof value === "object" && !Array.isArray(value));

const isDraftRecord = (value: unknown, operationId: string): value is DraftRecord => {
	if (!isRecord(value) || value.schemaVersion !== SCHEMA_VERSION || value.operationId !== operationId) return false;
	return (
		typeof value.actorScopeHash === "string" &&
		typeof value.requestFingerprint === "string" &&
		typeof value.createdAt === "number" &&
		typeof value.expiresAt === "number" &&
		isRecord(value.command) &&
		value.command.operationId === operationId
	);
};

export async function persistDimensionModelDraft(
	command: CreateDimensionModelCommand,
	actorScope: string,
	options: DimensionModelDraftSessionOptions = {},
) {
	const storage = resolveStorage(options.storage);
	const now = (options.now || Date.now)();
	const digest = options.digest || defaultDigest;
	const [actorScopeHash, requestFingerprint] = await Promise.all([
		hashActorScope(actorScope, digest),
		fingerprintDimensionModelCommand(command, digest),
	]);
	const record: DraftRecord = {
		schemaVersion: SCHEMA_VERSION,
		operationId: command.operationId,
		actorScopeHash,
		requestFingerprint,
		createdAt: now,
		expiresAt: now + (options.ttlMs || DEFAULT_TTL_MS),
		command,
	};
	storage.setItem(storageKey(command.operationId), JSON.stringify(record));
	return { requestFingerprint, expiresAt: record.expiresAt };
}

export async function readDimensionModelDraft(
	operationId: string,
	actorScope: string,
	options: DimensionModelDraftSessionOptions = {},
): Promise<DimensionModelDraftReadResult> {
	const storage = resolveStorage(options.storage);
	const raw = storage.getItem(storageKey(operationId));
	if (!raw) return { kind: "MISSING" };
	let parsed: unknown;
	try {
		parsed = JSON.parse(raw);
	} catch {
		storage.removeItem(storageKey(operationId));
		return { kind: "INVALID" };
	}
	if (!isDraftRecord(parsed, operationId)) {
		storage.removeItem(storageKey(operationId));
		return { kind: "INVALID" };
	}
	if (parsed.expiresAt <= (options.now || Date.now)()) {
		storage.removeItem(storageKey(operationId));
		return { kind: "EXPIRED" };
	}
	const digest = options.digest || defaultDigest;
	const [actorScopeHash, requestFingerprint] = await Promise.all([
		hashActorScope(actorScope, digest),
		fingerprintDimensionModelCommand(parsed.command, digest),
	]);
	if (actorScopeHash !== parsed.actorScopeHash) {
		storage.removeItem(storageKey(operationId));
		return { kind: "ACTOR_MISMATCH" };
	}
	if (requestFingerprint !== parsed.requestFingerprint) {
		storage.removeItem(storageKey(operationId));
		return { kind: "INVALID" };
	}
	return { kind: "FOUND", command: parsed.command, requestFingerprint };
}

export const clearDimensionModelDraft = (
	operationId: string,
	storage?: DimensionModelDraftSessionOptions["storage"],
) => {
	try {
		resolveStorage(storage).removeItem(storageKey(operationId));
		return true;
	} catch {
		return false;
	}
};
