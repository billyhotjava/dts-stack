import { useCallback } from "react";
import { useSearchParams } from "react-router";
import OrchestrationTaskEditor from "./OrchestrationTaskEditor";
import OrchestrationTaskList from "./OrchestrationTaskList";
import { type DesignFormValues, designPayload } from "./orchestrationDesignModel";

export { designPayload };
export type { DesignFormValues };

type OrchestrationTabKey = "design" | "runs";

const VALID_TABS: ReadonlyArray<OrchestrationTabKey> = ["design", "runs"];

function isValidTab(value: string | null): value is OrchestrationTabKey {
	return Boolean(value && (VALID_TABS as ReadonlyArray<string>).includes(value));
}

function parsePositiveId(value: string | null): number | null {
	if (!value) return null;
	const parsed = Number(value);
	return Number.isFinite(parsed) && parsed > 0 ? Math.floor(parsed) : null;
}

export default function OrchestrationPage() {
	const [searchParams, setSearchParams] = useSearchParams();
	const requestedTaskId = parsePositiveId(searchParams.get("taskId"));
	const requestedExecutionId = parsePositiveId(searchParams.get("executionId"));
	const requestedTab = searchParams.get("tab");
	const activeKey: OrchestrationTabKey = isValidTab(requestedTab) ? requestedTab : "design";

	const updateQuery = useCallback(
		(values: Record<string, string | number | null | undefined>) => {
			setSearchParams(
				(current) => {
					const next = new URLSearchParams(current);
					Object.entries(values).forEach(([key, value]) => {
						if (value === null || value === undefined || value === "") next.delete(key);
						else next.set(key, String(value));
					});
					return next;
				},
				{ replace: true },
			);
		},
		[setSearchParams],
	);

	if (!requestedTaskId) {
		return (
			<OrchestrationTaskList onOpenTask={(taskId, view) => updateQuery({ taskId, tab: view, executionId: null })} />
		);
	}

	return (
		<OrchestrationTaskEditor
			key={requestedTaskId}
			taskId={requestedTaskId}
			executionId={requestedExecutionId}
			activeKey={activeKey}
			onQuery={updateQuery}
			onBack={() => updateQuery({ taskId: null, tab: null, executionId: null })}
		/>
	);
}
