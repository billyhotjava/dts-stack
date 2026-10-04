import { expect, it, vi } from "vitest";
import { getDatasetFields } from "./platformApi";

const get = vi.hoisted(() => vi.fn());
vi.mock("./apiClient", () => ({ default: { get } }));
it("keeps dataset fields already unwrapped by the shared client", async () => {
	const fields = [{ name: "phone", dataType: "STRING" }];
	get.mockResolvedValue(fields);
	await expect(getDatasetFields("d1")).resolves.toEqual(fields);
});
