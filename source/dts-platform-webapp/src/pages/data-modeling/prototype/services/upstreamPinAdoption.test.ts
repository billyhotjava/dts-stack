import { describe, expect, it } from "vitest";
import type { ModelInputAvailability, ModelUpstreamPin } from "@/api/modelInputInspectionApi";
import type { ModelSpecDraft } from "./modelWorkbenchService";
import { adoptableUpstreamPins, withUpstreamPin } from "./upstreamPinAdoption";

const UPSTREAM = "30000000-0000-0000-0000-000000000001";
const pin = (revision = 3): ModelUpstreamPin => ({
	modelSpecId: UPSTREAM,
	revision,
	checksum: "a".repeat(64),
	implementationRevision: 1,
	implementationChecksum: "b".repeat(64),
	dbtUniqueId: "model.dts.prjdemo_dwd_project_task_snapshot",
});
const availability = (overrides: Partial<ModelInputAvailability> = {}): Record<string, ModelInputAvailability> => ({
	[UPSTREAM]: {
		modelSpecId: UPSTREAM,
		revision: 3,
		checksum: "a".repeat(64),
		implementationState: "ACTIVE",
		currentPin: pin(),
		selectable: true,
		blockReason: null,
		referenceState: "UNSELECTED",
		selectedPin: null,
		referenceReason: null,
		...overrides,
	},
});
const draft = (inputs: ModelSpecDraft["authoringImplementationInputs"]): ModelSpecDraft =>
	({
		dependsOn: [{ modelSpecId: UPSTREAM, revision: 3 }],
		authoringImplementationInputs: inputs,
	}) as ModelSpecDraft;

describe("upstream pin adoption", () => {
	it("adopts the current implementation for a selected upstream that only carries its design revision", () => {
		const current = draft([{ modelSpecId: UPSTREAM, revision: 3, checksum: "a".repeat(64) }]);

		const adoptable = adoptableUpstreamPins(current, availability());
		const next = adoptable.reduce(withUpstreamPin, current);

		expect(adoptable).toEqual([pin()]);
		expect(next.authoringImplementationInputs).toEqual([pin()]);
		expect(next.dependsOn).toEqual([{ modelSpecId: UPSTREAM, revision: 3 }]);
		expect(adoptableUpstreamPins(next, availability())).toEqual([]);
	});

	it("leaves existing pins, drifted designs and unselectable upstreams to explicit user confirmation", () => {
		expect(
			adoptableUpstreamPins(draft([pin()]), availability({ currentPin: { ...pin(), implementationRevision: 2 } })),
		).toEqual([]);
		expect(adoptableUpstreamPins(draft([]), availability({ currentPin: pin(4) }))).toEqual([]);
		expect(adoptableUpstreamPins(draft([]), availability({ selectable: false }))).toEqual([]);
		expect(adoptableUpstreamPins(draft([]), {})).toEqual([]);
	});
});
