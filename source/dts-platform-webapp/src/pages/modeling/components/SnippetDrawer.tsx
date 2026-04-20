import { Button, Card, Drawer, Input, Tabs, Tag } from "antd";
import { EmptyState } from "@/components/empty-state";
import type { DbtSourceItem, DbtRefItem } from "../sqlModeling.types";

const layerTag = (layer?: string) => {
	if (!layer) return <Tag>未分层</Tag>;
	const color =
		layer === "ODS" ? "blue"
		: layer === "STG" ? "gold"
		: layer === "DWD" ? "cyan"
		: layer === "DWS" ? "purple"
		: layer === "ADS" ? "geekblue"
		: "default";
	return <Tag color={color}>{layer}</Tag>;
};

export type SnippetDrawerProps = {
	open: boolean;
	onClose: () => void;
	snippetTab: "source" | "ref";
	onSnippetTabChange: (tab: "source" | "ref") => void;
	snippetKeyword: string;
	onSnippetKeywordChange: (keyword: string) => void;
	sourcesLoading: boolean;
	refsLoading: boolean;
	filteredSources: DbtSourceItem[];
	filteredRefs: DbtRefItem[];
	onInsertSnippet: (snippet: string) => void;
};

export default function SnippetDrawer({
	open,
	onClose,
	snippetTab,
	onSnippetTabChange,
	snippetKeyword,
	onSnippetKeywordChange,
	sourcesLoading,
	refsLoading,
	filteredSources,
	filteredRefs,
	onInsertSnippet,
}: SnippetDrawerProps) {
	return (
		<Drawer
			open={open}
			title={snippetTab === "source" ? "插入源表 (ODS)" : "插入模型引用"}
			width={560}
			onClose={onClose}
		>
			<div className="mb-4">
				<Input
					placeholder={snippetTab === "source" ? "搜索源表..." : "搜索模型..."}
					value={snippetKeyword}
					onChange={(e) => onSnippetKeywordChange(e.target.value)}
				/>
			</div>
			<Tabs
				activeKey={snippetTab}
				onChange={(key) => onSnippetTabChange(key as "source" | "ref")}
				items={[
					{
						key: "source",
						label: "ODS 源表",
						children: sourcesLoading ? (
							<div className="text-center text-sm text-muted-foreground py-8">加载中...</div>
						) : filteredSources.length === 0 ? (
							<EmptyState title="暂无源表" description="请先在数据集成中配置 ODS 表映射。" compact />
						) : (
							<div className="max-h-[400px] overflow-y-auto space-y-2">
								{filteredSources.map((item, idx) => (
									<Card
										key={`${item.schema}-${item.table}-${idx}`}
										size="small"
										className="cursor-pointer hover:border-primary transition-colors"
										onClick={() => onInsertSnippet(item.sourceSnippet || `{{ source('${item.schema}', '${item.table}') }}`)}
									>
										<div className="flex items-center justify-between">
											<div>
												<div className="font-medium text-foreground">
													{item.table}
												</div>
												<div className="text-xs text-muted-foreground">
													Schema: {item.schema} {item.systemCode ? `· 系统: ${item.systemCode}` : ""}
												</div>
												{item.description && (
													<div className="text-xs text-muted-foreground mt-1">{item.description}</div>
												)}
											</div>
											<Button size="small" type="link">
												插入
											</Button>
										</div>
										<div className="mt-2 rounded bg-muted px-2 py-1 font-mono text-xs text-muted-foreground">
											{item.sourceSnippet || `{{ source('${item.schema}', '${item.table}') }}`}
										</div>
									</Card>
								))}
							</div>
						),
					},
					{
						key: "ref",
						label: "模型引用",
						children: refsLoading ? (
							<div className="text-center text-sm text-muted-foreground py-8">加载中...</div>
						) : filteredRefs.length === 0 ? (
							<EmptyState title="暂无模型" description="请先创建 SQL 模型。" compact />
						) : (
							<div className="max-h-[400px] overflow-y-auto space-y-2">
								{filteredRefs.map((item, idx) => (
									<Card
										key={`${item.id || item.name}-${idx}`}
										size="small"
										className="cursor-pointer hover:border-primary transition-colors"
										onClick={() => onInsertSnippet(item.refSnippet || `{{ ref('${item.name}') }}`)}
									>
										<div className="flex items-center justify-between">
											<div>
												<div className="flex items-center gap-2">
													<span className="font-medium text-foreground">{item.name}</span>
													{layerTag(item.layer)}
												</div>
												<div className="text-xs text-muted-foreground">
													{item.sourceSystem ? `来源: ${item.sourceSystem}` : ""}
													{item.tags ? ` · 标签: ${item.tags}` : ""}
												</div>
												{item.description && (
													<div className="text-xs text-muted-foreground mt-1">{item.description}</div>
												)}
											</div>
											<Button size="small" type="link">
												插入
											</Button>
										</div>
										<div className="mt-2 rounded bg-muted px-2 py-1 font-mono text-xs text-muted-foreground">
											{item.refSnippet || `{{ ref('${item.name}') }}`}
										</div>
									</Card>
								))}
							</div>
						),
					},
				]}
			/>
		</Drawer>
	);
}
