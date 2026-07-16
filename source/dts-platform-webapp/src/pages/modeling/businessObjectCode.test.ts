import { describe, expect, it } from "vitest";
import { generateBusinessObjectCode } from "./businessObjectCode";

const NOW = new Date("2026-07-11T10:00:00");

describe("business object code generation", () => {
	it("generates the first code of the day with sequence 001", () => {
		expect(generateBusinessObjectCode([], NOW)).toBe("BO20260711001");
	});

	it("skips codes that are already taken, case- and whitespace-insensitively", () => {
		const existing = ["bo20260711001", " BO20260711002 ", "BO20260710009"];
		expect(generateBusinessObjectCode(existing, NOW)).toBe("BO20260711003");
	});

	it("fills gaps left by deleted objects deterministically", () => {
		const existing = ["BO20260711001", "BO20260711003"];
		expect(generateBusinessObjectCode(existing, NOW)).toBe("BO20260711002");
	});

	it("stays unique when the daily sequence is exhausted", () => {
		const existing = Array.from({ length: 999 }, (_, i) => `BO20260711${String(i + 1).padStart(3, "0")}`);
		const code = generateBusinessObjectCode(existing, NOW);
		expect(code.startsWith("BO20260711")).toBe(true);
		expect(existing).not.toContain(code);
	});
});
