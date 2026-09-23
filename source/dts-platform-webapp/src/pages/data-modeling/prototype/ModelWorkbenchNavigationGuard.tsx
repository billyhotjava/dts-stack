import { type RefObject, useCallback, useEffect } from "react";
import { type BlockerFunction, useBlocker } from "react-router";
import { ModelUnsavedChangesDialog } from "./ModelUnsavedChangesDialog";
import { shouldBlockWorkbenchNavigation } from "./modelingWorkbenchNavigation";
export function ModelWorkbenchNavigationGuard({
	dirty,
	savingRef,
	discardConfirmedRef,
	onSave,
	onDiscard,
}: {
	dirty: boolean;
	savingRef: RefObject<boolean>;
	/** Set when the editor already asked the user; lets exactly the next navigation through. */
	discardConfirmedRef?: { current: boolean };
	onSave: () => Promise<boolean>;
	onDiscard: () => void;
}) {
	const blocker = useBlocker(
		useCallback<BlockerFunction>(
			({ currentLocation, nextLocation }) => {
				if (discardConfirmedRef?.current) {
					discardConfirmedRef.current = false;
					return false;
				}
				return (
					!savingRef.current &&
					shouldBlockWorkbenchNavigation(
						dirty,
						currentLocation.pathname + currentLocation.search,
						nextLocation.pathname + nextLocation.search,
					)
				);
			},
			[dirty, savingRef, discardConfirmedRef],
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
