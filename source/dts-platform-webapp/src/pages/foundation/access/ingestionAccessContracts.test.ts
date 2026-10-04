// @vitest-environment jsdom
import { expect, test } from "vitest";
import {
	normalizeIngestionRevisionState,
	normalizeIngestionTaskDTO,
	normalizeIngestionTaskRevisionDTO,
	normalizeManagedFileUploadResult,
} from "@/api/ingestion";

test("revision responses normalize to the explicit lifecycle union and reject unknown values", () => {
	expect(normalizeIngestionRevisionState(" draft ")).toBe("DRAFT");
	expect(normalizeIngestionRevisionState("future_state")).toBeUndefined();
	expect(
		normalizeIngestionTaskDTO({
			name: "task",
			sourceType: "mysqlreader",
			sourceConfig: {},
			syncMode: "full",
			revisionState: "future_state",
		}).revisionState,
	).toBeUndefined();
	expect(normalizeIngestionTaskRevisionDTO({ revisionNumber: 3, revisionState: "future_state" })).toBeUndefined();
});

test("an ACTIVE task may serialize a DRAFT current projection while revisions remain the lifecycle authority", () => {
	const task = normalizeIngestionTaskDTO(
		JSON.parse(
			'{"id":19,"name":"orders","status":"active","sourceType":"mysqlreader","sourceConfig":{},"syncMode":"full_refresh","revisionNumber":3,"revisionState":"DRAFT"}',
		),
	);
	const revisions = JSON.parse(
		'[{"revisionNumber":3,"revisionState":"DRAFT"},{"revisionNumber":2,"revisionState":"ACTIVE"}]',
	).map(normalizeIngestionTaskRevisionDTO);
	expect(task.status).toBe("active");
	expect(task.revisionState).toBe("DRAFT");
	expect(
		revisions.map((revision: { revisionNumber: number; revisionState: string }) => [
			revision.revisionNumber,
			revision.revisionState,
		]),
	).toEqual([
		[3, "DRAFT"],
		[2, "ACTIVE"],
	]);
});

test("managed file responses retain only opaque identity, seal and parsed metadata", () => {
	const result = normalizeManagedFileUploadResult({
		fileId: "file-7",
		fileType: "excel",
		originalName: "orders.xlsx",
		columns: [{ name: "order_id", dataType: "string" }],
		hostPath: "/srv/private/orders.xlsx",
		containerPath: "/decrypted/orders.xlsx",
		csvPath: "/tmp/orders.csv",
		errorPath: "/tmp/errors.csv",
	});
	expect(result.fileId).toBe("file-7");
	expect(result.columns).toEqual([
		{
			name: "order_id",
			type: "string",
			label: undefined,
			description: undefined,
			length: undefined,
			precision: undefined,
			scale: undefined,
			_odsMatched: undefined,
		},
	]);
	for (const forbidden of ["hostPath", "containerPath", "csvPath", "errorPath"]) {
		expect(Object.hasOwn(result, forbidden)).toBe(false);
	}
});
