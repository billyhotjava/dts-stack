import { ToolOutlined } from "@ant-design/icons";
import { Tag } from "antd";
import { SectionTitle, Surface } from "@/ui/components";
import { AREA_CONTENT } from "./contents";

/** 信息性占位页：展示该区域将收编的页面与交付 sprint，体现新 IA 映射。 */
export function StagePlaceholder({ areaKey }: { areaKey: string }) {
	const area = AREA_CONTENT[areaKey];
	if (!area) return null;

	return (
		<div style={{ maxWidth: 1080, margin: "0 auto" }}>
			<SectionTitle
				kicker={area.kicker}
				title={area.title}
				desc={area.intro}
				extra={<Tag color="blue">{area.sprint} 交付</Tag>}
			/>
			<Surface pad="lg">
				<div style={{ display: "flex", alignItems: "center", gap: 8, color: "var(--ink-muted)", marginBottom: 16 }}>
					<ToolOutlined />
					<span style={{ fontSize: "var(--text-sm)" }}>本区域将收编以下能力（{area.sprint} 实现）：</span>
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
