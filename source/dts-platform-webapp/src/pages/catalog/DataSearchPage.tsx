import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Button, Card, Input, Select, Space, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { EmptyState } from "@/components/empty-state";
import { PageHeader } from "@/components/page-header";
import { listDomains, searchCatalog } from "@/api/platformApi";

type SearchRow = {
	id: string;
	name: string;
	type: string;
	assetKind: "DATASET" | "TABLE" | "COLUMN";
	domainId?: string;
	domain?: string;
	owner?: string;
	datasetName?: string;
	updatedAt?: string;
};

const TYPE_OPTIONS = [
	{ label: "全部类型", value: "ALL" },
	{ label: "数据集", value: "DATASET" },
	{ label: "表", value: "TABLE" },
	{ label: "字段", value: "COLUMN" },
];

const DATASET_TYPE_OPTIONS = [
	{ label: "全部系统", value: "ALL" },
	{ label: "Hive", value: "HIVE" },
	{ label: "JDBC", value: "JDBC" },
	{ label: "文件", value: "FILE" },
];

const CLASSIFICATION_OPTIONS = [
	{ label: "全部密级", value: "ALL" },
	{ label: "公开", value: "PUBLIC" },
	{ label: "内部", value: "INTERNAL" },
	{ label: "秘密", value: "SECRET" },
	{ label: "机密", value: "CONFIDENTIAL" },
];

const LAYER_OPTIONS = [
	{ label: "全部分层", value: "ALL" },
	{ label: "ODS", value: "ODS" },
	{ label: "DWD", value: "DWD" },
	{ label: "DWS", value: "DWS" },
	{ label: "ADS", value: "ADS" },
];

const SEARCH_FORM_STORAGE_KEY = "catalog.search.form.v1";
const DATASET_FILTER_STORAGE_KEY = "catalog.asset.filter.v1";

