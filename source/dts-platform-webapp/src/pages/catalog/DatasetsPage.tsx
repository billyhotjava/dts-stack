import { useState } from "react";
import { Card, Input, Select, Space, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";

type AssetRow = {
	id: string;
	name: string;
	type: string;
	domain?: string;
	owner?: string;
	updatedAt?: string;
	status?: string;
};

const ASSET_ROWS: AssetRow[] = [];

export default function Page() {
	const [keyword, setKeyword] = useState("");
	const [domain, setDomain] = useState<string | undefined>();
	const [assetType, setAssetType] = useState<string | undefined>();

	const columns: ColumnsType<AssetRow> = [
		{
			title: "资产名称",
			dataIndex: "name",
			render: (value) => value || "-",
		},
		{
			title: "类型",
			dataIndex: "type",
			render: (value) => (value ? <Tag>{value}</Tag> : "-"),
		},
		{
			title: "主题域",
			dataIndex: "domain",
			render: (value) => value || "-",
		},
		{
			title: "负责人",
			dataIndex: "owner",
			render: (value) => value || "-",
		},
		{
			title: "更新时间",
			dataIndex: "updatedAt",
			render: (value) => value || "-",
		},
		{
			title: "状态",
			dataIndex: "status",
			render: (value) => (value ? <Tag color="green">{value}</Tag> : "-"),
		},
	];

	return (
		<div className="space-y-4">
			<PageHeader
				title="数据资产门户 · 资产地图"
				description="按主题域与资产类型组织资产视图，便于全局盘点与治理。"
			/>

			<Card title="资产筛选">
				<Space size={12} wrap>
					<Select
						allowClear
						placeholder="主题域"
						style={{ minWidth: 180 }}
						value={domain}
						onChange={(value) => setDomain(value)}
						options={[]}
					/>
					<Select
						allowClear
						placeholder="资产类型"
						style={{ minWidth: 180 }}
						value={assetType}
						onChange={(value) => setAssetType(value)}
						options={[]}
					/>
					<Input
						placeholder="搜索资产名称 / 描述"
						style={{ width: 260 }}
						value={keyword}
						onChange={(event) => setKeyword(event.target.value)}
						allowClear
					/>
				</Space>
			</Card>

			<Card title="资产地图视图">
				<EmptyState title="暂无资产地图" description="请先完成元数据采集或同步资产数据。" />
			</Card>

			<Card title="资产列表">
				{ASSET_ROWS.length ? (
					<Table rowKey="id" columns={columns} dataSource={ASSET_ROWS} pagination={{ pageSize: 10 }} />
				) : (
					<EmptyState title="暂无资产" description="当前筛选条件下未找到资产。" />
				)}
			</Card>
		</div>
	);
}
