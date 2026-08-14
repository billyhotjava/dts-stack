import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Button, Input, Modal, Select, Space, Tag, Typography } from "antd";
import { actionColumn, appendDetailAction, CompactTable, RecordDetailDrawer } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { SearchOutlined } from "@ant-design/icons";
import { useGovernanceManageAccess } from "@/hooks/useModuleManageAccess";
import { listIndicators, deleteIndicator, previewIndicatorSql, generateAndRunIndicators } from "@/api/platformApi";

const DOMAIN_OPTIONS = [
	{ label: "全部", value: "" },
	{ label: "财务", value: "FINANCE" },
	{ label: "运营", value: "OPERATION" },
	{ label: "质量", value: "QUALITY" },
	{ label: "合规", value: "COMPLIANCE" },
	{ label: "自定义", value: "CUSTOM" },
];

const STATUS_OPTIONS = [
	{ label: "全部", value: "" },
	{ label: "草稿", value: "DRAFT" },
	{ label: "已发布", value: "PUBLISHED" },
	{ label: "已归档", value: "ARCHIVED" },
];

const STATUS_COLORS: Record<string, string> = {
	DRAFT: "default",
	PUBLISHED: "green",
	ARCHIVED: "red",
};

const DOMAIN_COLORS: Record<string, string> = {
	FINANCE: "blue",
	OPERATION: "green",
	QUALITY: "orange",
	COMPLIANCE: "red",
	CUSTOM: "purple",
};

type Indicator = any;

