import { describe, expect, it } from "vitest";
import type { IngestionTaskDTO, ManagedFileUploadResult } from "@/api/ingestion";
import type { AccessPlanFormValues, AccessPlanPayloadContext } from "./accessPlan.types";
import {
	buildAccessPlanCreateRequest,
	buildAccessPlanUpdateDTO,
	buildManagedApiConnectionTestRequest,
	inferAccessKind,
	normalizeAccessKind,
	requireSafeApiResourcePath,
} from "./accessPlanPayload";

const baseValues = (overrides: Partial<AccessPlanFormValues> = {}): AccessPlanFormValues => ({
	name: "客户主数据入湖",
	description: "每日同步",
	sourceDataSourceId: "11111111-1111-1111-1111-111111111111",
	targetDataSourceId: "22222222-2222-2222-2222-222222222222",
	readerType: "mysqlreader",
	tableSelectionMode: "manual",
	selectedTables: ["crm.customer"],
	readerSchema: "crm",
	readerTablePattern: "customer%",
	syncMode: "full_refresh",
	scheduleType: "manual",
	syncPrefix: "ods_crm_",
	airflowEnabled: true,
	runNow: false,
	apiMethod: "GET",
	fileClassification: "INTERNAL",
	fileAutoId: true,
	...overrides,
});

const context = (
	kind: AccessPlanPayloadContext["kind"],
	values: AccessPlanFormValues,
	overrides: Partial<AccessPlanPayloadContext> = {},
): AccessPlanPayloadContext => ({
	kind,
	values,
	defaultDestination: {
		available: true,
		writerTypeReady: true,
		writerConfigReady: true,
		writerType: "postgresqlwriter",
		destinationName: "默认数据湖",
	},
	...overrides,
});

describe("normalizeAccessKind", () => {
	it("locks the wizard to one of the three supported entry kinds", () => {
		expect(normalizeAccessKind("database")).toBe("database");
		expect(normalizeAccessKind("api")).toBe("api");
		expect(normalizeAccessKind("file")).toBe("file");
		expect(normalizeAccessKind("unknown")).toBe("database");
	});
});

describe("buildAccessPlanCreateRequest", () => {
	it("always saves a database request as a non-running draft", () => {
		const request = buildAccessPlanCreateRequest(context("database", baseValues({ runNow: true })));

		expect(request).toMatchObject({
			name: "客户主数据入湖",
			source: {
				dataSourceId: "11111111-1111-1111-1111-111111111111",
				type: "mysqlreader",
				config: { readerType: "mysqlreader", table: ["crm.customer"] },
			},
			destination: {
				usePlatformDefault: true,
				type: "postgresqlwriter",
				config: { targetDataSourceId: "22222222-2222-2222-2222-222222222222" },
			},
			streams: { selection: "manual", include: ["crm.customer"] },
			runNow: false,
			draft: true,
		});
	});

	it("turns typed API paging fields into a non-running draft", () => {
		const request = buildAccessPlanCreateRequest(
			context(
				"api",
				baseValues({
					name: "CRM 订单 API",
					sourceSystem: "CRM",
					apiResourceId: "orders",
					apiResourcePath: "/v1/orders",
					apiRecordPath: "data.items",
					apiPageParam: "page",
					apiSizeParam: "pageSize",
					apiPageSize: 100,
					apiCursorField: "updatedAt",
					apiCursorParam: "updatedAfter",
					runNow: true,
				}),
			),
		);

		expect(request.source.type).toBe("httpreader");
		expect(request.source.config.resource).toMatchObject({
			resourceId: "orders",
			path: "/v1/orders",
			recordPath: "data.items",
			targetTable: "ods_api_crm_orders",
			pagination: { type: "page", pageParam: "page", sizeParam: "pageSize", pageSize: 100 },
			cursor: { type: "field", field: "updatedAt", injectInto: "query", parameterName: "updatedAfter" },
		});
		expect(request.streams).toEqual({ selection: "manual", include: ["orders"] });
		expect(request).toMatchObject({ draft: true, runNow: false });
	});

	it("always saves a classified file as a non-running draft", () => {
		const file: ManagedFileUploadResult = {
			fileType: "excel",
			originalName: "source.xlsx",
			fileId: "file-1",
			fileHash: "file-checksum",
			keyVersion: "v1",
			encrypted: true,
			columns: [{ name: "customer_id", type: "string" }],
			classification: "CONFIDENTIAL",
			classificationSeal: {
				sealId: "seal-1",
				subjectType: "FILE",
				subjectKey: "ingestion-upload:file-1",
				effectiveLevel: "CONFIDENTIAL",
				snapshotVersion: 1,
				checksum: "checksum",
				sealedAt: "2026-07-31T00:00:00Z",
			},
			fieldClassifications: { customer_id: "CONFIDENTIAL" },
		};
		const request = buildAccessPlanCreateRequest(
			context("file", baseValues({ fileTargetTable: "ods_customer" }), { fileUploadResult: file }),
		);

		expect(request).toMatchObject({
			draft: true,
			runNow: false,
			source: {
				type: "txtfilereader",
				config: {
					_fileId: "file-1",
					_fileHash: "file-checksum",
					_keyVersion: "v1",
					_encrypted: true,
				},
			},
			streams: { selection: "manual", include: ["ods_customer"] },
			classificationSeal: { sealId: "seal-1", fileFloor: "CONFIDENTIAL" },
			fieldClassifications: { customer_id: "CONFIDENTIAL" },
		});
		expect(request.source.config).not.toHaveProperty("_filePath");
		expect(request.source.config).not.toHaveProperty("_containerPath");
	});
});

