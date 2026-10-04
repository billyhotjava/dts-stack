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

	it("uses dts-metrics Sprint-35 APIs instead of platform semantic APIs", () => {
		const semanticApi = assertFile("src/features/semantic/semanticApi.ts");
		const semanticTypes = assertFile("src/features/semantic/semanticTypes.ts");
		const designerPage = assertFile("src/pages/semantic/SemanticDesignerPage.tsx");

		assert.match(semanticApi, /\/api\/metrics\/visual-assets/);
		assert.match(semanticApi, /\/api\/metrics\/graphs\/draft\/preflight/);
		assert.match(semanticApi, /\/api\/metrics\/graphs"/);
		assert.match(designerPage, /getVisualAssets\(\{ layers: "DWD", includeDrilldown: true \}\)/);
		assert.match(designerPage, /DWD 高级建模/);
		assert.match(designerPage, /standardCode: dwdStandardCode/);
		assert.match(designerPage, /saveDwdCandidate/);
		assert.match(designerPage, /aria-label="派生指标 DSL 操作"/);
		assert.match(designerPage, /count_if/);
		assert.match(designerPage, /sum_if/);
		assert.match(designerPage, /case_when/);
		assert.match(designerPage, /buildDerivedExpression\(item\)/);
		assert.match(designerPage, /readOnly value=\{buildDerivedExpression\(item\)\}/);
		assert.match(assertFile("src/app/MetricsShell.tsx"), /\/api\/metrics\/models\/\$\{encodeURIComponent\(id\)\}\/\$\{action\}/);
		assert.match(assertFile("src/app/MetricsShell.tsx"), /\/api\/metrics\/models\/\$\{encodeURIComponent\(id\)\}\/versions/);
		assert.match(semanticTypes, /export type MetricLifecycleStatus/);
		assert.match(semanticTypes, /export type MetricContractErrorCode/);
		assert.match(semanticTypes, /export type DerivedMetricOperation/);
		assert.match(semanticTypes, /"graph_validation_failed"/);
		assert.match(semanticTypes, /"standard_code_required"/);
		assert.match(semanticTypes, /"ROLLBACK_TARGET_REQUIRED"|rollback_target_required/);
		assert.doesNotMatch(semanticApi, /\/bi\/api\/semantic/);
		assert.doesNotMatch(semanticApi, /\/api\/metrics\/semantic/);
		assert.doesNotMatch(designerPage, /localSqlPreview/);
		assert.doesNotMatch(designerPage, /RLS placeholder/);
		assert.doesNotMatch(designerPage, /expression:\s*event\.target\.value/);
	});

	it("renders the semantic relation canvas with React Flow instead of the legacy SVG canvas", () => {
		const packageJson = JSON.parse(assertFile("package.json"));
		const canvasComponent = assertFile("src/features/semantic/SemanticModelCanvas.tsx");
		const canvasHelpers = assertFile("src/features/semantic/semanticCanvas.helpers.ts");

		assert.equal(packageJson.dependencies["@xyflow/react"], "^12.10.2");
		assert.match(canvasComponent, /from "@xyflow\/react"/);
		assert.match(canvasComponent, /<ReactFlow/);
		assert.match(canvasComponent, /<Background/);
		assert.match(canvasComponent, /<Controls/);
		assert.match(canvasComponent, /semantic-layer-badge/);
		assert.match(canvasComponent, /semantic-node-status-grid/);
		assert.match(canvasHelpers, /warehouseLayer/);
		assert.match(canvasHelpers, /governanceStatus/);
		assert.match(canvasHelpers, /permissionDecision/);
		assert.match(canvasHelpers, /lineageStatus/);
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
		assert.match(explorerComponent, /standardCodeField/);
		assert.match(explorerComponent, /labelField/);
		assert.match(canvasComponent, /onDropField/);
		assert.match(canvasComponent, /onDrop=/);
		assert.match(canvasComponent, /field:metric:/);
		assert.match(canvasComponent, /field:dimension:/);
		assert.match(designerPage, /onDropField=\{handleDropField\}/);
		assert.match(designerPage, /selectedMeasures=\{selectedMeasures\}/);
		assert.match(designerPage, /selectedDimensions=\{selectedDimensions\}/);
	});

	it("exposes standard-code metadata in the field explorer instead of relying on labels", () => {
		const explorerHelpers = assertFile("src/features/semantic/semanticFieldExplorer.helpers.ts");
		const explorerComponent = assertFile("src/features/semantic/SemanticFieldExplorer.tsx");

		assert.match(explorerHelpers, /inferStandardCodeField/);
		assert.match(explorerHelpers, /standard_code_field/);
		assert.match(explorerHelpers, /_name/);
		assert.match(explorerComponent, /node\.standardCodeField/);
		assert.match(explorerComponent, /label: \{node\.labelField\}/);
	});

	it("keeps the metrics menu integrated into the approved workbench and semantic modeling groups", () => {
		const shellSource = assertFile("src/app/MetricsShell.tsx");
		const routeLines = shellSource.match(/title: "[^"]+"/g)?.map((line) => line.replace(/^title: "|",?$/g, "")) ?? [];
		const groupLines = shellSource.match(/group: "[^"]+"/g)?.map((line) => line.replace(/^group: "|",?$/g, "")) ?? [];

		assert.deepEqual(groupLines, ["Sprint-35 Features"]);
		assert.deepEqual(routeLines, [
			"整体架构与 PRD 契约",
			"前后端 API 契约",
			"前端可视化工作台",
			"后端建模与 dbt 网关",
			"安全、评审机制与 IT 准入",
		]);
	});

	it("does not ship local fake metrics data in the React app", () => {
		const shellSource = assertFile("src/app/MetricsShell.tsx");
		const designerPage = assertFile("src/pages/semantic/SemanticDesignerPage.tsx");
		const forbidden = [
			/sampleManifest/,
			/defaultWorkspace/,
			/designerFieldPool/,
			/benchmarkCapabilityRows/,
			/project-management-core/,
			/flower-rental/,
			/local-fallback/,
			/fallbackMeta/,
			/workspace\/snapshot/,
		];

		for (const pattern of forbidden) {
			assert.doesNotMatch(shellSource, pattern);
			assert.doesNotMatch(designerPage, pattern);
		}
	});
});
