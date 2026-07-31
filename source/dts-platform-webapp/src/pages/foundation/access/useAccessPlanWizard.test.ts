// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { IngestionTaskDTO } from "@/api/ingestion";
import type { AccessPlanFormValues } from "./accessPlan.types";

const mocks = vi.hoisted(() => ({
	selections: vi.fn(),
	getDefaultDestinationStatus: vi.fn(),
	getTask: vi.fn(),
	updateTask: vi.fn(),
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
	it("updates by spreading the old DTO and applying changed fields", async () => {
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
	});

	it("creates through the existing IngestionTaskRequest endpoint when no edit task exists", async () => {
		mocks.createTask.mockResolvedValue({ taskId: 21 });

		const result = await saveAccessPlan({
			kind: "database",
			values,
			defaultDestination: destination,
			existingTask: null,
		});

		expect(result).toEqual({ taskId: 21, updated: false });
		expect(mocks.createTask).toHaveBeenCalledWith(expect.objectContaining({ name: "客户表入湖" }));
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
