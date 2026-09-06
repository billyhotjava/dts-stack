import { type RefObject, useCallback, useEffect } from "react";
import { type BlockerFunction, useBlocker } from "react-router";
import { ModelUnsavedChangesDialog } from "./ModelUnsavedChangesDialog";
import { shouldBlockWorkbenchNavigation } from "./modelingWorkbenchNavigation";
export function ModelWorkbenchNavigationGuard({
	dirty,
	savingRef,
	onSave,
	onDiscard,
}: {
	dirty: boolean;
	savingRef: RefObject<boolean>;
	onSave: () => Promise<boolean>;
	onDiscard: () => void;
}) {
	const blocker = useBlocker(
		useCallback<BlockerFunction>(
			({ currentLocation, nextLocation }) =>
				!savingRef.current &&
				shouldBlockWorkbenchNavigation(
					dirty,
					currentLocation.pathname + currentLocation.search,
					nextLocation.pathname + nextLocation.search,
				),
			[dirty, savingRef],
		),
	);
	useEffect(() => {
		if (!dirty) return;
		const handle = (event: BeforeUnloadEvent) => {
			event.preventDefault();
			event.returnValue = "";
		};
		window.addEventListener("beforeunload", handle);
		return () => window.removeEventListener("beforeunload", handle);
	}, [dirty]);
	return blocker.state === "blocked" ? (
		<ModelUnsavedChangesDialog
			onSave={onSave}
			onDiscard={() => {
				onDiscard();
				blocker.proceed();
			}}
			onSaved={blocker.proceed}
			onStay={blocker.reset}
		/>
	) : null;
}
