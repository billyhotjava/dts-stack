import { useEffect, useMemo, useState } from "react";
import { DownloadOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Space } from "antd";
import { toast } from "sonner";
import { VisualFlowCanvas } from "@/components/visual-canvas/VisualFlowCanvas";
import { getCatalogLineageImpact } from "@/api/platformApi";
import {
	applyLayeredLayout,
	buildFlowElements,
	downloadBlob,
	edgeEndpoint,
	edgeLabel,
	EmptyAction,
	type ImpactNode,
	type ImpactResult,
	type LayoutDirection,
	LineageDataFilters,
	type LineageDirection,
	LineageNodeDrawer,
	LineageSectionNav,
	loadDatasetOptions,
	NODE_SIZE,
	nodeTone,
	relationStroke,
	toIsoInstant,
	useLineageData,
} from "./lineageShared";

const svgEscape = (value: unknown) =>
	String(value ?? "")
		.replaceAll("&", "&amp;")
		.replaceAll("<", "&lt;")
		.replaceAll(">", "&gt;")
		.replaceAll("\"", "&quot;");

export default function LineageGraphPage() {
	const [datasets, setDatasets] = useState<Array<{ id: string; name: string }>>([]);
	const [selectedId, setSelectedId] = useState<string>();
	const [direction, setDirection] = useState<LineageDirection>("BOTH");
	const [depth, setDepth] = useState(3);
	const [projectName, setProjectName] = useState("");
	const [layerFilters, setLayerFilters] = useState<string[]>([]);
	const [changedWithinHours, setChangedWithinHours] = useState(0);
	const [snapshotAt, setSnapshotAt] = useState("");
	const [keyword, setKeyword] = useState("");
	const [layoutDirection, setLayoutDirection] = useState<LayoutDirection>("LR");
	const [loading, setLoading] = useState(false);
	const [impact, setImpact] = useState<ImpactResult | null>(null);
	const [selectedNode, setSelectedNode] = useState<ImpactNode | null>(null);
	const datasetOptions = useMemo(() => datasets.map((item) => ({ label: item.name, value: item.id })), [datasets]);
	const { nodes, edges } = useLineageData(impact, keyword);
	const { rfNodes, rfEdges } = useMemo(() => buildFlowElements(nodes, edges, layoutDirection, selectedNode), [nodes, edges, layoutDirection, selectedNode]);

	const loadDatasets = async () => {
		try {
			const options = await loadDatasetOptions();
			setDatasets(options);
			if (!selectedId && options.length) setSelectedId(options[0].id);
		} catch {
			// global interceptor handles toast
		}
	};

	const loadImpact = async () => {
		if (!selectedId) {
			setImpact(null);
			return;
		}
		setLoading(true);
		try {
			const resp: any = await getCatalogLineageImpact(selectedId, {
				direction,
				depth,
				projectName: projectName.trim() || undefined,
				layers: layerFilters.length ? layerFilters.join(",") : undefined,
				changedWithinHours: changedWithinHours > 0 ? changedWithinHours : undefined,
				withJobs: true,
				withColumns: true,
				at: toIsoInstant(snapshotAt),
			});
			setImpact(resp || null);
		} catch {
			setImpact(null);
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void loadDatasets();
	}, []);

	useEffect(() => {
		void loadImpact();
	}, [selectedId, direction, depth, projectName, layerFilters, changedWithinHours, snapshotAt]);

	const buildLineageSvg = () => {
		if (!nodes.length) return null;
		const positions = applyLayeredLayout(nodes, edges, layoutDirection);
		const padding = 48;
		const positionedNodes = nodes
			.filter((node) => node.id)
			.map((node, index) => ({ node, position: positions.get(node.id!) ?? { x: index * 220, y: 0 } }));
		const maxX = Math.max(...positionedNodes.map((item) => item.position.x + NODE_SIZE.width), NODE_SIZE.width);
		const maxY = Math.max(...positionedNodes.map((item) => item.position.y + NODE_SIZE.height), NODE_SIZE.height);
		const width = maxX + padding * 2;
		const height = maxY + padding * 2;
		const positionById = new Map(positionedNodes.map((item) => [item.node.id, item.position]));
		const svgEdges = edges
			.map((edge) => {
				const from = edgeEndpoint(edge, "from");
				const to = edgeEndpoint(edge, "to");
				const source = from ? positionById.get(from) : undefined;
				const target = to ? positionById.get(to) : undefined;
				if (!source || !target) return "";
				const x1 = source.x + NODE_SIZE.width + padding;
				const y1 = source.y + NODE_SIZE.height / 2 + padding;
				const x2 = target.x + padding;
				const y2 = target.y + NODE_SIZE.height / 2 + padding;
				const dash = String(edge.verificationStatus || "").toUpperCase() === "KNOWN_UNVERIFIED" || edge.relationType === "MANUAL" ? " stroke-dasharray=\"5 5\"" : "";
				return `<path d="M ${x1} ${y1} L ${x2} ${y2}" fill="none" stroke="${relationStroke(edge.relationType)}" stroke-width="1.8"${dash} marker-end="url(#arrow)"/><text x="${(x1 + x2) / 2}" y="${(y1 + y2) / 2 - 6}" text-anchor="middle" font-size="10" fill="#595959">${svgEscape(edgeLabel(edge))}</text>`;
			})
			.join("\n");
		const svgNodes = positionedNodes
			.map(({ node, position }) => {
				const tone = nodeTone(node);
				const x = position.x + padding;
				const y = position.y + padding;
				const detail = node.kind === "job" ? node.jobType || node.relationType || "JOB" : node.layer || node.assetType || node.type || "DATASET";
				return `<rect x="${x}" y="${y}" width="${NODE_SIZE.width}" height="${NODE_SIZE.height}" rx="${node.kind === "job" ? 12 : 6}" fill="${tone.bg}" stroke="${tone.border}"/><text x="${x + 12}" y="${y + 22}" font-size="10" fill="#64748b">${svgEscape(String(node.kind || "dataset").toUpperCase())} · ${svgEscape(detail)}</text><text x="${x + 12}" y="${y + 44}" font-size="13" font-weight="600" fill="#0f172a">${svgEscape(node.name || node.table || "未知节点")}</text>`;
			})
			.join("\n");
		const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
<defs><marker id="arrow" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto" markerUnits="strokeWidth"><path d="M0,0 L0,6 L9,3 z" fill="#8c8c8c"/></marker></defs>
<rect width="100%" height="100%" fill="#ffffff"/>
${svgEdges}
${svgNodes}
</svg>`;
		return { svg, width, height };
	};

	const handleExportSvg = () => {
		const graph = buildLineageSvg();
		if (!graph) {
			toast.warning("当前无可导出的图");
			return;
		}
		downloadBlob(`lineage-graph-${new Date().toISOString().slice(0, 19).replaceAll(":", "-")}.svg`, graph.svg, "image/svg+xml;charset=utf-8;");
		toast.success("已导出血缘图 SVG");
	};

	const handleExportPng = () => {
		const graph = buildLineageSvg();
		if (!graph) {
			toast.warning("当前无可导出的图");
			return;
		}
		const image = new Image();
		const url = URL.createObjectURL(new Blob([graph.svg], { type: "image/svg+xml;charset=utf-8" }));
		image.onload = () => {
			const canvas = document.createElement("canvas");
			const scale = Math.max(1, window.devicePixelRatio || 1);
			canvas.width = Math.ceil(graph.width * scale);
			canvas.height = Math.ceil(graph.height * scale);
			const ctx = canvas.getContext("2d");
			if (!ctx) {
				URL.revokeObjectURL(url);
				toast.error("PNG 导出失败：浏览器不支持 Canvas");
				return;
			}
			ctx.fillStyle = "#ffffff";
			ctx.fillRect(0, 0, canvas.width, canvas.height);
			ctx.scale(scale, scale);
			ctx.drawImage(image, 0, 0);
			canvas.toBlob((blob) => {
				URL.revokeObjectURL(url);
				if (!blob) {
					toast.error("PNG 导出失败");
					return;
				}
				const link = document.createElement("a");
				link.href = URL.createObjectURL(blob);
				link.download = `lineage-graph-${new Date().toISOString().slice(0, 19).replaceAll(":", "-")}.png`;
				link.click();
				URL.revokeObjectURL(link.href);
				toast.success("已导出血缘图 PNG");
			}, "image/png");
		};
		image.onerror = () => {
			URL.revokeObjectURL(url);
			toast.error("PNG 导出失败");
		};
		image.src = url;
	};

	return (
		<div className="space-y-4">
			<Card title="血缘与影响分析 / 血缘图谱" extra={<Space><Button icon={<DownloadOutlined />} onClick={handleExportSvg} disabled={!nodes.length}>导出SVG</Button><Button icon={<DownloadOutlined />} onClick={handleExportPng} disabled={!nodes.length}>导出PNG</Button></Space>}>
				<div className="mb-3"><LineageSectionNav section="graph" /></div>
				<LineageDataFilters
					datasetOptions={datasetOptions}
					selectedId={selectedId}
					onSelectedIdChange={setSelectedId}
					direction={direction}
					onDirectionChange={setDirection}
					depth={depth}
					onDepthChange={setDepth}
					projectName={projectName}
					onProjectNameChange={setProjectName}
					layerFilters={layerFilters}
					onLayerFiltersChange={setLayerFilters}
					changedWithinHours={changedWithinHours}
					onChangedWithinHoursChange={setChangedWithinHours}
					keyword={keyword}
					onKeywordChange={setKeyword}
					snapshotAt={snapshotAt}
					onSnapshotAtChange={setSnapshotAt}
					layoutDirection={layoutDirection}
					onLayoutDirectionChange={setLayoutDirection}
					showLayout
				/>
			</Card>
			{!selectedId ? <Alert type="info" message="请选择一个数据集查看血缘图谱。" showIcon action={<EmptyAction onReload={loadDatasets} />} /> : null}
			{selectedId ? (
				<Card loading={loading} bodyStyle={{ padding: 0 }}>
					<VisualFlowCanvas
						nodes={rfNodes}
						edges={rfEdges}
						height={540}
						showMiniMap
						emptyText="暂无血缘节点"
						onNodeClick={(node) => {
							const matched = nodes.find((item) => (item.id || "") === node.id);
							if (matched) setSelectedNode(matched);
						}}
						nodeColor={(node) => {
							const matched = nodes.find((item) => (item.id || "") === node.id);
							return nodeTone(matched || {}).bg;
						}}
					/>
				</Card>
			) : null}
			<LineageNodeDrawer selectedNode={selectedNode} onClose={() => setSelectedNode(null)} />
		</div>
	);
}
