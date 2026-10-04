import { describe, expect, it } from "vitest";
import type { ModelAuthoringContext, ModelAuthoringValidation } from "@/api/modelAuthoringApi";
import {
	hasCurrentAuthoringValidation,
	resolveModelWorkflowAction,
	type ModelWorkflowInput,
} from "./modelWorkflowAction";

const now = Date.parse("2026-09-06T00:00:00Z");
const context = {
	allowedActions: ["SAVE", "VALIDATE", "COMMIT"],
	openDraft: { draftId: "draft-1", state: "VALIDATED", etag: "etag-1" },
} as ModelAuthoringContext;
const validation: ModelAuthoringValidation = {
	modelIssues: [],
	projectionIssues: [],
	implementationValidation: {
		draftId: "draft-1",
		state: "VALIDATED",
		etag: "etag-1",
		expiresAt: "2026-09-07T00:00:00Z",
		validatedChecksum: "checksum",
		diagnostics: [],
		proposedStructure: [],
	},
};
const input = (patch: Partial<ModelWorkflowInput> = {}): ModelWorkflowInput => ({
	persisted: true,
	published: false,
	dirty: false,
	readOnly: false,
	canMaintain: true,
	context,
	validation,
	...patch,
});

describe("authoring workflow capability and receipt boundary", () => {
	it("saves new models and unsaved changes before offering later actions", () => {
		expect(resolveModelWorkflowAction(input({ persisted: false, context: null }), now)).toMatchObject({
			action: "save",
			enabled: true,
		});
		expect(resolveModelWorkflowAction(input({ dirty: true }), now)).toMatchObject({ action: "save", enabled: true });
	});
	it("commits only a current receipt with the server capability", () => {
		expect(resolveModelWorkflowAction(input(), now)).toMatchObject({ action: "commit", enabled: true });
		expect(
			resolveModelWorkflowAction(input({ context: { ...context, allowedActions: ["VALIDATE"] } }), now),
		).toMatchObject({ action: "validate", enabled: true });
	});
	it.each(["ERROR", "FATAL"])("rejects %s diagnostics from either validation source", (severity) => {
		const issue = { code: "INVALID_SQL", severity, message: "SQL 无效" };
		expect(hasCurrentAuthoringValidation(context, { ...validation, projectionIssues: [issue] }, now)).toBe(false);
		expect(
			hasCurrentAuthoringValidation(
				context,
				{ ...validation, implementationValidation: { ...validation.implementationValidation!, diagnostics: [issue] } },
				now,
			),
		).toBe(false);
	});
	it("does not reject non-blocking warnings", () => {
		expect(
			hasCurrentAuthoringValidation(
				context,
				{ ...validation, projectionIssues: [{ code: "NOTICE", severity: "WARNING", message: "提示" }] },
				now,
			),
		).toBe(true);
	});
	it.each(["etag", "draftId", "expiresAt"] as const)("requires current %s", (field) => {
		const stale = {
			...validation,
			implementationValidation: {
				...validation.implementationValidation!,
				[field]: field === "expiresAt" ? "2026-09-05T00:00:00Z" : "other",
			},
		};
		expect(resolveModelWorkflowAction(input({ validation: stale }), now)).toMatchObject({
			action: "validate",
			enabled: true,
		});
	});
	it("fails closed on permission loading, denied save, and archived read-only views", () => {
		expect(resolveModelWorkflowAction(input({ context: null }), now).enabled).toBe(false);
		expect(
			resolveModelWorkflowAction(input({ dirty: true, context: { ...context, allowedActions: [] } }), now).enabled,
		).toBe(false);
		expect(resolveModelWorkflowAction(input({ readOnly: true }), now).enabled).toBe(false);
	});
	it("requires an explicit fork capability for a published version", () => {
		expect(resolveModelWorkflowAction(input({ published: true, readOnly: true }), now)).toMatchObject({
			action: "fork",
			enabled: false,
		});
		expect(
			resolveModelWorkflowAction(
				input({ published: true, readOnly: true, context: { ...context, allowedActions: ["FORK_DRAFT"] } }),
				now,
			),
		).toMatchObject({ action: "fork", enabled: true });
	});
	it("opens delivery only after the implementation is committed without pending edits", () => {
		const implementation = {} as NonNullable<ModelAuthoringContext["implementation"]>;
		expect(
			resolveModelWorkflowAction(input({ context: { ...context, implementation, openDraft: null } }), now),
		).toMatchObject({ action: "deliver", enabled: true });
		expect(
			resolveModelWorkflowAction(
				input({
					context: { ...context, implementation, openDraft: { ...context.openDraft!, state: "DRAFT" } },
					validation: null,
				}),
				now,
			),
		).toMatchObject({ action: "validate" });
	});
});
