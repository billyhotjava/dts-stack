// @vitest-environment jsdom
import { expect, it } from "vitest";
import type { ModelDeliveryStatus } from "@/api/modelDeliveryStatusApi";
import { currentCatalogOutputs } from "./ModelCatalogDeliveryPanel";
const output = (resourceId: string | null, matchesCurrentTarget = true) => ({
	resourceId,
	state: "SUCCEEDED" as const,
	reasonCode: null,
	message: "",
	matchesCurrentTarget,
	updatedAt: null,
});
const delivery = (
	outputs: ReturnType<typeof output>[],
	matchesCurrentTarget = true,
): Pick<ModelDeliveryStatus, "steps"> => ({
	steps: [
		{
			key: "catalog",
			matchesCurrentTarget,
			resourceId: null,
			state: "WAITING_INPUT",
			reasonCode: null,
			message: "",
			evidenceRevision: null,
			updatedAt: null,
			outputs,
		},
	],
});
it("keeps registered outputs when catalog is partial", () =>
	expect(currentCatalogOutputs(delivery([output("a"), output("b")]))).toHaveLength(2));
it("excludes stale outputs", () => expect(currentCatalogOutputs(delivery([output("a", false)]))).toEqual([]));
it("keeps a registered output visible to read-only users", () =>
	expect(currentCatalogOutputs(delivery([output("a")]))[0]?.resourceId).toBe("a"));
