import Editor, { type OnMount, useMonaco } from "@monaco-editor/react";
import { useEffect, useRef, useState } from "react";
import type { editor as MonacoEditor } from "monaco-editor";
import { configureMonacoLoader } from "@/components/monaco/configureMonaco";
import { dbtEditorLanguage, isDbtSaveShortcut, toDbtMarkerData } from "./dbtCodeEditorContract";

export { dbtEditorLanguage, isDbtSaveShortcut } from "./dbtCodeEditorContract";

export type DbtEditorDiagnostic = {
	path?: string | null;
	severity: "ERROR" | "WARNING" | string;
	message: string;
	line?: number | null;
	column?: number | null;
};

export type DbtEditorFocusLocation = {
	path: string;
	line: number;
	column: number;
	token: number;
};

export function DbtCodeEditor({
	path,
	content,
	readOnly,
	diagnostics,
	focusLocation,
	onChange,
	onSave,
}: {
	path: string;
	content: string;
	readOnly: boolean;
	diagnostics: DbtEditorDiagnostic[];
	focusLocation?: DbtEditorFocusLocation | null;
	onChange: (content: string) => void;
	onSave: () => void;
}) {
	const hostRef = useRef<HTMLDivElement | null>(null);
	const monaco = useMonaco();
	const [editor, setEditor] = useState<MonacoEditor.IStandaloneCodeEditor | null>(null);
	const handleMount: OnMount = (mountedEditor) => setEditor(mountedEditor);

	useEffect(() => {
		const model = editor?.getModel();
		if (!monaco || !model) return;
		monaco.editor.setModelMarkers(
			model,
			"dts-dbt-draft",
			toDbtMarkerData(diagnostics).map((marker) => ({
				...marker,
				severity: marker.severity === "ERROR" ? monaco.MarkerSeverity.Error : monaco.MarkerSeverity.Warning,
			})),
		);
		return () => monaco.editor.setModelMarkers(model, "dts-dbt-draft", []);
	}, [diagnostics, editor, monaco, path]);

	useEffect(() => {
		if (!editor || !focusLocation || focusLocation.path !== path || focusLocation.line < 1) return;
		const position = { lineNumber: focusLocation.line, column: Math.max(1, focusLocation.column) };
		editor.setPosition(position);
		editor.revealPositionInCenter(position);
		editor.focus();
	}, [editor, focusLocation, path]);

	useEffect(() => {
		const host = hostRef.current;
		if (!host) return;
		const onKeyDown = (event: KeyboardEvent) => {
			if (!readOnly && isDbtSaveShortcut(event)) {
				event.preventDefault();
				onSave();
			}
		};
		host.addEventListener("keydown", onKeyDown);
		return () => host.removeEventListener("keydown", onKeyDown);
	}, [onSave, readOnly]);

	configureMonacoLoader();
	return (
		<div className="dmx-dbt-code-editor" ref={hostRef}>
			<Editor
				height="420px"
				language={dbtEditorLanguage(path)}
				options={{ automaticLayout: true, lineNumbers: "on", minimap: { enabled: false }, readOnly, scrollBeyondLastLine: false }}
				onChange={(value) => onChange(value || "")}
				onMount={handleMount}
				path={`dbt-draft://${path}`}
				value={content}
			/>
			{diagnostics.length ? <output className="dmx-dbt-code-editor__diagnostics">当前文件有 {diagnostics.length} 条诊断</output> : null}
		</div>
	);
}
