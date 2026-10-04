import { expect, test } from "vitest";
import type { IngestionTaskDTO } from "@/api/ingestion";
import { accessTargetMappings, isModelBoundAccess } from "./accessTargetSummary";

test("bound file execution shows the exact model target without exposing connection fields", () => {
	const task = {
		sourceConfig: { _originalName: "input.csv" },
		destinationConfig: { modelTarget: { schemaName: "public", tableName: "ods_existing" }, password: "fixture-private" },
		tableMapping: [{ source: "old", target: "wrong" }],
	} as unknown as IngestionTaskDTO;
	expect(isModelBoundAccess(task)).toBe(true);
	expect(accessTargetMappings(task)).toEqual([{ key: "model", source: "input.csv", target: "public.ods_existing" }]);
});

test("ordinary database mappings and managed file landing targets remain visible", () => {
	expect(accessTargetMappings({ tableMapping: [{ source: "orders", target: "ods_orders" }] } as IngestionTaskDTO)[0].target).toBe("ods_orders");
	expect(accessTargetMappings({ sourceConfig: { _fileLanding: { targetTable: "ods_file" } } } as IngestionTaskDTO)[0].target).toBe("ods_file");
	expect(accessTargetMappings(null)).toEqual([]);
});
