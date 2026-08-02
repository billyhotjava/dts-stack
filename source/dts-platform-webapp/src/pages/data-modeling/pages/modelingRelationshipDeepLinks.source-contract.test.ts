import { readFileSync } from "node:fs";
import { describe, expect, it } from "vitest";

const read = (name: string) => readFileSync(new URL(`./${name}`, import.meta.url), "utf8");
const PLANNING = read("PlanningWorkspace.tsx");
const STANDARDS = read("StandardsWorkspace.tsx");
const METRICS = read("MetricsWorkspace.tsx");

describe("relationship graph destination deep links", () => {
	it("selects a real warehouse plan and consumes only planId", () => {
		expect(PLANNING).toMatch(/useSearchParams/);
		expect(PLANNING).toMatch(/searchParams\.get\("planId"\)/);
		expect(PLANNING).toMatch(/plan\.id === requestedPlanId/);
		expect(PLANNING).toMatch(/delete\("planId"\)/);
		expect(PLANNING).toMatch(/new URLSearchParams\(current\)/);
		expect(PLANNING).toContain("目标建设计划");
	});

	it("opens a real standard detail or explains that it is outside the current catalog", () => {
		expect(STANDARDS).toMatch(/useSearchParams/);
		expect(STANDARDS).toMatch(/searchParams\.get\("standardId"\)/);
		expect(STANDARDS).toMatch(/row\.id === requestedStandardId/);
		expect(STANDARDS).toMatch(/setDetailRow\(targetRow\)/);
		expect(STANDARDS).toMatch(/delete\("standardId"\)/);
		expect(STANDARDS).toMatch(/new URLSearchParams\(current\)/);
		expect(STANDARDS).toContain("不在当前");
	});

	it("selects a real indicator in its actual type view and consumes only indicatorId", () => {
		expect(METRICS).toMatch(/useSearchParams/);
		expect(METRICS).toMatch(/searchParams\.get\("indicatorId"\)/);
		expect(METRICS).toMatch(/item\.id === requestedIndicatorId/);
		expect(METRICS).toMatch(/classifyIndicator\(targetIndicator\)/);
		expect(METRICS).toMatch(/setSelection\(toMetricSelection\(targetIndicator\)\)/);
		expect(METRICS).toMatch(/delete\("indicatorId"\)/);
		expect(METRICS).toMatch(/new URLSearchParams\(current\)/);
		expect(METRICS).toContain("目标指标");
	});
});
