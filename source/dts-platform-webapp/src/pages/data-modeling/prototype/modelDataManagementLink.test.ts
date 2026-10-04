import { describe, expect, it } from "vitest";
import {
	buildModelDataManagementUrl,
	buildModelWorkbenchReturnUrl,
	resolveModelDataManagementFocus,
	resolveModelingReturnTo,
} from "./modelDataManagementLink";

describe("model data-management link", () => {
	it("keeps the existing parameter order for the plain data-management entry", () => {
		expect(buildModelDataManagementUrl({ modelSpecId: "m-1", environment: "dev", candidateId: "c-1" })).toBe(
			"/catalog/search?view=table&modelSpecId=m-1&environment=dev&candidateId=c-1",
		);
	});

	it("opens the quality section and carries one way back to the model", () => {
		const back = buildModelWorkbenchReturnUrl("m-1", "dev");
		const url = new URL(
			buildModelDataManagementUrl({ modelSpecId: "m-1", environment: "dev", focus: "quality", returnTo: back }),
			"http://dts.local",
		);

		expect(url.pathname).toBe("/catalog/search");
		expect(url.searchParams.get("focus")).toBe("quality");
		expect(url.searchParams.get("returnTo")).toBe(
			"/data-modeling/dimensions/workbench?modelSpecId=m-1&step=verification&environment=dev",
		);
	});

	it("drops return targets outside the modeling workbench", () => {
		for (const unsafe of [
			"https://evil.example/x",
			"//evil.example",
			"/catalog/search",
			"/governance/rules",
			"javascript:alert(1)",
		]) {
			expect(resolveModelingReturnTo(unsafe)).toBeUndefined();
			expect(
				new URL(
					buildModelDataManagementUrl({ modelSpecId: "m-1", environment: "dev", returnTo: unsafe }),
					"http://dts.local",
				).searchParams.has("returnTo"),
			).toBe(false);
		}
		expect(resolveModelingReturnTo("/data-modeling/dimensions/workbench?modelSpecId=m-1")).toBe(
			"/data-modeling/dimensions/workbench?modelSpecId=m-1",
		);
	});

	it("accepts only the known focus section", () => {
		expect(resolveModelDataManagementFocus("quality")).toBe("quality");
		expect(resolveModelDataManagementFocus("catalog")).toBeUndefined();
		expect(resolveModelDataManagementFocus(null)).toBeUndefined();
	});
});
