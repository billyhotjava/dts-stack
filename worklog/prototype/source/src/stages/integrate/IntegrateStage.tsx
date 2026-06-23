import { FolderOutlined, PlusOutlined, ToolOutlined } from "@ant-design/icons";
import { Button, Tag } from "antd";
import { useDepartmentStore } from "@/store/departmentStore";
import { useProjectSpaceStore } from "@/store/projectSpaceStore";
import { SectionTitle, Surface } from "@/ui/components";
import { AREA_CONTENT } from "../contents";

/**
 * 阶段② 集成 —— ELT/建模工作。
 * 项目空间是本阶段的"dev 辅助分组"：在部门内把转换/模型按空间组织。
 * 资产产出最终归口部门（非项目空间）。完整画布在 S3–S4。
 */
export function IntegrateStage() {
	const dept = useDepartmentStore((s) => s.departments.find((d) => d.id === s.currentDepartmentId) ?? null);
	const spaces = useProjectSpaceStore((s) => s.spaces);
	const currentSpaceId = useProjectSpaceStore((s) => s.currentSpaceId);
	const setCurrent = useProjectSpaceStore((s) => s.setCurrent);
	const area = AREA_CONTENT.integrate;

	return (
		<div style={{ maxWidth: 1080, margin: "0 auto" }}>
			<SectionTitle
				kicker="阶段 ②"
				title="集成 · 可视化 ELT"
				desc={`在画布上拖拽搭建转换，dbt 隐藏在底层自动生成。${dept ? `当前部门：${dept.name}` : ""}`}
				extra={<Tag color="blue">{area.sprint} 交付</Tag>}
			/>

			{/* 项目空间 = dev 辅助分组 */}
			<Surface pad="md" style={{ marginBottom: 16 }}>
				<div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 12 }}>
					<FolderOutlined style={{ color: "var(--accent)" }} />
					<span style={{ fontWeight: 650 }}>项目空间</span>
					<span style={{ fontSize: "var(--text-xs)", color: "var(--ink-subtle)" }}>
						本部门内组织 ELT/建模 的 dev 分组（资产产出仍归口部门）
					</span>
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
									padding: "10px 16px",
									minWidth: 160,
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

			{/* 画布能力占位（S3–S4） */}
			<Surface pad="lg">
				<div style={{ display: "flex", alignItems: "center", gap: 8, color: "var(--ink-muted)", marginBottom: 16 }}>
					<ToolOutlined />
					<span style={{ fontSize: "var(--text-sm)" }}>可视化 ELT 画布将在 {area.sprint} 实现，收编：</span>
				</div>
				<div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))", gap: 10 }}>
					{area.pages.map((page) => (
						<div
							key={page}
							style={{
								display: "flex",
								alignItems: "center",
								gap: 10,
								padding: "10px 14px",
								border: "1px solid var(--hairline)",
								borderRadius: "var(--radius-md)",
								background: "var(--surface-sunken)",
								fontSize: "var(--text-sm)",
							}}
						>
							<span style={{ width: 6, height: 6, borderRadius: "50%", background: "var(--accent)", flex: "0 0 auto" }} />
							{page}
						</div>
					))}
				</div>
			</Surface>
		</div>
	);
}
