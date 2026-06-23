import { FolderOutlined, PlusOutlined, CodeOutlined } from "@ant-design/icons";
import { Button, Empty, Segmented, Tag } from "antd";
import { useEffect, useState } from "react";
import { CanvasListView } from "@/canvas/CanvasListView";
import { DbtPreviewDrawer } from "@/canvas/DbtPreviewDrawer";
import { EltCanvas } from "@/canvas/EltCanvas";
import { NodeConfigDrawer } from "@/canvas/NodeConfigDrawer";
import { RunDock } from "@/canvas/RunDock";
import { useTransformGraphStore } from "@/canvas/transformGraphStore";
import { unwrap } from "@/mock/client";
import { transformService } from "@/mock/services/transformService";
import { useDepartmentStore } from "@/store/departmentStore";
import { useProjectSpaceStore } from "@/store/projectSpaceStore";
import { SectionTitle, Surface } from "@/ui/components";

/**
 * 阶段② 集成 · 可视化 ELT 工作台。
 * 部门为主：项目空间是 dev 辅助分组；画布/配置/运行/dbt 按所选空间。
 * 资产产出归口部门；dbt 在底层自动生成（只读查看）。
 */
export function IntegrateStage() {
	const dept = useDepartmentStore((s) => s.departments.find((d) => d.id === s.currentDepartmentId) ?? null);
	const spaces = useProjectSpaceStore((s) => s.spaces);
	const currentSpaceId = useProjectSpaceStore((s) => s.currentSpaceId);
	const setCurrent = useProjectSpaceStore((s) => s.setCurrent);
	const loadGraph = useTransformGraphStore((s) => s.load);

	const [view, setView] = useState<"canvas" | "list">("canvas");
	const [dbtOpen, setDbtOpen] = useState(false);

	// 切项目空间 → 载入该空间的转换图（画布与列表共用同一份 store）
	useEffect(() => {
		if (currentSpaceId) void transformService.getGraph(currentSpaceId).then((r) => loadGraph(unwrap(r)));
	}, [currentSpaceId, loadGraph]);

	return (
		<div style={{ maxWidth: 1180, margin: "0 auto" }}>
			<SectionTitle
				kicker="阶段 ②"
				title="集成 · 可视化 ELT"
				desc={`拖拽节点搭建转换，dbt 在底层自动生成。${dept ? `当前部门：${dept.name}` : ""}`}
				extra={<Tag color="blue">S4 配置/运行</Tag>}
			/>

			{/* 项目空间 = dev 辅助分组 */}
			<Surface pad="md" style={{ marginBottom: 16 }}>
				<div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 12 }}>
					<FolderOutlined style={{ color: "var(--accent)" }} />
					<span style={{ fontWeight: 650 }}>项目空间</span>
					<span style={{ fontSize: "var(--text-xs)", color: "var(--ink-subtle)" }}>本部门内组织 ELT/建模（资产产出归口部门）</span>
					<Button size="small" icon={<PlusOutlined />} style={{ marginLeft: "auto" }}>
						新建空间
					</Button>
				</div>
				<div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
					{spaces.map((sp) => {
						const active = sp.id === currentSpaceId;
						return (
							<button
								key={sp.id}
								type="button"
								onClick={() => setCurrent(sp.id)}
								style={{
									display: "flex",
									flexDirection: "column",
									gap: 2,
									padding: "8px 14px",
									minWidth: 150,
									textAlign: "left",
									cursor: "pointer",
									borderRadius: "var(--radius-md)",
									border: `1px solid ${active ? "var(--accent)" : "var(--hairline)"}`,
									background: active ? "var(--accent-soft)" : "var(--surface)",
									color: active ? "var(--accent-active)" : "var(--ink)",
								}}
							>
								<span style={{ fontWeight: 600 }}>{sp.name}</span>
								<span className="tnum" style={{ fontSize: "var(--text-xs)", color: "var(--ink-subtle)" }}>
									{sp.modelCount ?? 0} 个模型 · {sp.owner}
								</span>
							</button>
						);
					})}
					{spaces.length === 0 ? <span style={{ color: "var(--ink-subtle)", fontSize: "var(--text-sm)" }}>本部门暂无项目空间</span> : null}
				</div>
			</Surface>

			{currentSpaceId ? (
				<>
					{/* 工作台工具条：视图切换 + 查看 dbt */}
					<div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 12 }}>
						<Segmented
							value={view}
							onChange={(v) => setView(v as "canvas" | "list")}
							options={[
								{ label: "画布", value: "canvas" },
								{ label: "列表", value: "list" },
							]}
						/>
						<Button icon={<CodeOutlined />} onClick={() => setDbtOpen(true)}>
							查看生成的 dbt
						</Button>
					</div>

					{view === "canvas" ? <EltCanvas /> : <Surface pad="md"><CanvasListView /></Surface>}

					<RunDock />
					<NodeConfigDrawer />
					<DbtPreviewDrawer open={dbtOpen} onClose={() => setDbtOpen(false)} />
				</>
			) : (
				<Surface pad="lg">
					<Empty description="请选择一个项目空间以打开画布" />
				</Surface>
			)}
		</div>
	);
}
