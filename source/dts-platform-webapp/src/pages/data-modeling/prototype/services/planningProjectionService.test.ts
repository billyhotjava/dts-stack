// @vitest-environment jsdom
import { describe, expect, it } from "vitest";
import { normalizeModelingRequestFailure } from "./planningProjectionService";

describe("normalizeModelingRequestFailure", () => {
	it("uses a fixed permission message", () => {
		expect(
			normalizeModelingRequestFailure(
				{ response: { status: 403, data: { detail: "jdbc:postgresql://secret-host/db" } } },
				"读取失败。",
			),
		).toEqual({ kind: "permission", message: "当前账号无权访问该建模数据，请联系管理员授权。" });
	});

	it("does not expose server detail and only appends stable evidence", () => {
		const failure = normalizeModelingRequestFailure(
			{
				response: {
					status: 422,
					data: {
						code: "DBT_DRAFT_INVALID",
						detail: "select * from private_schema.secret_table",
						message: "models/private.sql contains {{ secret }}",
						correlationId: "req-83a.42",
					},
				},
			},
			"dbt 操作失败。",
		);

		expect(failure).toEqual({
			kind: "request",
			message: "dbt 操作失败。（错误码 DBT_DRAFT_INVALID；关联 ID req-83a.42）",
			code: "DBT_DRAFT_INVALID",
		});
		expect(failure.message).not.toContain("private_schema");
		expect(failure.message).not.toContain("secret");
	});

	it("keeps local validation errors when no server response exists", () => {
		expect(normalizeModelingRequestFailure(new Error("请先创建高级草稿"), "操作失败。")).toEqual({
			kind: "request",
			message: "请先创建高级草稿",
		});
	});
});
