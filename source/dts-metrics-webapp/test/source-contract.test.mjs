import assert from "node:assert/strict";
import { readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, it } from "node:test";

const root = new URL("..", import.meta.url).pathname;

function read(path) {
	return readFileSync(join(root, path), "utf8");
}

function assertFile(path) {
	const fullPath = join(root, path);
	assert.equal(statSync(fullPath).isFile(), true, `${path} should exist`);
	return read(path);
}

describe("metrics webapp source migration contract", () => {
	it("keeps the shell thin instead of storing migrated semantic UI in App.tsx", () => {
		const appSource = assertFile("src/App.tsx");
		const appLines = appSource.split(/\r?\n/).length;

		assert.match(appSource, /from "\.\/app\/MetricsShell"/);
		assert.ok(appLines <= 80, `App.tsx should stay a thin entry component, got ${appLines} lines`);
	});

	it("contains the migrated platform semantic canvas and field explorer modules", () => {
		const canvasSource = assertFile("src/features/semantic/semanticCanvas.helpers.ts");
		const explorerSource = assertFile("src/features/semantic/semanticFieldExplorer.helpers.ts");
		const canvasComponent = assertFile("src/features/semantic/SemanticModelCanvas.tsx");
		const explorerComponent = assertFile("src/features/semantic/SemanticFieldExplorer.tsx");
		const designerPage = assertFile("src/pages/semantic/SemanticDesignerPage.tsx");

		assert.match(canvasSource, /buildSemanticCanvasGraph/);
		assert.match(explorerSource, /buildSemanticFieldExplorerTree/);
		assert.match(canvasComponent, /function SemanticModelCanvas/);
		assert.match(explorerComponent, /function SemanticFieldExplorer/);
		assert.match(designerPage, /<SemanticModelCanvas/);
		assert.match(designerPage, /<SemanticFieldExplorer/);
	});

	it("uses the platform semantic APIs from the migrated React architecture", () => {
		const semanticApi = assertFile("src/features/semantic/semanticApi.ts");

		assert.match(semanticApi, /\/bi\/api\/semantic\/meta/);
		assert.match(semanticApi, /\/bi\/api\/semantic\/query\/preview-sql/);
		assert.match(semanticApi, /\/api\/semantic\/metrics/);
	});

	it("renders the semantic relation canvas with React Flow instead of the legacy SVG canvas", () => {
		const packageJson = JSON.parse(assertFile("package.json"));
		const canvasComponent = assertFile("src/features/semantic/SemanticModelCanvas.tsx");

		assert.equal(packageJson.dependencies["@xyflow/react"], "^12.10.2");
		assert.match(canvasComponent, /from "@xyflow\/react"/);
		assert.match(canvasComponent, /<ReactFlow/);
		assert.match(canvasComponent, /<Background/);
		assert.match(canvasComponent, /<Controls/);
		assert.match(canvasComponent, /@xyflow\/react\/dist\/style\.css/);
		assert.doesNotMatch(canvasComponent, /<svg/);
		assert.doesNotMatch(canvasComponent, /semantic-canvas-edges/);
	});

	it("lets the migrated field tree drag metrics and dimensions into the React Flow canvas", () => {
		const explorerComponent = assertFile("src/features/semantic/SemanticFieldExplorer.tsx");
		const canvasComponent = assertFile("src/features/semantic/SemanticModelCanvas.tsx");
		const designerPage = assertFile("src/pages/semantic/SemanticDesignerPage.tsx");

		assert.match(explorerComponent, /application\/vnd\.dts-metrics-field/);
		assert.match(explorerComponent, /onDragStart/);
		assert.match(explorerComponent, /draggable=/);
		assert.match(canvasComponent, /onDropField/);
		assert.match(canvasComponent, /onDrop=/);
		assert.match(canvasComponent, /field:metric:/);
		assert.match(canvasComponent, /field:dimension:/);
		assert.match(designerPage, /onDropField=\{handleDropField\}/);
		assert.match(designerPage, /selectedMeasures=\{selectedMeasures\}/);
		assert.match(designerPage, /selectedDimensions=\{selectedDimensions\}/);
	});
});
