import { BarChartOutlined, PlusOutlined, SearchOutlined } from "@ant-design/icons";
import { Button, Empty, Input, Spin, Tag, Tooltip } from "antd";
import { useMemo, useState } from "react";
import type { CardListItem } from "../../api/analyticsApi";

export const DASHBOARD_ANALYSIS_DRAG_TYPE = "application/x-dts-analysis-card-id";

const DISPLAY_LABELS: Record<string, string> = {
	table: "表格",
	line: "折线图",
	bar: "柱状图",
	pie: "饼图",
	area: "面积图",
	scalar: "指标卡",
	row: "横向柱状图",
	combo: "组合图",
	funnel: "漏斗图",
	scatter: "散点图",
};

interface DashboardAnalysisLibraryProps {
	cards: CardListItem[];
	existingCardIds: Set<number>;
	disabled?: boolean;
	loading?: boolean;
	onAdd: (card: CardListItem) => void;
}

export function DashboardAnalysisLibrary({
	cards,
	existingCardIds,
	disabled = false,
	loading = false,
	onAdd,
}: DashboardAnalysisLibraryProps) {
	const [keyword, setKeyword] = useState("");
	const filteredCards = useMemo(() => {
		const normalized = keyword.trim().toLowerCase();
		return cards.filter(
			(card) =>
				!normalized ||
				card.name?.toLowerCase().includes(normalized) ||
				card.description?.toLowerCase().includes(normalized),
		);
	}, [cards, keyword]);

	return (
		<aside className="mb-dashboard-composer__panel mb-dashboard-analysis-library" aria-label="已发布分析">
			<div className="mb-dashboard-composer__panel-title">
				<div>
					<strong>已发布分析</strong>
					<div className="mb-dashboard-composer__panel-caption">拖入画布或点击添加</div>
				</div>
				<Tag color="blue">{cards.length}</Tag>
			</div>
			<Input
				allowClear
				prefix={<SearchOutlined />}
				placeholder="搜索分析"
				value={keyword}
				onChange={(event) => setKeyword(event.target.value)}
			/>
			<ul className="mb-dashboard-analysis-library__list">
				{loading ? (
					<li className="flex min-h-[180px] items-center justify-center">
						<Spin />
					</li>
				) : filteredCards.length === 0 ? (
					<li>
						<Empty
							image={Empty.PRESENTED_IMAGE_SIMPLE}
							description={cards.length === 0 ? "暂无已发布分析" : "未找到匹配分析"}
						/>
					</li>
				) : (
					filteredCards.map((card) => {
						const alreadyAdded = existingCardIds.has(card.id);
						const canAdd = !disabled && !alreadyAdded;
						return (
							<li
								key={card.id}
								className={`mb-dashboard-analysis-library__item${alreadyAdded ? " is-added" : ""}`}
								draggable={canAdd}
								onDragStart={(event) => {
									event.dataTransfer.effectAllowed = "copy";
									event.dataTransfer.setData(DASHBOARD_ANALYSIS_DRAG_TYPE, String(card.id));
									event.dataTransfer.setData("text/plain", String(card.id));
								}}
							>
								<div className="mb-dashboard-analysis-library__icon">
									<BarChartOutlined />
								</div>
								<div className="mb-dashboard-analysis-library__meta">
									<Tooltip title={card.name}>
										<strong>{card.name || `分析 ${card.id}`}</strong>
									</Tooltip>
									<span>
										{DISPLAY_LABELS[card.display || ""] || "分析"} · v{card.published_revision_id}
									</span>
								</div>
								<Button
									type="text"
									size="small"
									icon={<PlusOutlined />}
									disabled={!canAdd}
									onClick={() => onAdd(card)}
									aria-label={`添加${card.name || "分析"}`}
								/>
							</li>
						);
					})
				)}
			</ul>
		</aside>
	);
}
