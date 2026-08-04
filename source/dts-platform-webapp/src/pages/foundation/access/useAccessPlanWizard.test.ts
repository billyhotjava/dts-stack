// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { IngestionTaskDTO } from "@/api/ingestion";
import type { AccessPlanFormValues } from "./accessPlan.types";

const mocks = vi.hoisted(() => ({
	selections: vi.fn(),
	getDefaultDestinationStatus: vi.fn(),
	getTask: vi.fn(),
	updateTask: vi.fn(),
	admitTask: vi.fn(),
	createTask: vi.fn(),
}));

vi.mock("@/api/services/dataSourcesService", () => ({
	default: { selections: mocks.selections },
}));

vi.mock("@/api/ingestion", async (importOriginal) => {
	const actual = await importOriginal<typeof import("@/api/ingestion")>();
	return {
		...actual,
		ingestionTaskAPI: {
			...actual.ingestionTaskAPI,
			getDefaultDestinationStatus: mocks.getDefaultDestinationStatus,
			getTask: mocks.getTask,
			updateTask: mocks.updateTask,
			admitTask: mocks.admitTask,
		},
	};
});

vi.mock("@/api/platformApi", async (importOriginal) => {
	const actual = await importOriginal<typeof import("@/api/platformApi")>();
	return { ...actual, createIngestionTask: mocks.createTask };
});

import {
	loadAccessPlanBootstrap,
	loadAccessPlanEdit,
	loadAccessPlanInitialization,
	resolveUserClassificationRank,
	safeAccessPlanErrorMessage,
	saveAccessPlan,
} from "./useAccessPlanWizard";

const values: AccessPlanFormValues = {
	name: "客户表入湖",
	sourceDataSourceId: "source-1",
	targetDataSourceId: "target-1",
	readerType: "mysqlreader",
	tableSelectionMode: "manual",
	selectedTables: ["crm.customer"],
	syncMode: "full_refresh",
	scheduleType: "manual",
	airflowEnabled: true,
	runNow: false,
	apiMethod: "GET",
	fileClassification: "INTERNAL",
	fileAutoId: true,
};

const destination = {
	available: true,
	writerTypeReady: true,
	writerConfigReady: true,
	writerType: "postgresqlwriter",
	destinationName: "默认湖",
};

describe("resolveUserClassificationRank", () => {
	it("accepts person_level from direct and identity-provider attribute payloads", () => {
		expect(resolveUserClassificationRank({ person_level: "GENERAL" })).toBe(2);
		expect(resolveUserClassificationRank({ person_level: "IMPORTANT" })).toBe(3);
		expect(resolveUserClassificationRank({ attributes: { person_level: "IMPORTANT" } })).toBe(3);
		expect(resolveUserClassificationRank({ attributes: { person_level: ["CORE"] } })).toBe(3);
	});

	it("uses the highest valid classification across conflicting and multi-value claims", () => {
		expect(resolveUserClassificationRank({ dataLevel: "INTERNAL", person_level: "CORE" })).toBe(3);
		expect(resolveUserClassificationRank({ attributes: { person_level: ["INTERNAL", "CORE"] } })).toBe(3);
		expect(resolveUserClassificationRank({ maxDataLevel: "SECRET" })).toBe(2);
		expect(resolveUserClassificationRank({ person_level: "INTERNAL" })).toBeUndefined();
		expect(resolveUserClassificationRank({ person_level: "UNKNOWN" })).toBeUndefined();
	});
});

describe("safeAccessPlanErrorMessage", () => {
	it("shows the safe database authentication guidance and rejects raw JDBC details", () => {
		expect(
			safeAccessPlanErrorMessage(
				new Error("数据库认证失败，请检查用户名、密码及来源 IP 授权"),
				"源表发现失败",
			),
		).toBe("数据库认证失败，请检查用户名、密码及来源 IP 授权");
		expect(
			safeAccessPlanErrorMessage(
				new Error("Access denied for user 'sensitive-user' (using password: YES)"),
				"源表发现失败",
			),
		).toBe("源表发现失败");
	});
});

