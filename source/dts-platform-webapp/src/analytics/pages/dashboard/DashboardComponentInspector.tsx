import { DeleteOutlined, EditOutlined, SwapOutlined } from "@ant-design/icons";
import { Button, Divider, Empty, InputNumber, Space, Tag } from "antd";
import type { DashboardCard } from "../../api/analyticsApi";
import { isPublishedAnalysisCard } from "./dashboardEditorModel";
import { resolveRouteForOpen } from "../../helpers/resolveAnalyticsUrl";

interface DashboardComponentInspectorProps {
	dashcard: DashboardCard | null;
	disabled?: boolean;
	onLayoutChange: (patch: Partial<Pick<DashboardCard, "row" | "col" | "size_x" | "size_y">>) => void;
	onReplace: () => void;
	onDelete: () => void;
}

const layoutFields: Array<{
	key: "col" | "row" | "size_x" | "size_y";
	label: string;
	min: number;
	max: number;
}> = [
	{ key: "col", label: "X", min: 0, max: 11 },
	{ key: "row", label: "Y", min: 0, max: 999 },
	{ key: "size_x", label: "宽", min: 3, max: 12 },
	{ key: "size_y", label: "高", min: 2, max: 20 },
];

export function DashboardComponentInspector({
	dashcard,
	disabled = false,
	onLayoutChange,
	onReplace,
	onDelete,
}: DashboardComponentInspectorProps) {
	return (
		<aside className="mb-dashboard-composer__panel mb-dashboard-component-inspector" aria-label="组件属性">
			<div className="mb-dashboard-composer__panel-title">
				<div>
					<strong>组件属性</strong>
					<div className="mb-dashboard-composer__panel-caption">位置、尺寸与分析入口</div>
				</div>
			</div>
			{!dashcard ? (
				<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="选择画布中的组件" />
			) : (
				<>
					<div className="mb-dashboard-component-inspector__name">
						<strong>{dashcard.card?.name || `分析 ${dashcard.card_id ?? "-"}`}</strong>
						<Tag color={isPublishedAnalysisCard(dashcard.card) ? "green" : "error"}>
							{isPublishedAnalysisCard(dashcard.card) ? "已发布分析" : "需替换"}
						</Tag>
					</div>
					<div className="mb-dashboard-component-inspector__layout">
						{layoutFields.map((field) => (
							<div key={field.key} className="mb-dashboard-component-inspector__layout-field">
								<span>{field.label}</span>
								<InputNumber
									aria-label={`组件${field.label}`}
									value={dashcard[field.key] ?? (field.key === "size_x" ? 6 : field.key === "size_y" ? 4 : 0)}
									min={field.min}
									max={field.max}
									disabled={disabled}
									onChange={(value) => {
										if (typeof value === "number") onLayoutChange({ [field.key]: value });
									}}
								/>
							</div>
						))}
					</div>
					<Divider />
					<Space direction="vertical" style={{ width: "100%" }}>
						<Button block icon={<SwapOutlined />} disabled={disabled} onClick={onReplace}>
							替换分析
						</Button>
						<Button
							block
							icon={<EditOutlined />}
							href={
								dashcard.card_id
									? resolveRouteForOpen(`/bi/questions/${encodeURIComponent(String(dashcard.card_id))}/edit`)
									: undefined
							}
							target="_blank"
							disabled={!dashcard.card_id}
						>
							打开分析
						</Button>
						<Button block danger icon={<DeleteOutlined />} disabled={disabled} onClick={onDelete}>
							删除组件
						</Button>
					</Space>
				</>
			)}
		</aside>
	);
}