export default function DataSearchPage() {
	const [keyword, setKeyword] = useState("");
	const [domain, setDomain] = useState<string | undefined>();
	const [assetType, setAssetType] = useState<string>("ALL");
	const [datasetType, setDatasetType] = useState<string>("ALL");
	const [classification, setClassification] = useState<string>("ALL");
	const [warehouseLayer, setWarehouseLayer] = useState<string>("ALL");
	const [loading, setLoading] = useState(false);
	const [results, setResults] = useState<SearchRow[]>([]);
	const [domains, setDomains] = useState<{ id: string; name: string }[]>([]);
	const [searched, setSearched] = useState(false);

	useEffect(() => {
		try {
			const raw = localStorage.getItem(SEARCH_FORM_STORAGE_KEY);
			if (raw) {
				const saved = JSON.parse(raw);
				setKeyword(typeof saved?.keyword === "string" ? saved.keyword : "");
				setDomain(typeof saved?.domain === "string" && saved.domain ? saved.domain : undefined);
				setAssetType(typeof saved?.assetType === "string" && saved.assetType ? saved.assetType : "ALL");
				setDatasetType(typeof saved?.datasetType === "string" && saved.datasetType ? saved.datasetType : "ALL");
				setClassification(typeof saved?.classification === "string" && saved.classification ? saved.classification : "ALL");
				setWarehouseLayer(typeof saved?.warehouseLayer === "string" && saved.warehouseLayer ? saved.warehouseLayer : "ALL");
			}
		} catch {
			// ignore malformed cache
		}
		void loadDomains();
	}, []);

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

	const saveCurrentQuery = () => {
		const payload = {
			keyword,
			domain: domain || "",
			assetType,
			datasetType,
			classification,
			warehouseLayer,
		};
		localStorage.setItem(SEARCH_FORM_STORAGE_KEY, JSON.stringify(payload));
		toast.success("已保存当前检索条件");
	};

	const restoreSavedQuery = () => {
		try {
			const raw = localStorage.getItem(SEARCH_FORM_STORAGE_KEY);
			if (!raw) {
				toast.warning("暂无已保存的检索条件");
				return;
			}
			const saved = JSON.parse(raw);
			setKeyword(typeof saved?.keyword === "string" ? saved.keyword : "");
			setDomain(typeof saved?.domain === "string" && saved.domain ? saved.domain : undefined);
			setAssetType(typeof saved?.assetType === "string" && saved.assetType ? saved.assetType : "ALL");
			setDatasetType(typeof saved?.datasetType === "string" && saved.datasetType ? saved.datasetType : "ALL");
			setClassification(typeof saved?.classification === "string" && saved.classification ? saved.classification : "ALL");
			setWarehouseLayer(typeof saved?.warehouseLayer === "string" && saved.warehouseLayer ? saved.warehouseLayer : "ALL");
			toast.success("已恢复检索条件");
		} catch {
			toast.error("检索条件恢复失败");
		}
	};

	const applyAssetFilters = () => {
		try {
			const raw = localStorage.getItem(DATASET_FILTER_STORAGE_KEY);
			if (!raw) {
				toast.warning("未找到资产列表筛选条件");
				return;
			}
			const saved = JSON.parse(raw);
			setDomain(typeof saved?.domain === "string" && saved.domain ? saved.domain : undefined);
			setDatasetType(typeof saved?.assetType === "string" && saved.assetType ? saved.assetType : "ALL");
			setClassification(typeof saved?.classification === "string" && saved.classification ? saved.classification : "ALL");
			setWarehouseLayer(typeof saved?.warehouseLayer === "string" && saved.warehouseLayer ? saved.warehouseLayer : "ALL");
			toast.success("已应用资产列表筛选条件");
		} catch {
			toast.error("资产筛选条件读取失败");
		}
	};

	const normalizeRows = (payload: any): SearchRow[] => {
		const rows: SearchRow[] = [];
		const datasets = Array.isArray(payload?.datasets) ? payload.datasets : [];
		const tables = Array.isArray(payload?.tables) ? payload.tables : [];
		const columns = Array.isArray(payload?.columns) ? payload.columns : [];
		datasets.forEach((item: any) => {
			if (!item) return;
			rows.push({
				id: String(item.id || `dataset-${rows.length}`),
				name: String(item.name || item.hiveTable || "-"),
				type: item.type ? String(item.type) : "DATASET",
				assetKind: "DATASET",
				domainId: item.domainId ? String(item.domainId) : undefined,
				domain: item.domainName || undefined,
				owner: item.owner || item.ownerDept || undefined,
				updatedAt: item.updatedAt || undefined,
			});
		});
		tables.forEach((item: any) => {
			if (!item) return;
			rows.push({
				id: String(item.id || `table-${rows.length}`),
				name: String(item.name || "-"),
				type: "TABLE",
				assetKind: "TABLE",
				domainId: item.domainId ? String(item.domainId) : undefined,
				domain: item.domainName || undefined,
				owner: item.owner || item.datasetOwnerDept || undefined,
				datasetName: item.datasetName || undefined,
			});
		});
		columns.forEach((item: any) => {
			if (!item) return;
			const columnName = String(item.name || "-");
			const tableName = String(item.tableName || item.datasetName || "").trim();
			rows.push({
				id: String(item.id || `column-${rows.length}`),
				name: tableName ? `${tableName}.${columnName}` : columnName,
				type: "COLUMN",
				assetKind: "COLUMN",
				domainId: item.domainId ? String(item.domainId) : undefined,
				domain: item.domainName || undefined,
				owner: item.datasetOwnerDept || undefined,
				datasetName: item.datasetName || undefined,
			});
		});
		return rows;
	};

	const handleSearch = async () => {
		const trimmed = keyword.trim();
		if (!trimmed) {
			toast.error("请输入关键词后再搜索");
			return;
		}
		setLoading(true);
		setSearched(true);
		try {
			const resp: any = await searchCatalog({
				keyword: trimmed,
				types: assetType === "ALL" ? undefined : assetType,
				domainId: domain && domain !== "ALL" ? domain : undefined,
				classification: classification === "ALL" ? undefined : classification,
				warehouseLayer: warehouseLayer === "ALL" ? undefined : warehouseLayer,
				datasetType: datasetType === "ALL" ? undefined : datasetType,
				enabledOnly: true,
				limit: 200,
			});
			setResults(normalizeRows(resp || {}));
			saveCurrentQuery();
		} catch (error: any) {
			toast.error(error?.message || "搜索失败");
		} finally {
			setLoading(false);
		}
	};

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
			title: "来源",
			dataIndex: "datasetName",
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
			/>

			<Card title="搜索条件">
				<Space size={12} wrap>
					<Input.Search
						placeholder="输入关键词"
						style={{ width: 320 }}
						value={keyword}
						onChange={(event) => setKeyword(event.target.value)}
						onSearch={handleSearch}
						allowClear
					/>
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
					<Select
						allowClear
						placeholder="数据源类型"
						style={{ minWidth: 180 }}
						value={datasetType}
						onChange={(value) => setDatasetType(value || "ALL")}
						options={DATASET_TYPE_OPTIONS}
					/>
					<Select
						allowClear
						placeholder="密级"
						style={{ minWidth: 180 }}
						value={classification}
						onChange={(value) => setClassification(value || "ALL")}
						options={CLASSIFICATION_OPTIONS}
					/>
					<Select
						allowClear
						placeholder="分层"
						style={{ minWidth: 180 }}
						value={warehouseLayer}
						onChange={(value) => setWarehouseLayer(value || "ALL")}
						options={LAYER_OPTIONS}
					/>
					<Button type="primary" onClick={handleSearch} loading={loading}>
						搜索
					</Button>
					<Button onClick={saveCurrentQuery}>保存条件</Button>
					<Button onClick={restoreSavedQuery}>恢复条件</Button>
					<Button onClick={applyAssetFilters}>应用资产筛选</Button>
				</Space>
			</Card>

			<Card title="搜索结果">
				{results.length ? (
					<Table
						rowKey="id"
						columns={columns}
						dataSource={results}
						loading={loading}
						pagination={{ pageSize: 10 }}
					/>
				) : searched ? (
					<EmptyState title="暂无结果" description="调整筛选条件后重新搜索。" />
				) : (
					<EmptyState title="开始检索" description="输入关键词并点击搜索。"/>
				)}
			</Card>
		</div>
	);
}