beforeEach(() => {
	vi.clearAllMocks();
	mocks.selections.mockImplementation(async (params?: { capability?: string }) => {
		if (params?.capability === "DBT_TARGET") {
			return {
				capability: "DBT_TARGET",
				defaultDataSourceId: "target-1",
				items: [{ id: "target-1", name: "默认湖", type: "postgresql", recommended: true }],
			};
		}
		return {
			capability: "INGESTION_SOURCE",
			items: [
				{ id: "source-1", name: "CRM", type: "mysql" },
				{ id: "api-1", name: "CRM API", type: "api", connectorCategory: "API" },
			],
		};
	});
	mocks.getDefaultDestinationStatus.mockResolvedValue(destination);
	mocks.admitTask.mockImplementation(async (id: number) => ({ id, status: "active" }));
});

describe("loadAccessPlanBootstrap", () => {
	it("loads managed sources, target selections and the read-only default summary", async () => {
		const result = await loadAccessPlanBootstrap();

		expect(result.dataSources).toHaveLength(2);
		expect(result.targetDataSources[0].id).toBe("target-1");
		expect(result.defaultTargetDataSourceId).toBe("target-1");
		expect(result.defaultDestination?.destinationName).toBe("默认湖");
		expect(mocks.selections.mock.calls).toEqual([
			[{ capability: "INGESTION_SOURCE" }],
			[{ capability: "DBT_TARGET" }],
		]);
	});
});

describe("loadAccessPlanEdit", () => {
	it("restores an existing same-kind API task for editing", async () => {
		const oldTask: IngestionTaskDTO = {
			id: 12,
			name: "订单 API",
			sourceType: "httpreader",
			sourceDataSourceId: "api-1",
			sourceConfig: { resource: { resourceId: "orders", path: "/orders", method: "GET" } },
			destinationType: "postgresqlwriter",
			destinationConfig: { targetDataSourceId: "target-1" },
			syncMode: "full_refresh",
		};
		mocks.getTask.mockResolvedValue(oldTask);

		const result = await loadAccessPlanEdit(12, "api");

		expect(result.task).toBe(oldTask);
		expect(result.values.name).toBe("订单 API");
		expect(result.values.apiResourcePath).toBe("/orders");
	});

	it("rejects a cross-kind edit instead of silently converting the task", async () => {
		mocks.getTask.mockResolvedValue({
			id: 13,
			name: "客户库",
			sourceType: "mysqlreader",
			sourceConfig: {},
			syncMode: "full_refresh",
		});

		await expect(loadAccessPlanEdit(13, "file")).rejects.toThrow("任务类型为数据库接入");
	});
});

describe("loadAccessPlanInitialization", () => {
	it("does not finish edit initialization until both bootstrap and the existing task are loaded", async () => {
		let resolveTask!: (task: IngestionTaskDTO) => void;
		mocks.getTask.mockReturnValue(
			new Promise<IngestionTaskDTO>((resolve) => {
				resolveTask = resolve;
			}),
		);
		let settled = false;
		const initialization = loadAccessPlanInitialization(12, "api").then((result) => {
			settled = true;
			return result;
		});
		await Promise.resolve();
		await Promise.resolve();
		expect(settled).toBe(false);

		resolveTask({
			id: 12,
			name: "订单 API",
			sourceType: "httpreader",
			sourceConfig: { resource: { resourceId: "orders", path: "/orders", method: "GET" } },
			syncMode: "full_refresh",
		});
		const result = await initialization;

		expect(result.bootstrap.defaultTargetDataSourceId).toBe("target-1");
		expect(result.edit?.task.id).toBe(12);
	});
});

