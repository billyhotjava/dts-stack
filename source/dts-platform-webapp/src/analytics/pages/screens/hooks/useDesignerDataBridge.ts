import { useEffect, useRef } from "react";
import { resolveSourceColumnsMeta, shouldPersistSourceColumns } from "../renderers/shared/sourceColumns";
import type { ComponentDataFeedback } from "../ScreenDataFeedbackContext";
import type { CardData } from "../types";

interface DesignerDataBridgeOptions {
	componentId: string;
	mode: "designer" | "preview";
	data: CardData | null;
	loading: boolean;
	error: string | null;
	previousColumns?: Array<{ name?: string }>;
	onConfigMeta?: (meta: Record<string, unknown>) => void;
	onDataFeedback?: (componentId: string, feedback: ComponentDataFeedback | null) => void;
}

/**
 * Reuses the renderer's existing query result for authoring feedback.
 * Sample rows stay transient; only column metadata is eligible for persistence.
 */
export function useDesignerDataBridge({
	componentId,
	mode,
	data,
	loading,
	error,
	previousColumns,
	onConfigMeta,
	onDataFeedback,
}: DesignerDataBridgeOptions): void {
	const onConfigMetaRef = useRef(onConfigMeta);
	const onDataFeedbackRef = useRef(onDataFeedback);
	onConfigMetaRef.current = onConfigMeta;
	onDataFeedbackRef.current = onDataFeedback;

	useEffect(() => {
		if (!onConfigMetaRef.current) return;
		const nextColumns = resolveSourceColumnsMeta(data);
		if (shouldPersistSourceColumns(previousColumns, nextColumns)) {
			onConfigMetaRef.current({ _sourceColumns: nextColumns });
		}
	}, [data, previousColumns]);

	useEffect(() => {
		if (mode !== "designer") return;
		onDataFeedbackRef.current?.(componentId, { data, loading, error });
	}, [componentId, data, error, loading, mode]);

	useEffect(() => {
		if (mode !== "designer") return undefined;
		return () => onDataFeedbackRef.current?.(componentId, null);
	}, [componentId, mode]);
}
