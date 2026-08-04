// @vitest-environment jsdom

import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import { ConceptDimensionRecordDialog } from "./ConceptDimensionRecordDialog";
import type { ConceptDimensionDraft } from "./services/modelWorkbenchService";

let container: HTMLDivElement;
let root: Root;

const definition = (status: DimensionDefinitionView["status"] = "DRAFT"): DimensionDefinitionView => ({
	id: "dimension-1",
	systemCode: "dim_1234567890abcdef",
	domainId: "finance",
	name: "预算科目",
	definition: "统一预算科目定义",
	ownerId: "owner-1",
	reuseScope: "DOMAIN",
	hierarchies: [],
	scopeType: "DOMAIN",
	dataMartId: null,
	attributes: [],
	status,
	revision: status === "CURRENT" ? 2 : 1,
	checksum: "checksum-1",
	usageCount: 0,
	createdAt: "2026-08-04T10:00:00Z",
	updatedAt: "2026-08-04T10:00:00Z",
});

const draft = (status: DimensionDefinitionView["status"] = "DRAFT"): ConceptDimensionDraft => ({
	createKind: "dimension",
	base: null,
	definitionBase: definition(status),
	idempotencyKey: "concept-draft-1",
	domainId: "finance",
	name: "预算科目",
	description: "统一预算科目定义",
	reuseScope: "DOMAIN",
});

beforeEach(() => {
	(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
	container = document.createElement("div");
	document.body.appendChild(container);
	root = createRoot(container);
});

afterEach(async () => {
	await act(async () => root.unmount());
	container.remove();
});

describe("ConceptDimensionRecordDialog", () => {
	it("confirms a saved draft from version management", async () => {
		const onConfirm = vi.fn();
		await act(async () =>
			root.render(
				<ConceptDimensionRecordDialog
					canMaintain
					dialog="versions"
					draft={draft()}
					onClose={vi.fn()}
					onConfirm={onConfirm}
					saving={false}
				/>,
			),
		);

		expect(container.textContent).toContain("dim_1234567890abcdef");
		expect(container.textContent).toContain("DRAFT · r1");
		const confirm = Array.from(container.querySelectorAll("button")).find(
			(item) => item.textContent === "确认当前版本",
		);
		expect(confirm).toHaveProperty("disabled", false);
		await act(async () => confirm?.click());
		expect(onConfirm).toHaveBeenCalledOnce();
	});

	it("does not offer confirmation after the definition is current", async () => {
		await act(async () =>
			root.render(
				<ConceptDimensionRecordDialog
					canMaintain
					dialog="versions"
					draft={draft("CURRENT")}
					onClose={vi.fn()}
					onConfirm={vi.fn()}
					saving={false}
				/>,
			),
		);

		expect(container.textContent).toContain("CURRENT · r2");
		expect(container.textContent).not.toContain("确认当前版本");
	});

	it("states the release-record contract boundary", async () => {
		await act(async () =>
			root.render(
				<ConceptDimensionRecordDialog
					canMaintain
					dialog="releases"
					draft={draft()}
					onClose={vi.fn()}
					onConfirm={vi.fn()}
					saving={false}
				/>,
			),
		);

		expect(container.textContent).toContain("当前维度定义接口未提供独立发布记录");
		expect(container.textContent).not.toContain("确认当前版本");
	});
});
