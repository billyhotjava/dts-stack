import { createContext, type ReactNode, useCallback, useContext, useMemo, useState } from "react";
import type { CardData } from "./types";

export interface ComponentDataFeedback {
	data: CardData | null;
	loading: boolean;
	error: string | null;
}

type FeedbackByComponentId = Record<string, ComponentDataFeedback>;

interface ScreenDataFeedbackDispatch {
	publishComponentDataFeedback: (componentId: string, feedback: ComponentDataFeedback) => void;
	clearComponentDataFeedback: (componentId: string) => void;
}

const ScreenDataFeedbackStateContext = createContext<FeedbackByComponentId | null>(null);
const ScreenDataFeedbackDispatchContext = createContext<ScreenDataFeedbackDispatch | null>(null);

export function ScreenDataFeedbackProvider({ children }: { children: ReactNode }) {
	const [feedbackByComponentId, setFeedbackByComponentId] = useState<FeedbackByComponentId>({});

	const publishComponentDataFeedback = useCallback((componentId: string, feedback: ComponentDataFeedback) => {
		setFeedbackByComponentId((previous) => {
			const current = previous[componentId];
			if (current?.data === feedback.data && current.loading === feedback.loading && current.error === feedback.error) {
				return previous;
			}
			return { ...previous, [componentId]: feedback };
		});
	}, []);

	const clearComponentDataFeedback = useCallback((componentId: string) => {
		setFeedbackByComponentId((previous) => {
			if (previous[componentId] === undefined) {
				return previous;
			}
			const next = { ...previous };
			delete next[componentId];
			return next;
		});
	}, []);

	const dispatchValue = useMemo<ScreenDataFeedbackDispatch>(
		() => ({
			publishComponentDataFeedback,
			clearComponentDataFeedback,
		}),
		[clearComponentDataFeedback, publishComponentDataFeedback],
	);

	return (
		<ScreenDataFeedbackDispatchContext.Provider value={dispatchValue}>
			<ScreenDataFeedbackStateContext.Provider value={feedbackByComponentId}>
				{children}
			</ScreenDataFeedbackStateContext.Provider>
		</ScreenDataFeedbackDispatchContext.Provider>
	);
}

export function useScreenDataFeedbackDispatch(): ScreenDataFeedbackDispatch {
	const context = useContext(ScreenDataFeedbackDispatchContext);
	if (!context) {
		throw new Error("useScreenDataFeedbackDispatch must be used within a ScreenDataFeedbackProvider");
	}
	return context;
}

export function useComponentDataFeedback(componentId?: string): ComponentDataFeedback | undefined {
	const context = useContext(ScreenDataFeedbackStateContext);
	if (!context) {
		throw new Error("useComponentDataFeedback must be used within a ScreenDataFeedbackProvider");
	}
	return componentId ? context[componentId] : undefined;
}
