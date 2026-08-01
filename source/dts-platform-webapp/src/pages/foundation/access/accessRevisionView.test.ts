import { expect, test } from "vitest";
import { resolveAccessRevisionView } from "./accessRevisionView";

test("an ACTIVE task with a DRAFT edit admits the draft but executes only the active revision", () => {
	const view = resolveAccessRevisionView(
		{ status: "active", revisionNumber: 3, revisionState: "DRAFT" },
		[
			{ revisionNumber: 3, revisionState: "DRAFT", sourceKind: "database", effectiveConfigChecksum: "draft" },
			{ revisionNumber: 2, revisionState: "ACTIVE", sourceKind: "database", effectiveConfigChecksum: "active" },
		],
		false,
	);
	expect(view.hasDraftRevision).toBe(true);
	expect(view.draftRevisionNumber).toBe(3);
	expect(view.activeRevisionNumber).toBe(2);
	expect(view.canExecuteActiveRevision).toBe(true);
	expect(view.executeReason).toBe("将执行当前生效版本 R2");
});

test("execution fails closed when revision history cannot prove an active revision", () => {
	const view = resolveAccessRevisionView({ status: "active", revisionNumber: 3, revisionState: "DRAFT" }, [], true);
	expect(view.canExecuteActiveRevision).toBe(false);
	expect(view.executeReason).toBe("生效 Revision 加载失败，已禁止执行");
});
