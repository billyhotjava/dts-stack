import { FolderOutlined, PlusOutlined } from "@ant-design/icons";
import { Button, Empty, Tag } from "antd";
import { EltCanvas } from "@/canvas/EltCanvas";
import { useDepartmentStore } from "@/store/departmentStore";
import { useProjectSpaceStore } from "@/store/projectSpaceStore";
import { SectionTitle, Surface } from "@/ui/components";

/**
 * 阶段② 集成 · 可视化 ELT。
 * 部门为主：项目空间是本阶段的 dev 辅助分组；画布按所选项目空间加载。
 * 资产产出归口部门。dbt 隐藏在底层自动生成（S4 展示只读抽屉）。
 */
export function IntegrateStage() {
	const dept = useDepartmentStore((s) => s.departments.find((d) => d.id === s.currentDepartmentId) ?? null);
	const spaces = useProjectSpaceStore((s) => s.spaces);
	const currentSpaceId = useProjectSpaceStore((s) => s.currentSpaceId);
	const setCurrent = useProjectSpaceStore((s) => s.setCurrent);

	return (
		<div style={{ maxWidth: 1180, margin: "0 auto" }}>
			<SectionTitle
				kicker="阶段 ②"
				title="集成 · 可视化 ELT"
				desc={`拖拽节点搭建转换，dbt 在底层自动生成。${dept ? `当前部门：${dept.name}` : ""}`}
				extra={<Tag color="blue">画布内核 · S3</Tag>}
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
					{spaces.length === 0 ? (
						<span style={{ color: "var(--ink-subtle)", fontSize: "var(--text-sm)" }}>本部门暂无项目空间</span>
					) : null}
				</div>
			</Surface>

			{/* 可视化 ELT 画布 */}
			{currentSpaceId ? (
				<EltCanvas projectSpaceId={currentSpaceId} />
			) : (
				<Surface pad="lg">
					<Empty description="请选择一个项目空间以打开画布" />
				</Surface>
			)}
		</div>
	);
}