describe("requireSafeApiResourcePath", () => {
	it("keeps a relative resource path and non-sensitive query parameters", () => {
		expect(requireSafeApiResourcePath("/v1/orders?status=active&page=1")).toBe("/v1/orders?status=active&page=1");
	});

	it.each(["https://evil.example/orders", "//evil.example/orders", "/v1/orders#token"])(
		"rejects non-relative or fragment-bearing paths: %s",
		(path) => {
			expect(() => requireSafeApiResourcePath(path)).toThrow("API 资源路径必须是站内相对路径");
		},
	);

	it.each(["/v1/orders?api_key=value", "/v1/orders?access_token=value", "/v1/orders?password=value"])(
		"rejects credentials embedded in query parameters: %s",
		(path) => {
			expect(() => requireSafeApiResourcePath(path)).toThrow("API 资源路径不能包含凭据参数");
		},
	);
});

describe("buildManagedApiConnectionTestRequest", () => {
	it("sends only a managed data source and a strict relative GET resource", () => {
		const request = buildManagedApiConnectionTestRequest(
			baseValues({
				sourceDataSourceId: "11111111-1111-1111-1111-111111111111",
				apiMethod: "POST",
				apiResourceId: "orders",
				apiResourcePath: "/v1/orders?status=active",
				apiRecordPath: "data.items",
			}),
		);

		expect(request).toEqual({
			dataSourceId: "11111111-1111-1111-1111-111111111111",
			resource: {
				path: "/v1/orders?status=active",
				method: "GET",
				resourceId: "orders",
				displayName: undefined,
				recordPath: "data.items",
			},
		});
		expect(request).not.toHaveProperty("sourceConfig");
		expect(request).not.toHaveProperty("secrets");
		expect(request).not.toHaveProperty("requestPolicy");
	});
});

describe("buildAccessPlanUpdateDTO", () => {
	it("preserves the old DTO while replacing editable API fields", () => {
		const oldTask: IngestionTaskDTO = {
			id: 42,
			name: "旧任务",
			sourceType: "httpreader",
			sourceDataSourceId: "11111111-1111-1111-1111-111111111111",
			sourceConfig: { resource: { resourceId: "old" } },
			destinationType: "postgresqlwriter",
			destinationConfig: { targetDataSourceId: "22222222-2222-2222-2222-222222222222" },
			syncMode: "full_refresh",
			status: "active",
			createdBy: "operator",
			classificationSeal: {
				sealId: "seal-old",
				subjectType: "ASSET",
				subjectKey: "data-source:11111111-1111-1111-1111-111111111111",
				effectiveLevel: "INTERNAL",
				snapshotVersion: 1,
				checksum: "checksum-old",
				sealedAt: "2026-07-31T00:00:00Z",
			},
		};
		const updated = buildAccessPlanUpdateDTO(
			oldTask,
			context("api", baseValues({ name: "新任务", apiResourceId: "orders", apiResourcePath: "/orders" })),
		);

		expect(updated.id).toBe(42);
		expect(updated.name).toBe("新任务");
		expect(updated.createdBy).toBe("operator");
		expect(updated.status).toBe("draft");
		expect(updated.classificationSeal?.sealId).toBe("seal-old");
		expect(updated.sourceConfig.resource.resourceId).toBe("orders");
	});

	it("drops source-bound classification evidence when the managed source changes", () => {
		const oldTask: IngestionTaskDTO = {
			id: 43,
			name: "旧任务",
			sourceType: "mysqlreader",
			sourceDataSourceId: "11111111-1111-1111-1111-111111111111",
			sourceConfig: {},
			syncMode: "full_refresh",
			status: "active",
			classificationSeal: {
				sealId: "seal-old",
				subjectType: "ASSET",
				subjectKey: "data-source:11111111-1111-1111-1111-111111111111",
				effectiveLevel: "INTERNAL",
				snapshotVersion: 1,
				checksum: "checksum-old",
				sealedAt: "2026-07-31T00:00:00Z",
			},
			fieldClassifications: { customer_id: "INTERNAL" },
		};
		const updated = buildAccessPlanUpdateDTO(
			oldTask,
			context("database", baseValues({ sourceDataSourceId: "33333333-3333-3333-3333-333333333333" })),
		);

		expect(updated.status).toBe("draft");
		expect(updated.classificationSeal).toBeUndefined();
		expect(updated.fieldClassifications).toBeUndefined();
	});

	it("identifies an existing task kind before allowing same-kind editing", () => {
		expect(inferAccessKind({ sourceType: "httpreader", sourceConfig: {}, name: "a", syncMode: "full_refresh" })).toBe(
			"api",
		);
		expect(
			inferAccessKind({ sourceType: "txtfilereader", sourceConfig: {}, name: "b", syncMode: "full_refresh" }),
		).toBe("file");
		expect(inferAccessKind({ sourceType: "mysqlreader", sourceConfig: {}, name: "c", syncMode: "full_refresh" })).toBe(
			"database",
		);
	});
});
