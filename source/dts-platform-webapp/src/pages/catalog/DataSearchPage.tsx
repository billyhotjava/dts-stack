import { useState } from "react";
import { Card, Input, Select, Space, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";

type SearchRow = {
	id: string;
	name: string;
	type: string;
	domain?: string;
	owner?: string;
	updatedAt?: string;
};

const SEARCH_RESULTS: SearchRow[] = [];

export default function DataSearchPage() {
	const [keyword, setKeyword] = useState("");
	const [domain, setDomain] = useState<string | undefined>();
	const [assetType, setAssetType] = useState<string | undefined>();

	const columns: ColumnsType<SearchRow> = [
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
	];

	return (
		<div className="space-y-4">
			<PageHeader
				title="数据资产门户 · 数据搜索"
				description="面向资产名称、字段与描述的统一检索入口。"
			/>

			<Card title="搜索条件">
				<Space size={12} wrap>
					<Input.Search
						placeholder="输入关键词"
						style={{ width: 320 }}
						value={keyword}
						onChange={(event) => setKeyword(event.target.value)}
						allowClear
					/>
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
				</Space>
			</Card>

			<Card title="搜索结果">
				{SEARCH_RESULTS.length ? (
					<Table rowKey="id" columns={columns} dataSource={SEARCH_RESULTS} pagination={{ pageSize: 10 }} />
				) : (
					<EmptyState title="暂无结果" description="调整筛选条件后重新搜索。" />
				)}
			</Card>
		</div>
	);
}
