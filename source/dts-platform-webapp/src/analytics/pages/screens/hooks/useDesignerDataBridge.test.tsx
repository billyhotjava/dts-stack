// @vitest-environment jsdom
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { CardData } from "../types";
import { useDesignerDataBridge } from "./useDesignerDataBridge";

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

interface HarnessProps {
	mode?: "designer" | "preview";
	data: CardData | null;
	onConfigMeta: (meta: Record<string, unknown>) => void;
	onDataFeedback: (componentId: string, feedback: unknown) => void;
}

function Harness({ mode = "designer", data, onConfigMeta, onDataFeedback }: HarnessProps) {
	useDesignerDataBridge({
		componentId: "chart-1",
		mode,
		data,
		loading: false,
		error: null,
		previousColumns: [],
		onConfigMeta,
		onDataFeedback,
	});
	return null;
}

describe("useDesignerDataBridge", () => {
	let container: HTMLDivElement | null = null;
	let root: Root | null = null;

	afterEach(async () => {
		if (root) await act(async () => root?.unmount());
		container?.remove();
		container = null;
		root = null;
	});

	it("publishes the renderer result transiently while persisting only column metadata", async () => {
		const onConfigMeta = vi.fn();
		const onDataFeedback = vi.fn();
		const data: CardData = {
			cols: [{ name: "region", display_name: "区域", base_type: "type/Text" }],
			rows: [["华东"]],
		};
		container = document.createElement("div");
		document.body.appendChild(container);
		root = createRoot(container);

		await act(async () => {
			root?.render(<Harness data={data} onConfigMeta={onConfigMeta} onDataFeedback={onDataFeedback} />);
		});

		expect(onConfigMeta).toHaveBeenCalledWith({
			_sourceColumns: [{ name: "region", displayName: "区域", baseType: "type/Text" }],
		});
		expect(onConfigMeta.mock.calls.flat()).not.toContainEqual(expect.objectContaining({ rows: expect.anything() }));
		expect(onDataFeedback).toHaveBeenCalledWith("chart-1", { data, loading: false, error: null });

		await act(async () => root?.unmount());
		root = null;
		expect(onDataFeedback).toHaveBeenLastCalledWith("chart-1", null);
	});

	it("does not expose sample feedback from preview mode", async () => {
		const onConfigMeta = vi.fn();
		const onDataFeedback = vi.fn();
		container = document.createElement("div");
		document.body.appendChild(container);
		root = createRoot(container);

		await act(async () => {
			root?.render(<Harness mode="preview" data={null} onConfigMeta={onConfigMeta} onDataFeedback={onDataFeedback} />);
		});

		expect(onDataFeedback).not.toHaveBeenCalled();
	});
});
