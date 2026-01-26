import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Card, Input, Select, Space, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { listDatasets, listDomains } from "@/api/platformApi";

type AssetRow = {
	id: string;
	name: string;
	type: string;
	domainId?: string;
	domain?: string;
	owner?: string;
	updatedAt?: string;
	status?: string;
};

const TYPE_OPTIONS = [
	{ label: "全部类型", value: "ALL" },
	{ label: "Hive", value: "HIVE" },
	{ label: "JDBC", value: "JDBC" },
	{ label: "文件", value: "FILE" },
];

export default function Page() {
	const [keyword, setKeyword] = useState("");
	const [domain, setDomain] = useState<string | undefined>();
	const [assetType, setAssetType] = useState<string>("ALL");
	const [loading, setLoading] = useState(false);
	const [records, setRecords] = useState<AssetRow[]>([]);
	const [pageState, setPageState] = useState({ page: 1, size: 10, total: 0 });
	const [domains, setDomains] = useState<{ id: string; name: string }[]>([]);

	useEffect(() => {
		void loadDomains();
	}, []);

	useEffect(() => {
		void loadDatasets(1, pageState.size);
	}, [keyword, domain, assetType]);

	const domainMap = useMemo(() => {
		return new Map(domains.map((item) => [item.id, item.name]));
	}, [domains]);

	const domainOptions = useMemo(() => {
		return [
			{ label: "全部主题域", value: "ALL" },
			...domains.map((item) => ({ label: item.name, value: item.id })),
		];
	}, [domains]);

	const loadDomains = async () => {
		try {
			const resp: any = await listDomains(0, 200, "");
			const list = Array.isArray(resp?.content) ? resp.content : [];
			setDomains(
				list
					.map((item: any) => ({ id: String(item.id || ""), name: String(item.name || "").trim() }))
					.filter((item: any) => item.id && item.name),
			);
		} catch (error: any) {
			toast.error(error?.message || "主题域加载失败");
		}
	};

	const loadDatasets = async (page = 1, size = 10) => {
		setLoading(true);
		try {
			const resp: any = await listDatasets({
				page: page - 1,
				size,
				keyword: keyword.trim() || undefined,
				domainId: domain && domain !== "ALL" ? domain : undefined,
				type: assetType === "ALL" ? undefined : assetType,
			});
			const content = Array.isArray(resp?.content) ? resp.content : [];
			setRecords(
				content.map((item: any) => ({
					id: String(item.id || ""),
					name: item.name || "-",
					type: item.type || "-",
					domainId: item.domainId ? String(item.domainId) : undefined,
					domain: item.domainId ? domainMap.get(String(item.domainId)) : undefined,
					owner: item.owner || item.ownerDept || "-",
					status: item.enabled === false ? "停用" : "启用",
					updatedAt: item.lastModifiedDate || item.createdDate || undefined,
				})),
			);
			setPageState({
				page: Number(resp?.page ?? page - 1) + 1,
				size: Number(resp?.size ?? size),
				total: Number(resp?.total ?? 0),
			});
		} catch (error: any) {
			toast.error(error?.message || "资产加载失败");
			setRecords([]);
		} finally {
			setLoading(false);
		}
	};

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
			render: (value, row) => {
				if (value) return value;
				if (row.domainId) {
					return domainMap.get(row.domainId) || "-";
				}
				return "-";
			},
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
						value={domain || "ALL"}
						onChange={(value) => setDomain(value === "ALL" ? undefined : value)}
						options={domainOptions}
					/>
					<Select
						allowClear
						placeholder="资产类型"
						style={{ minWidth: 180 }}
						value={assetType}
						onChange={(value) => setAssetType(value || "ALL")}
						options={TYPE_OPTIONS}
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
				{records.length ? (
					<div className="grid gap-3 md:grid-cols-3">
						<Card size="small" title="资产总量">
							<div className="text-lg font-semibold">{pageState.total}</div>
							<div className="text-xs text-muted-foreground">已纳入治理的资产记录</div>
						</Card>
						<Card size="small" title="主题域">
							<div className="text-lg font-semibold">{domains.length}</div>
							<div className="text-xs text-muted-foreground">已配置主题域数量</div>
						</Card>
						<Card size="small" title="活跃资产">
							<div className="text-lg font-semibold">
								{records.filter((item) => item.status === "启用").length}
							</div>
							<div className="text-xs text-muted-foreground">当前页启用资产</div>
						</Card>
					</div>
				) : (
					<EmptyState title="暂无资产地图" description="请先完成元数据采集或同步资产数据。" />
				)}
			</Card>

			<Card title="资产列表">
				{records.length ? (
					<Table
						rowKey="id"
						columns={columns}
						dataSource={records}
						loading={loading}
						pagination={{
							current: pageState.page,
							pageSize: pageState.size,
							total: pageState.total,
							showSizeChanger: true,
						}}
						onChange={(pagination) => {
							const nextPage = pagination.current || 1;
							const nextSize = pagination.pageSize || 10;
							void loadDatasets(nextPage, nextSize);
						}}
					/>
				) : (
					<EmptyState title="暂无资产" description="当前筛选条件下未找到资产。" />
				)}
			</Card>
		</div>
	);
}
