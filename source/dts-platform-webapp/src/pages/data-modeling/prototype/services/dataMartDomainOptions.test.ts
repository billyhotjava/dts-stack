import { beforeEach, describe, expect, it, vi } from "vitest";
import { loadDataMartDomainOptions } from "./dataMartDomainOptions";

const apiMocks = vi.hoisted(() => ({ listDomains: vi.fn() }));

vi.mock("@/api/platformApi", () => ({ listDomains: apiMocks.listDomains }));

beforeEach(() => apiMocks.listDomains.mockReset());

describe("loadDataMartDomainOptions", () => {
	it("preserves the catalog UUID separately from the displayed business code", async () => {
		apiMocks.listDomains.mockResolvedValue({
			data: {
				content: [
					{
						id: "9bb4e351-41f7-4e2f-b27e-adb55ca6cb11",
						code: "FIN",
						name: "财务域",
					},
				],
			},
		});

		await expect(loadDataMartDomainOptions()).resolves.toEqual([
			{
				id: "9bb4e351-41f7-4e2f-b27e-adb55ca6cb11",
				code: "FIN",
				name: "财务域",
			},
		]);
		expect(apiMocks.listDomains).toHaveBeenCalledWith(0, 500, "");
	});
});
