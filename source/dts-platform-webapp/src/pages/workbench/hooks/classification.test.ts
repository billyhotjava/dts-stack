import { describe, expect, it } from "vitest";
import { classificationColor } from "./classification";

describe("classificationColor", () => {
	it("maps_S1_to_red", () => {
		expect(classificationColor("S1")).toBe("red");
	});

	it("maps_S2_to_volcano", () => {
		expect(classificationColor("S2")).toBe("volcano");
	});

	it("maps_S3_to_orange", () => {
		expect(classificationColor("S3")).toBe("orange");
	});

	it("maps_S4_to_blue", () => {
		expect(classificationColor("S4")).toBe("blue");
	});

	it("falls_back_to_default_for_unknown_values", () => {
		expect(classificationColor("")).toBe("default");
		expect(classificationColor("SX")).toBe("default");
		expect(classificationColor("TOP_SECRET")).toBe("default");
	});
});
