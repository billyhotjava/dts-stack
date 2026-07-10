// 旅程快照持久化：旅程中断（刷新、关标签、重开浏览器）后可从工作台恢复。
// 后端旅程实例 API（GET/PUT /api/journey/instances/{journey}）尚未提供，
// 当前仅落地 localStorage；接口就绪后由同一契约切换存储实现。
import {
	E2E_DATA_PRODUCT_JOURNEY,
	JOURNEY_CONTEXT_PARAM_KEYS,
	buildJourneyUrl,
	type DataProductJourneyContext,
	type DataProductJourneyContextParams,
	type DataProductJourneyStageKey,
} from "./journeyContext";
import { DATA_PRODUCT_JOURNEY_STAGE_DEFINITIONS } from "./journeyStageState";

export const JOURNEY_SNAPSHOT_VERSION = 1;
export const JOURNEY_SNAPSHOT_STORAGE_KEY = "dts.journey.e2e-data-product.v1";

export type JourneySnapshotStorage = Pick<Storage, "getItem" | "setItem" | "removeItem">;

export type JourneySnapshot = {
	version: typeof JOURNEY_SNAPSHOT_VERSION;
	journey: string;
	stage: DataProductJourneyStageKey;
	params: DataProductJourneyContextParams;
	savedAt: string;
};

const resolveDefaultStorage = (): JourneySnapshotStorage | undefined => {
	try {
		return typeof window === "undefined" ? undefined : window.localStorage;
	} catch {
		return undefined;
	}
};

const isKnownStage = (stage: unknown): stage is DataProductJourneyStageKey =>
	DATA_PRODUCT_JOURNEY_STAGE_DEFINITIONS.some((definition) => definition.stageKey === stage);

export const createJourneySnapshot = (
	stage: DataProductJourneyStageKey,
	params: DataProductJourneyContextParams,
	savedAt = new Date().toISOString(),
): JourneySnapshot => ({
	version: JOURNEY_SNAPSHOT_VERSION,
	journey: E2E_DATA_PRODUCT_JOURNEY,
	stage,
	params,
	savedAt,
});

export const saveJourneySnapshot = (
	snapshot: JourneySnapshot,
	storage: JourneySnapshotStorage | undefined = resolveDefaultStorage(),
): boolean => {
	if (!storage) return false;
	try {
		storage.setItem(JOURNEY_SNAPSHOT_STORAGE_KEY, JSON.stringify(snapshot));
		return true;
	} catch {
		return false;
	}
};

export const clearJourneySnapshot = (
	storage: JourneySnapshotStorage | undefined = resolveDefaultStorage(),
): void => {
	if (!storage) return;
	try {
		storage.removeItem(JOURNEY_SNAPSHOT_STORAGE_KEY);
	} catch {
		// 隐私模式/配额异常时静默降级，不阻塞页面。
	}
};

export const loadJourneySnapshot = (
	storage: JourneySnapshotStorage | undefined = resolveDefaultStorage(),
): JourneySnapshot | null => {
	if (!storage) return null;
	let raw: string | null = null;
	try {
		raw = storage.getItem(JOURNEY_SNAPSHOT_STORAGE_KEY);
	} catch {
		return null;
	}
	if (!raw) return null;
	try {
		const parsed = JSON.parse(raw) as Partial<JourneySnapshot> | null;
		const valid =
			parsed != null &&
			parsed.version === JOURNEY_SNAPSHOT_VERSION &&
			parsed.journey === E2E_DATA_PRODUCT_JOURNEY &&
			isKnownStage(parsed.stage) &&
			typeof parsed.savedAt === "string";
		if (!valid) {
			clearJourneySnapshot(storage);
			return null;
		}
		return {
			version: JOURNEY_SNAPSHOT_VERSION,
			journey: E2E_DATA_PRODUCT_JOURNEY,
			stage: parsed.stage as DataProductJourneyStageKey,
			params: parsed.params ?? {},
			savedAt: parsed.savedAt as string,
		};
	} catch {
		clearJourneySnapshot(storage);
		return null;
	}
};

export type JourneySnapshotContextInput = Pick<DataProductJourneyContext, "enabled" | "stage" | "params">;

export const shouldPersistSnapshot = (
	context: JourneySnapshotContextInput,
	existing: JourneySnapshot | null,
): boolean => {
	if (!context.enabled) return false;
	if (!existing) return true;
	if (existing.stage !== context.stage) return true;
	return JOURNEY_CONTEXT_PARAM_KEYS.some((key) => existing.params[key] !== context.params[key]);
};

export const persistJourneyContextSnapshot = (
	context: JourneySnapshotContextInput,
	storage: JourneySnapshotStorage | undefined = resolveDefaultStorage(),
): boolean => {
	if (!storage) return false;
	if (!shouldPersistSnapshot(context, loadJourneySnapshot(storage))) return false;
	return saveJourneySnapshot(createJourneySnapshot(context.stage, context.params), storage);
};

export const buildSnapshotResumeUrl = (snapshot: JourneySnapshot): string => {
	const route =
		DATA_PRODUCT_JOURNEY_STAGE_DEFINITIONS.find((definition) => definition.stageKey === snapshot.stage)?.route ??
		"/workbench";
	return buildJourneyUrl(route, snapshot.params as Record<string, string | null | undefined>);
};
