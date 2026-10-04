import { describe, expect, it } from "vitest";
import {
	buildSnapshotResumeUrl,
	clearJourneySnapshot,
	createJourneySnapshot,
	describeJourneySnapshot,
	formatSnapshotSavedAgo,
	JOURNEY_SNAPSHOT_STORAGE_KEY,
	JOURNEY_SNAPSHOT_VERSION,
	type JourneySnapshotStorage,
	loadJourneySnapshot,
	persistJourneyContextSnapshot,
	saveJourneySnapshot,
	shouldOfferSnapshotResume,
	shouldPersistSnapshot,
} from "./journeySnapshot";

const createMemoryStorage = (): JourneySnapshotStorage & { data: Map<string, string> } => {
	const data = new Map<string, string>();
	return {
		data,
		getItem: (key) => data.get(key) ?? null,
		setItem: (key, value) => {
			data.set(key, value);
		},
		removeItem: (key) => {
			data.delete(key);
		},
	};
};

const createThrowingStorage = (): JourneySnapshotStorage => ({
	getItem: () => {
		throw new Error("storage unavailable");
	},
	setItem: () => {
		throw new Error("quota exceeded");
	},
	removeItem: () => {
		throw new Error("storage unavailable");
	},
});

describe("journey snapshot persistence", () => {
	it("round-trips a snapshot through storage", () => {
		const storage = createMemoryStorage();
		const snapshot = createJourneySnapshot("modeling", { sourceId: "ds-1", standardDraftId: "std-9" });

		expect(saveJourneySnapshot(snapshot, storage)).toBe(true);
		const loaded = loadJourneySnapshot(storage);

		expect(loaded).not.toBeNull();
		expect(loaded?.stage).toBe("modeling");
		expect(loaded?.params).toEqual({ sourceId: "ds-1", standardDraftId: "std-9" });
		expect(loaded?.version).toBe(JOURNEY_SNAPSHOT_VERSION);
		expect(typeof loaded?.savedAt).toBe("string");
	});

	it("drops and clears snapshots with a mismatched version", () => {
		const storage = createMemoryStorage();
		storage.setItem(
			JOURNEY_SNAPSHOT_STORAGE_KEY,
			JSON.stringify({ version: 999, journey: "e2e-data-product", stage: "modeling", params: {}, savedAt: "t" }),
		);

		expect(loadJourneySnapshot(storage)).toBeNull();
		expect(storage.data.has(JOURNEY_SNAPSHOT_STORAGE_KEY)).toBe(false);
	});

	it("drops snapshots for an unknown journey or stage", () => {
		const storage = createMemoryStorage();
		storage.setItem(
			JOURNEY_SNAPSHOT_STORAGE_KEY,
			JSON.stringify({
				version: JOURNEY_SNAPSHOT_VERSION,
				journey: "other-journey",
				stage: "modeling",
				params: {},
				savedAt: "t",
			}),
		);
		expect(loadJourneySnapshot(storage)).toBeNull();

		storage.setItem(
			JOURNEY_SNAPSHOT_STORAGE_KEY,
			JSON.stringify({
				version: JOURNEY_SNAPSHOT_VERSION,
				journey: "e2e-data-product",
				stage: "not-a-stage",
				params: {},
				savedAt: "t",
			}),
		);
		expect(loadJourneySnapshot(storage)).toBeNull();
	});

	it("drops corrupted json and clears the dirty entry", () => {
		const storage = createMemoryStorage();
		storage.setItem(JOURNEY_SNAPSHOT_STORAGE_KEY, "{not-json");

		expect(loadJourneySnapshot(storage)).toBeNull();
		expect(storage.data.has(JOURNEY_SNAPSHOT_STORAGE_KEY)).toBe(false);
	});

	it("degrades silently when storage throws", () => {
		const storage = createThrowingStorage();
		const snapshot = createJourneySnapshot("integration", {});

		expect(saveJourneySnapshot(snapshot, storage)).toBe(false);
		expect(loadJourneySnapshot(storage)).toBeNull();
		expect(() => clearJourneySnapshot(storage)).not.toThrow();
	});

	it("returns null and false when no storage is available", () => {
		expect(saveJourneySnapshot(createJourneySnapshot("integration", {}), undefined)).toBe(false);
		expect(loadJourneySnapshot(undefined)).toBeNull();
		expect(() => clearJourneySnapshot(undefined)).not.toThrow();
	});

	it("only persists enabled journey contexts", () => {
		const storage = createMemoryStorage();

		expect(
			persistJourneyContextSnapshot({ enabled: false, stage: "modeling", params: { modelSpecId: "m-1" } }, storage),
		).toBe(false);
		expect(storage.data.size).toBe(0);

		expect(
			persistJourneyContextSnapshot({ enabled: true, stage: "modeling", params: { modelSpecId: "m-1" } }, storage),
		).toBe(true);
		expect(loadJourneySnapshot(storage)?.params).toEqual({ modelSpecId: "m-1" });
	});

	it("dedupes persistence when stage and params are unchanged", () => {
		const storage = createMemoryStorage();
		const context = { enabled: true, stage: "metrics", params: { modelSpecId: "m-1", metricId: "k-2" } } as const;

		expect(persistJourneyContextSnapshot(context, storage)).toBe(true);
		expect(persistJourneyContextSnapshot(context, storage)).toBe(false);

		expect(persistJourneyContextSnapshot({ ...context, params: { ...context.params, metricId: "k-3" } }, storage)).toBe(
			true,
		);
		expect(loadJourneySnapshot(storage)?.params.metricId).toBe("k-3");
	});

	it("decides persistence from stage or param drift against the stored snapshot", () => {
		const existing = createJourneySnapshot("modeling", { modelSpecId: "m-1" });

		expect(shouldPersistSnapshot({ enabled: true, stage: "modeling", params: { modelSpecId: "m-1" } }, existing)).toBe(
			false,
		);
		expect(shouldPersistSnapshot({ enabled: true, stage: "metrics", params: { modelSpecId: "m-1" } }, existing)).toBe(
			true,
		);
		expect(shouldPersistSnapshot({ enabled: true, stage: "modeling", params: { modelSpecId: "m-2" } }, existing)).toBe(
			true,
		);
		expect(shouldPersistSnapshot({ enabled: true, stage: "modeling", params: {} }, existing)).toBe(true);
		expect(shouldPersistSnapshot({ enabled: false, stage: "modeling", params: { modelSpecId: "m-1" } }, existing)).toBe(
			false,
		);
		expect(shouldPersistSnapshot({ enabled: true, stage: "modeling", params: { modelSpecId: "m-1" } }, null)).toBe(
			true,
		);
	});

	it("offers resume only when a snapshot exists and the journey is not already active", () => {
		const snapshot = createJourneySnapshot("modeling", { modelSpecId: "m-1" });

		expect(shouldOfferSnapshotResume(snapshot, null)).toBe(true);
		expect(shouldOfferSnapshotResume(snapshot, "other")).toBe(true);
		expect(shouldOfferSnapshotResume(snapshot, "e2e-data-product")).toBe(false);
		expect(shouldOfferSnapshotResume(null, null)).toBe(false);
	});

	it("formats the saved-ago label across time buckets", () => {
		const now = Date.parse("2026-07-10T12:00:00.000Z");

		expect(formatSnapshotSavedAgo("2026-07-10T11:59:40.000Z", now)).toBe("刚刚");
		expect(formatSnapshotSavedAgo("2026-07-10T11:45:00.000Z", now)).toBe("15 分钟前");
		expect(formatSnapshotSavedAgo("2026-07-10T09:00:00.000Z", now)).toBe("3 小时前");
		expect(formatSnapshotSavedAgo("2026-07-07T12:00:00.000Z", now)).toBe("3 天前");
		expect(formatSnapshotSavedAgo("not-a-date", now)).toBe("");
	});

	it("describes a snapshot with stage title and labelled context summary", () => {
		const now = Date.parse("2026-07-10T12:00:00.000Z");
		const snapshot = {
			...createJourneySnapshot("metrics", { modelSpecId: "m-7", sourceId: "ds-1" }),
			savedAt: "2026-07-10T11:30:00.000Z",
		};

		const description = describeJourneySnapshot(snapshot, now);

		expect(description.stageTitle).toBe("数据指标");
		expect(description.contextSummary).toContain("模型 m-7");
		expect(description.contextSummary).toContain("数据源 ds-1");
		expect(description.savedAgo).toBe("30 分钟前");
	});

	it("builds a resume url with the journey flag and all saved params", () => {
		const snapshot = createJourneySnapshot("metrics", {
			modelSpecId: "m-7",
			sourceId: "ds-1",
			planId: "p-1",
			domainId: "d-1",
			modelType: "DIMENSION",
		});
		const url = buildSnapshotResumeUrl(snapshot);

		expect(url.startsWith("/data-modeling/metrics/atomic?")).toBe(true);
		expect(url).toContain("journey=e2e-data-product");
		expect(url).toContain("modelSpecId=m-7");
		expect(url).toContain("sourceId=ds-1");
		expect(url).toContain("planId=p-1");
		expect(url).toContain("domainId=d-1");
		expect(url).toContain("modelType=DIMENSION");
	});
});
