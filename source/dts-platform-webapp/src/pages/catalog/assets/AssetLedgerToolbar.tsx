import { DownOutlined, FilterOutlined, SearchOutlined, TableOutlined } from "@ant-design/icons";
import { Badge, Button, Card, Dropdown, Input, type MenuProps, Select, Space, Tag } from "antd";
import { AssetTagFilter } from "@/components/catalog/tags/AssetTagFilter";
import { CLASSIFICATION_OPTIONS, GOVERNANCE_OPTIONS, MATCH_OPTIONS, TYPE_OPTIONS } from "./assetPageShared";

type AssetLedgerToolbarProps = {
	activeFilterCount: number;
	assetType: string;
	classification: string;
	filtersOpen: boolean;
	governanceStatus: string;
	keyword: string;
	loading: boolean;
	matchStatus: string;
	onAssetTypeChange: (value: string) => void;
	onClassificationChange: (value: string) => void;
	onGovernanceStatusChange: (value: string) => void;
	onKeywordChange: (value: string) => void;
	onMatchStatusChange: (value: string) => void;
	onOpsMenuClick: (key: string) => void;
	onRefresh: () => void;
	onReset: () => void;
	onReturnToMap: () => void;
	onTagIdsChange: (tagIds: string[]) => void;
	onToggleFilters: () => void;
	opsMenuItems: MenuProps["items"];
	pageSubtitle: string;
	pageTitle: string;
	selectedTagIds: string[];
	tagFilterEnabled: boolean;
};

export function AssetLedgerToolbar({
	activeFilterCount,
	assetType,
	classification,
	filtersOpen,
	governanceStatus,
	keyword,
	loading,
	matchStatus,
	onAssetTypeChange,
	onClassificationChange,
	onGovernanceStatusChange,
	onKeywordChange,
	onMatchStatusChange,
	onOpsMenuClick,
	onRefresh,
	onReset,
	onReturnToMap,
	onTagIdsChange,
	onToggleFilters,
	opsMenuItems,
	pageSubtitle,
	pageTitle,
	selectedTagIds,
	tagFilterEnabled,
}: AssetLedgerToolbarProps) {
	return (
		<Card
			className="asset-ledger-toolbar"
			title={
				<div className="flex items-start gap-2">
					<TableOutlined className="mt-1" />
					<div>
						<div className="flex flex-wrap items-center gap-2">
							<span>{pageTitle}</span>
							<Tag color="geekblue">登记核验</Tag>
						</div>
						<div className="mt-1 text-xs font-normal text-slate-500">{pageSubtitle}</div>
					</div>
				</div>
			}
			extra={
				<Space wrap>
					<Button onClick={onReturnToMap}>返回地图</Button>
					<Button onClick={onRefresh} loading={loading}>
						刷新
					</Button>
					<Dropdown
						menu={{
							items: opsMenuItems,
							onClick: ({ key }) => onOpsMenuClick(String(key)),
						}}
						trigger={["click"]}
					>
						<Button data-testid="asset-ops-menu">
							同步与诊断 <DownOutlined />
						</Button>
					</Dropdown>
				</Space>
			}
		>
			<div className="flex flex-wrap items-center gap-2">
				<Input
					prefix={<SearchOutlined />}
					placeholder="搜索资产名称 / 描述"
					style={{ width: 280 }}
					value={keyword}
					onChange={(event) => onKeywordChange(event.target.value)}
					allowClear
				/>
				<Badge count={activeFilterCount} size="small">
					<Button icon={<FilterOutlined />} onClick={onToggleFilters} data-testid="asset-filters-toggle">
						{filtersOpen ? "收起筛选" : "筛选"}
					</Button>
				</Badge>
				{activeFilterCount > 0 || keyword.trim() ? <Button onClick={onReset}>重置</Button> : null}
			</div>
			{filtersOpen ? (
				<div className="mt-3 flex flex-wrap items-center gap-2">
					<Select
						allowClear
						placeholder="资产类型"
						style={{ minWidth: 160 }}
						value={assetType}
						onChange={(value) => onAssetTypeChange(value || "ALL")}
						options={TYPE_OPTIONS}
					/>
					<Select
						allowClear
						placeholder="密级"
						style={{ minWidth: 150 }}
						value={classification}
						onChange={(value) => onClassificationChange(value || "ALL")}
						options={CLASSIFICATION_OPTIONS}
					/>
					<Select
						allowClear
						placeholder="治理状态"
						style={{ minWidth: 150 }}
						value={governanceStatus}
						onChange={(value) => onGovernanceStatusChange(value || "ALL")}
						options={GOVERNANCE_OPTIONS}
					/>
					<Select
						allowClear
						placeholder="映射状态"
						style={{ minWidth: 150 }}
						value={matchStatus}
						onChange={(value) => onMatchStatusChange(value || "ALL")}
						options={MATCH_OPTIONS}
					/>
					<div>
						<AssetTagFilter value={selectedTagIds} onChange={onTagIdsChange} disabled={!tagFilterEnabled} />
						{!tagFilterEnabled ? (
							<div className="mt-1 text-xs text-amber-700">
								旧版资产门户不支持业务数据标签筛选，请启用新版资产门户后使用。
							</div>
						) : null}
					</div>
				</div>
			) : null}
		</Card>
	);
}
