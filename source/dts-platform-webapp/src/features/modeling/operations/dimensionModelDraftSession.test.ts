import { describe, expect, it } from "vitest";
import type { CreateDimensionModelCommand } from "@/api/modelSpecApi";
import {
	clearDimensionModelDraft,
	persistDimensionModelDraft,
	readDimensionModelDraft,
} from "./dimensionModelDraftSession";

const OPERATION_ID = "50000000-0000-4000-8000-000000000001";
const digest = async (value: string) => {
	let hash = 2_166_136_261;
	for (const byte of new TextEncoder().encode(value)) hash = Math.imul(hash ^ byte, 16_777_619);
	return `sha256:${(hash >>> 0).toString(16).padStart(8, "0")}`;
};

const command: CreateDimensionModelCommand = {
	operationId: OPERATION_ID,
	definitionBinding: {
		mode: "EXISTING",
		dimensionDefinitionRef: {
			dimensionDefinitionId: "60000000-0000-4000-8000-000000000001",
			revision: 7,
		},
	},
	modelSpec: {
		planId: "70000000-0000-4000-8000-000000000001",
		domainId: "80000000-0000-4000-8000-000000000001",
		modelType: "DIMENSION",
		layer: "DWD",
		name: "客户维度",
		description: null,
		implementationMode: "DESIGNER_GENERATED",
		materialization: "table",
		businessActivityRef: null,
		consumptionScenario: null,
		grain: { statement: "一个客户一行", keys: ["customer_code"] },
		factShape: null,
		timeSemantics: null,
		generationStrategy: null,
		dimensionProfile: {
			hierarchies: [],
			scdPolicy: { type: "NONE" },
		},
		dataMartId: null,
		variantCode: "DIM_CUSTOMER",
		fields: [
			{
				name: "customer_code",
				displayName: "客户编码",
				dataType: "STRING",
				nullable: false,
				role: "KEY",
				dimensionAttributeCode: "CUSTOMER_CODE",
			},
		],
		sourceRefs: [],
		dependsOn: [],
		dimensionRefs: [],
		metricRefs: [],
		standardBindings: [],
	},
};

const memoryStorage = () => {
	const values = new Map<string, string>();
	return {
		getItem: (key: string) => values.get(key) ?? null,
		setItem: (key: string, value: string) => values.set(key, value),
		removeItem: (key: string) => values.delete(key),
		dump: () => Array.from(values.values()).join("\n"),
	};
};

describe("dimension model draft session", () => {
	it("round-trips an exact command only for the same actor and before the short TTL", async () => {
		const storage = memoryStorage();
		await persistDimensionModelDraft(command, "tenant-a|actor-a", {
			storage,
			now: () => 1_000,
			ttlMs: 10_000,
			digest,
		});

		const restored = await readDimensionModelDraft(OPERATION_ID, "tenant-a|actor-a", {
			storage,
			now: () => 2_000,
			digest,
		});
		expect(restored).toMatchObject({ kind: "FOUND", command });
		expect(storage.dump()).not.toContain("tenant-a|actor-a");
		expect(JSON.stringify(restored)).not.toContain("token");
	});

	it("fails closed across actors and expires stale commands", async () => {
		const storage = memoryStorage();
		await persistDimensionModelDraft(command, "actor-a", {
			storage,
			now: () => 1_000,
			ttlMs: 100,
			digest,
		});
		expect(await readDimensionModelDraft(OPERATION_ID, "actor-b", { storage, now: () => 1_050, digest })).toEqual({
			kind: "ACTOR_MISMATCH",
		});
		await persistDimensionModelDraft(command, "actor-a", {
			storage,
			now: () => 1_000,
			ttlMs: 100,
			digest,
		});
		expect(await readDimensionModelDraft(OPERATION_ID, "actor-a", { storage, now: () => 1_101, digest })).toEqual({
			kind: "EXPIRED",
		});
	});

	it("removes a command only after explicit workspace acknowledgement", async () => {
		const storage = memoryStorage();
		await persistDimensionModelDraft(command, "actor-a", { storage, digest });
		clearDimensionModelDraft(OPERATION_ID, storage);
		expect(await readDimensionModelDraft(OPERATION_ID, "actor-a", { storage, digest })).toEqual({
			kind: "MISSING",
		});
	});
});
