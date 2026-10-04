// @vitest-environment jsdom
import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, describe, expect, it } from "vitest";
import type { DrillState } from "./useDrillDown";
import { useDrillDown } from "./useDrillDown";

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

describe("useDrillDown interaction guards", () => {
	let container: HTMLDivElement | null = null;

	afterEach(() => {
		container?.remove();
		container = null;
	});

	it("UI-10/UI-11 rejects missing mappings and duplicate clicks without advancing twice", async () => {
		let state: DrillState | null = null;
		function Harness() {
			state = useDrillDown(
				{ type: "sql", sqlConfig: { databaseId: 1, query: "select 1" } },
				{
					enabled: true,
					levels: [
						{
							label: "明细",
							dataSource: { type: "api", apiConfig: { url: "/example", method: "GET" } },
							mappings: [{ sourcePath: "data.key", variableKey: "selectedKey", transform: "string" }],
						},
					],
				},
			);
			return null;
		}

		container = document.createElement("div");
		document.body.appendChild(container);
		const root = createRoot(container);
		await act(async () => root.render(<Harness />));

		expect(state?.handleDrill({ data: {} })).toBe(false);
		expect(state?.breadcrumbs).toHaveLength(0);

		await act(async () => {
			expect(state?.handleDrill({ data: { key: "A-01" } })).toBe(true);
			expect(state?.handleDrill({ data: { key: "A-01" } })).toBe(false);
		});
		expect(state?.breadcrumbs).toHaveLength(2);
		expect(state?.queryParameters).toEqual([{ name: "selectedKey", value: "A-01" }]);

		await act(async () => state?.reset());
		expect(state?.breadcrumbs).toHaveLength(0);
		expect(state?.queryParameters).toEqual([]);

		await act(async () => root.unmount());
	});

	it("UI-12 keeps breadcrumbs and reset available when the target query fails", async () => {
		let state: DrillState | null = null;
		function Harness({ queryError }: { queryError?: string }) {
			state = useDrillDown(
				{ type: "card", cardConfig: { cardId: 10 } },
				{
					enabled: true,
					levels: [{ cardId: 12, paramName: "selectedKey", label: "Legacy" }],
				},
			);
			return queryError ? <span role="alert">{queryError}</span> : null;
		}

		container = document.createElement("div");
		document.body.appendChild(container);
		const root = createRoot(container);
		await act(async () => root.render(<Harness />));
		await act(async () => {
			expect(state?.handleDrill({ name: "A-01" })).toBe(true);
		});
		await act(async () => root.render(<Harness queryError="query failed" />));

		expect(container.querySelector('[role="alert"]')?.textContent).toBe("query failed");
		expect(state?.breadcrumbs).toHaveLength(2);
		expect(state?.queryParameters).toEqual([{ name: "selectedKey", value: "A-01" }]);

		await act(async () => state?.reset());
		expect(state?.breadcrumbs).toHaveLength(0);
		await act(async () => root.unmount());
	});
});
