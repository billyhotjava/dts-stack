import { beforeEach, describe, expect, it, vi } from "vitest";
import {
	type CreateWarehouseLayerCommand,
	createWarehouseLayer,
	deleteWarehouseLayer,
	listWarehouseLayers,
	normalizeWarehouseLayerView,
} from "./warehouseLayerApi";

vi.mock("@/api/apiClient", () => ({ default: { get: vi.fn(), post: vi.fn(), delete: vi.fn() } }));

import api from "@/api/apiClient";

const mockedApi = vi.mocked(api);

const command: CreateWarehouseLayerCommand = {
	code: "FIN_DETAIL",
	name: "财务明细层",
	systemLayerCode: "DWD",
	description: "财务域明细",
	namingPrefix: "fin_dwd_",
};

const view = {
	code: "FIN_DETAIL",
	name: "财务明细层",
	systemLayerCode: "DWD",
	layerGroup: "COMMON",
	modelTypes: ["DIMENSION", "FACT"],
	kind: "DETAIL",
	responsibility: "财务域明细",
	namingPrefixes: ["fin_dwd_", "dwd_"],
	optional: false,
	builtin: false,
	deletable: true,
	disabledReason: null,
};

describe("warehouseLayerApi", () => {
	beforeEach(() => {
		vi.clearAllMocks();
	});

	it("uses the canonical global warehouse-layer resource", async () => {
		mockedApi.get.mockResolvedValue({ data: [view] });
		mockedApi.post.mockResolvedValue({ data: view });
		mockedApi.delete.mockResolvedValue({ data: {} });

		await listWarehouseLayers();
		expect(api.get).toHaveBeenCalledWith(expect.objectContaining({ url: "/modeling/warehouse-layers" }));

		await createWarehouseLayer(command);
		expect(api.post).toHaveBeenCalledWith(
			expect.objectContaining({ url: "/modeling/warehouse-layers", data: command }),
		);

		await deleteWarehouseLayer("FIN_DETAIL");
		expect(api.delete).toHaveBeenCalledWith(expect.objectContaining({ url: "/modeling/warehouse-layers/FIN_DETAIL" }));
	});

	it("normalizes only complete warehouse layer views", () => {
		expect(normalizeWarehouseLayerView(view)).toEqual(view);
		expect(normalizeWarehouseLayerView({ ...view, code: undefined })).toBeUndefined();
		expect(normalizeWarehouseLayerView({ ...view, name: 42 })).toBeUndefined();
		expect(normalizeWarehouseLayerView({ ...view, disabledReason: "平台内置分层不可删除" })).toMatchObject({
			disabledReason: "平台内置分层不可删除",
		});
	});
});