export default function Page() {
	const [data, setData] = useState<Indicator[]>([]);
	const [loading, setLoading] = useState(false);
	const [domain, setDomain] = useState("");
	const [status, setStatus] = useState("");
	const [keyword, setKeyword] = useState("");
	const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
	const [previewSql, setPreviewSql] = useState("");
	const [previewOpen, setPreviewOpen] = useState(false);
	const [generating, setGenerating] = useState(false);
	const [detailRow, setDetailRow] = useState<Indicator | null>(null);
	const canManage = useGovernanceManageAccess();

	const fetchList = async () => {
		setLoading(true);
		try {
			const params: any = {};
			if (domain) params.domain = domain;
			if (status) params.status = status;
			if (keyword) params.keyword = keyword;
			const res: any = await listIndicators(params);
			// apiClient interceptor already unwraps ApiResponse.data, so res is the page payload
			const payload = res as any;
			setData(payload?.content ?? []);
		} catch {
			// interceptor handles error
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		fetchList();
	}, [domain, status]);

	const handleSearch = () => {
		fetchList();
	};

	const handlePreviewSql = async (record: Indicator) => {
		try {
			setPreviewSql("-- 正在生成 SQL...");
			setPreviewOpen(true);
			const res: any = await previewIndicatorSql(record.id);
			const d = res?.data ?? res;
			setPreviewSql(typeof d === "string" ? d : (d?.sql ?? JSON.stringify(d, null, 2)));
		} catch {
			setPreviewSql("-- SQL 预览失败");
		}
	};

	const handleGenerate = async (record: Indicator) => {
		Modal.confirm({
			title: "生成并执行",
			content: `确定要为指标「${record.name}」生成 dbt 模型并执行吗？`,
			okText: "确定",
			cancelText: "取消",
			onOk: async () => {
				try {
					await generateAndRunIndicators({ indicatorIds: [record.id] });
					toast.success("已提交生成执行");
					fetchList();
				} catch {
					// interceptor handles error
				}
			},
		});
	};

	const handleBatchGenerate = async () => {
		if (selectedRowKeys.length === 0) {
			toast.error("请至少选择一个指标");
			return;
		}
		Modal.confirm({
			title: "批量生成并执行",
			content: `确定要批量生成 ${selectedRowKeys.length} 个指标的 dbt 模型并执行吗？`,
			okText: "确定",
			cancelText: "取消",
			onOk: async () => {
				setGenerating(true);
				try {
					await generateAndRunIndicators({ indicatorIds: selectedRowKeys as string[] });
					toast.success("已提交批量生成执行");
					setSelectedRowKeys([]);
					fetchList();
				} catch {
					// interceptor handles error
				} finally {
					setGenerating(false);
				}
			},
		});
	};

	const handleDelete = (record: Indicator) => {
		Modal.confirm({
			title: "确认删除",
			content: `确定要删除指标「${record.name}」吗？`,
			okText: "删除",
			okType: "danger",
			cancelText: "取消",
			onOk: async () => {
				try {
					await deleteIndicator(record.id);
					toast.success("指标已删除");
					fetchList();
				} catch {
					// interceptor handles error
				}
			},
		});
	};

	const baseColumns: ColumnsType<Indicator> = [
		{
			title: "编码",
			dataIndex: "code",
			width: 160,
			ellipsis: true,
			sorter: (a, b) => (a.code || "").localeCompare(b.code || ""),
		},
		{
			title: "名称",
			dataIndex: "name",
			width: 200,
			ellipsis: true,
			sorter: (a, b) => (a.name || "").localeCompare(b.name || ""),
		},
		{
			title: "领域",
			dataIndex: "domain",
			width: 100,
			render: (v: string) => (v ? <Tag color={DOMAIN_COLORS[v] ?? "default"}>{v}</Tag> : "-"),
		},
		{
			title: "聚合方式",
			dataIndex: "aggregation",
			width: 100,
			render: (v: string) => v ?? "-",
		},
		{
			title: "源表",
			dataIndex: "sourceTable",
			width: 180,
			ellipsis: true,
			sorter: (a, b) => (a.sourceTable || "").localeCompare(b.sourceTable || ""),
			render: (v: string) => v ?? "-",
		},
		{
			title: "状态",
			dataIndex: "status",
			width: 100,
			render: (v: string) => (v ? <Tag color={STATUS_COLORS[v] ?? "default"}>{v}</Tag> : "-"),
		},
		actionColumn<Indicator>(
			(record) => [
				{ key: "preview-sql", label: "预览SQL", onClick: () => handlePreviewSql(record) },
				{ key: "generate", label: "生成", hidden: !canManage, onClick: () => handleGenerate(record) },
				{ key: "delete", label: "删除", danger: true, hidden: !canManage, onClick: () => handleDelete(record) },
			],
			{ width: 320 },
		),
	];

	const columns = useMemo(
		() => appendDetailAction(baseColumns, (row) => setDetailRow(row)),
		// eslint-disable-next-line react-hooks/exhaustive-deps
		[canManage],
	);

	return (
		<div style={{ padding: 24 }}>
			<div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
				<Typography.Title level={4} style={{ margin: 0 }}>
					指标定义列表
				</Typography.Title>
				{canManage && (
					<Button
						type="primary"
						loading={generating}
						disabled={selectedRowKeys.length === 0}
						onClick={handleBatchGenerate}
					>
						批量生成 ({selectedRowKeys.length})
					</Button>
				)}
			</div>

			<Space style={{ marginBottom: 16 }} wrap>
				<Select
					style={{ width: 120 }}
					options={DOMAIN_OPTIONS}
					value={domain}
					onChange={setDomain}
					placeholder="领域"
				/>
				<Select
					style={{ width: 120 }}
					options={STATUS_OPTIONS}
					value={status}
					onChange={setStatus}
					placeholder="状态"
				/>
				<Input
					style={{ width: 200 }}
					placeholder="搜索编码/名称"
					value={keyword}
					onChange={(e) => setKeyword(e.target.value)}
					onPressEnter={handleSearch}
					suffix={<SearchOutlined style={{ cursor: "pointer" }} onClick={handleSearch} />}
				/>
			</Space>

			<CompactTable
				rowKey="id"
				columns={columns}
				dataSource={data}
				loading={loading}
				pagination={{ defaultPageSize: 10, showSizeChanger: true, showTotal: (t) => `共 ${t} 条` }}
				size="middle"
				rowSelection={{
					selectedRowKeys,
					onChange: setSelectedRowKeys,
				}}
			/>

			{/* SQL Preview Modal */}
			<Modal title="SQL 预览" open={previewOpen} onCancel={() => setPreviewOpen(false)} footer={null} width={720}>
				<Input.TextArea value={previewSql} readOnly rows={18} style={{ fontFamily: "monospace" }} />
			</Modal>
			<RecordDetailDrawer<Indicator>
				open={detailRow !== null}
				onClose={() => setDetailRow(null)}
				record={detailRow}
				columns={baseColumns}
				title="指标详情"
			/>
		</div>
	);
}
