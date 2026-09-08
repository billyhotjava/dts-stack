// @vitest-environment jsdom
import { describe, expect, it } from "vitest";
import { normalizeModelingRequestFailure } from "./planningProjectionService";

describe("normalizeModelingRequestFailure", () => {
	it("guides a missing FACT business process back to design while retaining diagnostic evidence", () => {
		const failure = normalizeModelingRequestFailure({ response: { status: 422, data: {
			errorCode: "MODEL_LIFECYCLE_GATE_BLOCKED", correlationId: "req-fact-1",
			data: { blockers: [{ code: "MODEL_SPEC_BUSINESS_PROCESS_REQUIRED", message: "FACT requires a stable business process reference" }] },
		} } }, "物化构建未能启动。");
		expect(failure.message).toContain("模型设计");
		expect(failure.message).toContain("选择业务过程并保存模型");
		expect(failure.message).toContain("数仓规划");
		expect(failure.message).toContain("MODEL_LIFECYCLE_GATE_BLOCKED");
		expect(failure.message).toContain("req-fact-1");
		expect(failure.message).not.toContain("FACT requires");
	});

	it("shows actionable lifecycle blockers instead of the generic English gate message", () => {
		const failure = normalizeModelingRequestFailure(
			{
				response: {
					status: 422,
					data: {
						errorCode: "MODEL_LIFECYCLE_GATE_BLOCKED",
						message: "ModelSpec lifecycle gate is blocked",
						data: {
							stage: "IMPLEMENTATION_READY",
							blockers: [
								{
									code: "MODEL_SPEC_FACT_TIME_SHAPE_MISMATCH",
									field: "timeSemantics",
									message: "业务时间与事实形态不匹配",
								},
								{
									code: "MODEL_SPEC_FACT_INPUT_REQUIRED",
									field: "sourceRefs",
									message: "请至少选择已确认的上游输入来源或锁定上游模型",
								},
							],
						},
					},
				},
			},
			"物化构建未能启动。",
		);
		expect(failure.message).toContain("业务时间与事实形态不匹配");
		expect(failure.message).toContain("请至少选择已确认的上游输入来源");
		expect(failure.message).not.toContain("ModelSpec lifecycle gate");
	});

	it("keeps malformed lifecycle evidence bounded and uses a Chinese fallback", () => {
		const failure = normalizeModelingRequestFailure(
			{
				response: {
					status: 422,
					data: {
						errorCode: "MODEL_LIFECYCLE_GATE_BLOCKED",
						message: "ModelSpec lifecycle gate is blocked",
						data: { blockers: [null, { message: "x".repeat(501) }, { message: "bad\u0000message" }] },
					},
				},
			},
			"构建失败。",
		);
		expect(failure.message).toContain("模型前置校验未通过");
		expect(failure.message).not.toContain("bad");
	});

	it("explains stale source bindings in Chinese while preserving the diagnostic code", () => {
		const failure = normalizeModelingRequestFailure(
			{
				response: {
					status: 422,
					data: {
						code: "MODEL_SPEC_SOURCE_BINDING_INVALID",
						message: "Every source must resolve to the confirmed source version in the current warehouse plan",
					},
				},
			},
			"模型保存失败。",
		);
		expect(failure.message).toContain("请在数仓规划中确认来源");
		expect(failure.message).toContain("MODEL_SPEC_SOURCE_BINDING_INVALID");
		expect(failure.message).not.toContain("Every source");
	});

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

	it("keeps bounded model-contract guidance returned by the canonical API", () => {
		expect(
			normalizeModelingRequestFailure(
				{
					response: {
						status: 422,
						data: {
							code: "MODEL_IMPLEMENTATION_INPUT_KIND_NOT_ALLOWED",
							message: "当前模型类型不支持所选实现输入方式",
						},
					},
				},
				"模型保存失败。",
			),
		).toEqual({
			kind: "request",
			message:
				"模型保存失败。 当前模型类型不支持所选实现输入方式（错误码 MODEL_IMPLEMENTATION_INPUT_KIND_NOT_ALLOWED）",
			code: "MODEL_IMPLEMENTATION_INPUT_KIND_NOT_ALLOWED",
		});
	});

	it("keeps local validation errors when no server response exists", () => {
		expect(normalizeModelingRequestFailure(new Error("请先创建高级草稿"), "操作失败。")).toEqual({
			kind: "request",
			message: "请先创建高级草稿",
		});
	});

	it("surfaces undeclared dbt dependencies without exposing the raw server message", () => {
		const failure = normalizeModelingRequestFailure(
			{
				response: {
					status: 422,
					data: {
						code: "DBT_DRAFT_DEPENDENCY_UNDECLARED",
						message: "The implementation references dependencies that are not declared by ModelSpec",
						data: { dependencies: ["model.pjm.budget", "source.pjm.ods.orders"] },
						correlationId: "req-1",
					},
				},
			},
			"模型创作草稿校验失败。",
		);

		expect(failure).toEqual({
			kind: "request",
			message:
				"模型创作草稿校验失败。 SQL 引用了未在模型依赖中声明的对象：model.pjm.budget、source.pjm.ods.orders（错误码 DBT_DRAFT_DEPENDENCY_UNDECLARED；关联 ID req-1）",
			code: "DBT_DRAFT_DEPENDENCY_UNDECLARED",
		});
		expect(failure.message).not.toContain("references dependencies");
	});

	it("surfaces missing dbt dependencies declared by the model but unused in SQL", () => {
		const failure = normalizeModelingRequestFailure(
			{
				response: {
					status: 422,
					data: {
						code: "DBT_DRAFT_DEPENDENCY_MISSING",
						data: { dependencies: ["model.pjm.budget"] },
					},
				},
			},
			"模型创作草稿校验失败。",
		);

		expect(failure.message).toBe(
			"模型创作草稿校验失败。 模型依赖中已声明但 SQL 未使用的对象：model.pjm.budget（错误码 DBT_DRAFT_DEPENDENCY_MISSING）",
		);
	});

	it("drops unsafe dependency entries from dbt dependency guidance", () => {
		const failure = normalizeModelingRequestFailure(
			{
				response: {
					status: 422,
					data: {
						code: "DBT_DRAFT_DEPENDENCY_UNDECLARED",
						data: {
							dependencies: ["model.pjm.budget", "SELECT * FROM secret_table; DROP TABLE", "model.<script>"],
						},
					},
				},
			},
			"校验失败。",
		);

		expect(failure.message).toBe(
			"校验失败。 SQL 引用了未在模型依赖中声明的对象：model.pjm.budget（错误码 DBT_DRAFT_DEPENDENCY_UNDECLARED）",
		);
	});
});
