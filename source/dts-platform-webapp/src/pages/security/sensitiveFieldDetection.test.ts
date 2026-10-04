import { describe, expect, it } from "vitest";
import { detectSensitiveFields } from "./sensitiveFieldDetection";

describe("sensitive field detection", () => {
	it("recognizes names and comments without treating all identifiers or names as personal data", () => {
		const fields = [
			"userPhone",
			"email_address",
			"idcard_no",
			"bank_account_number",
			"password",
			"order_id",
			"project_name",
			"mobile_model",
		].map((name) => ({ name, dataType: "STRING" }));
		fields.push({ name: "contact", dataType: "STRING", comment: "客户联系电话" } as (typeof fields)[number]);
		const detected = detectSensitiveFields(fields, [], "d1");
		expect(detected.map((row) => row.column)).toEqual([
			"userPhone",
			"email_address",
			"idcard_no",
			"bank_account_number",
			"password",
			"contact",
		]);
		expect(detected.find((row) => row.column === "password")?.function).toBe("REDACT");
		expect(detected.find((row) => row.column === "contact")?.reason).toBe("字段注释匹配");
	});
	it("keeps existing global and dataset rules and deduplicates repeated metadata", () => {
		const fields = ["phone", "phone", "email", "password"].map((name) => ({ name, dataType: "STRING" }));
		const existing = [
			{ column: "EMAIL", dataset: { id: "d1" } },
			{ column: "password", dataset: null },
			{ column: "phone", dataset: { id: "d2" } },
		];
		expect(detectSensitiveFields(fields, existing, "d1").map((row) => row.column)).toEqual(["phone"]);
	});
});