describe("saveAccessPlan", () => {
	it("updates a database plan and activates it within the same save flow", async () => {
		const oldTask: IngestionTaskDTO = {
			id: 14,
			name: "旧名称",
			sourceType: "mysqlreader",
			sourceDataSourceId: "source-1",
			sourceConfig: {},
			syncMode: "full_refresh",
			createdBy: "operator",
		};
		mocks.updateTask.mockImplementation(async (_id, dto) => dto);

		const result = await saveAccessPlan({
			kind: "database",
			values,
			defaultDestination: destination,
			existingTask: oldTask,
			editId: 14,
		});

		expect(result.taskId).toBe(14);
		expect(result.updated).toBe(true);
		expect(mocks.updateTask).toHaveBeenCalledWith(
			14,
			expect.objectContaining({ name: "客户表入湖", createdBy: "operator" }),
		);
		expect(mocks.admitTask).toHaveBeenCalledWith(14);
	});

	it("creates and activates a database plan within the same save flow", async () => {
		mocks.createTask.mockResolvedValue({ taskId: 21 });

		const result = await saveAccessPlan({
			kind: "database",
			values,
			defaultDestination: destination,
			existingTask: null,
		});

		expect(result).toEqual({ taskId: 21, updated: false });
		expect(mocks.createTask).toHaveBeenCalledWith(expect.objectContaining({ name: "客户表入湖" }));
		expect(mocks.admitTask).toHaveBeenCalledWith(21);
	});

	it("creates and activates a file plan without requiring an optional quality check", async () => {
		mocks.createTask.mockResolvedValue({ taskId: 22 });

		const result = await saveAccessPlan({
			kind: "file",
			values,
			defaultDestination: destination,
			existingTask: null,
			fileUploadResult: {
				fileId: "file-1",
				fileType: "csv",
				originalName: "customers.csv",
				columns: [{ name: "id", type: "string" }],
				classification: "INTERNAL",
				classificationSeal: {
					sealId: "seal-1",
					subjectType: "FILE",
					subjectKey: "ingestion-upload:file-1",
					effectiveLevel: "INTERNAL",
					snapshotVersion: 1,
					checksum: "0123456789abcdef",
					sealedAt: "2026-08-04T00:00:00Z",
				},
				fieldClassifications: { id: "INTERNAL" },
			},
		});

		expect(result).toEqual({ taskId: 22, updated: false });
		expect(mocks.admitTask).toHaveBeenCalledWith(22);
	});

	it("never falls back to create while an edit task is still unavailable", async () => {
		await expect(
			saveAccessPlan({
				kind: "database",
				values,
				defaultDestination: destination,
				existingTask: null,
				editId: 99,
			}),
		).rejects.toThrow("编辑任务尚未加载完成");

		expect(mocks.createTask).not.toHaveBeenCalled();
		expect(mocks.updateTask).not.toHaveBeenCalled();
	});

	it("never updates a different task when the edit route changes", async () => {
		const oldTask: IngestionTaskDTO = {
			id: 14,
			name: "旧名称",
			sourceType: "mysqlreader",
			sourceDataSourceId: "source-1",
			sourceConfig: {},
			syncMode: "full_refresh",
		};

		await expect(
			saveAccessPlan({
				kind: "database",
				values,
				defaultDestination: destination,
				existingTask: oldTask,
				editId: 15,
			}),
		).rejects.toThrow("编辑任务尚未加载完成");

		expect(mocks.createTask).not.toHaveBeenCalled();
		expect(mocks.updateTask).not.toHaveBeenCalled();
	});

	it("never updates a stale edit task from a new-task route", async () => {
		const staleTask: IngestionTaskDTO = {
			id: 14,
			name: "旧名称",
			sourceType: "mysqlreader",
			sourceDataSourceId: "source-1",
			sourceConfig: {},
			syncMode: "full_refresh",
		};

		await expect(
			saveAccessPlan({
				kind: "database",
				values,
				defaultDestination: destination,
				existingTask: staleTask,
			}),
		).rejects.toThrow("新建任务上下文尚未初始化");

		expect(mocks.createTask).not.toHaveBeenCalled();
		expect(mocks.updateTask).not.toHaveBeenCalled();
	});
});
