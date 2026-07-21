import { describe, expect, it } from "vitest";
import {
	LEGACY_MODELING_PATHS,
	resolveModelingCompatibilityTarget,
} from "./modelingCompatibilityRoute.ts";

const redirect = (path: string, query = "", mapped?: Parameters<typeof resolveModelingCompatibilityTarget>[2]) => {
	const result = resolveModelingCompatibilityTarget(path, new URLSearchParams(query), mapped);
	expect(result.kind).toBe("redirect");
	if (result.kind !== "redirect") throw new Error("expected redirect");
	return new URL(result.to, "http://dts.local");
};

describe("Sprint-67 modeling compatibility routes", () => {
	it("maps every retired deep link to a non-legacy canonical target without loops", () => {
		const targets = [
			redirect("/modeling/semantic/subjects", "active=domain-1"),
			redirect("/modeling/semantic/objects", "planId=plan-1&domainId=domain-1"),
			redirect("/modeling/semantic/models", "planId=plan-1&modelSpecId=model-1"),
			redirect("/modeling/semantic/metrics", "modelSpecId=model-1"),
			redirect("/modeling/semantic/publish", "modelId=model-1"),
			redirect("/modeling/semantic/runs", "modelId=model-1"),
			redirect("/studio/low-code-development", "planId=plan-1&domainId=domain-1"),
			redirect("/modeling/dbt-files", "modelId=model-1"),
		];

		expect(targets.map((target) => target.pathname)).toEqual([
			"/governance/subjects",
			"/modeling/dimensions",
			"/modeling/models",
			"/modeling/metric-workbench",
			"/modeling/models",
			"/ops/instances",
			"/modeling/models",
			"/studio/sql-modeling",
		]);
		for (const target of targets) expect(LEGACY_MODELING_PATHS).not.toContain(target.pathname);
	});

	it("normalizes legacy aliases, drops unknown parameters and rejects unsafe returnTo", () => {
		const target = redirect(
			"/modeling/semantic/publish",
			"planningId=plan-1&active=domain-1&modelId=model-1&revision=3&objectId=obj-1&processId=p-1&returnTo=https%3A%2F%2Fevil.example&junk=1",
		);

		expect(Object.fromEntries(target.searchParams)).toEqual({
			view: "release",
			planId: "plan-1",
			domainId: "domain-1",
			modelSpecId: "model-1",
			revision: "3",
		});
	});

	it("keeps a safe internal returnTo path", () => {
		const target = redirect(
			"/modeling/semantic/models",
			"returnTo=%2Fmodeling%2Fplans%2Fplan-1%2Fbaseline%3Ftab%3Dcategories",
		);
		expect(target.searchParams.get("returnTo")).toBe("/modeling/plans/plan-1/baseline?tab=categories");
	});

	it("uses a migrated object target and never carries objectId into the new URL", () => {
		const target = redirect("/modeling/semantic/objects", "objectId=obj-1", {
			modelSpecId: "dimension-1",
			modelType: "DIMENSION",
			planId: "plan-1",
			domainId: "domain-1",
			revision: "4",
		});

		expect(target.pathname).toBe("/modeling/dimensions");
		expect(target.searchParams.get("planId")).toBe("plan-1");
		expect(target.searchParams.get("domainId")).toBe("domain-1");
		expect(target.searchParams.get("objectId")).toBeNull();
	});

	it("returns an explicit recovery state when an object mapping is unavailable", () => {
		expect(
			resolveModelingCompatibilityTarget(
				"/modeling/semantic/objects",
				new URLSearchParams("objectId=missing-object"),
			),
		).toEqual({ kind: "recovery", code: "NEEDS_CLASSIFICATION", legacyObjectId: "missing-object" });
	});
});
