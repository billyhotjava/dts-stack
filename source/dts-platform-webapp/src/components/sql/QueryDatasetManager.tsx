import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Button,
	Card,
	Descriptions,
	Form,
	Input,
	Modal,
	Select,
	Space,
	Spin,
	Tag,
	Tabs,
	Typography,
} from "antd";
import { CompactTable } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { ExclamationCircleOutlined } from "@ant-design/icons";
import reportsService, { type ReportLink } from "@/api/services/reportsService";
import {
	archiveQueryDataset,
	createQueryDatasetVersion,
	listQueryDatasets,
	listQueryDatasetVersions,
	publishQueryDataset,
	type QueryDatasetAsset,
	type QueryDatasetVersion,
} from "@/api/sql-workbench";
import { formatTime } from "@/utils/textUtils";

const { Text } = Typography;
const { TextArea } = Input;

type CreateVersionFormValues = {
	sqlText: string;
	changeSummary?: string;
};

const datasetStatusTag = (value?: string | null) => {
	const status = String(value || "").toUpperCase();
	const color = status === "PUBLISHED" ? "green" : status === "ARCHIVED" ? "default" : "blue";
	return <Tag color={color}>{status || "-"}</Tag>;
};

const reportClassificationTag = (value?: string | null) => {
	const key = String(value || "").toUpperCase();
	const color = key === "PUBLIC" ? "green" : key === "INTERNAL" ? "blue" : key === "SECRET" ? "orange" : key === "CONFIDENTIAL" ? "red" : "default";
	const label = key === "PUBLIC" ? "公开" : key === "INTERNAL" ? "内部" : key === "SECRET" ? "秘密" : key === "CONFIDENTIAL" ? "机密" : value || "-";
	return <Tag color={color}>{label}</Tag>;
};

export function QueryDatasetManager() {
	const [loadingDatasets, setLoadingDatasets] = useState(false);
	const [datasets, setDatasets] = useState<QueryDatasetAsset[]>([]);
	const [datasetKeyword, setDatasetKeyword] = useState("");
	const [datasetStatus, setDatasetStatus] = useState<string>("ALL");
	const [selectedDatasetId, setSelectedDatasetId] = useState<string>();

	const [loadingVersions, setLoadingVersions] = useState(false);
	const [versions, setVersions] = useState<QueryDatasetVersion[]>([]);

	const [loadingReports, setLoadingReports] = useState(false);
	const [reports, setReports] = useState<ReportLink[]>([]);

	const [publishingVersionNo, setPublishingVersionNo] = useState<number | null>(null);
	const [archivingDatasetId, setArchivingDatasetId] = useState<string | null>(null);

	const [versionModalOpen, setVersionModalOpen] = useState(false);
	const [versionSaving, setVersionSaving] = useState(false);
	const [previewVersion, setPreviewVersion] = useState<QueryDatasetVersion | null>(null);
	const [versionForm] = Form.useForm<CreateVersionFormValues>();

	const loadDatasets = useCallback(async () => {
		setLoadingDatasets(true);
		try {
			const rows = await listQueryDatasets();
			setDatasets(Array.isArray(rows) ? (rows as QueryDatasetAsset[]) : []);
		} catch (error: any) {
			toast.error(error?.message || "加载查询数据集失败");
			setDatasets([]);
		} finally {
			setLoadingDatasets(false);
		}
	}, []);

	const loadDatasetDetails = useCallback(async (datasetId: string) => {
		setLoadingVersions(true);
		setLoadingReports(true);
		try {
			const [versionRows, reportRows] = await Promise.all([
				listQueryDatasetVersions(datasetId).catch(() => []),
				reportsService.listAll({ queryDatasetId: datasetId }).catch(() => []),
			]);
			setVersions(Array.isArray(versionRows) ? (versionRows as QueryDatasetVersion[]) : []);
			setReports(Array.isArray(reportRows) ? (reportRows as ReportLink[]) : []);
		} finally {
			setLoadingVersions(false);
			setLoadingReports(false);
		}
	}, []);

	useEffect(() => {
		void loadDatasets();
	}, [loadDatasets]);

	useEffect(() => {
		if (!datasets.length) {
			setSelectedDatasetId(undefined);
			return;
		}
		if (!selectedDatasetId || !datasets.some((item) => item.id === selectedDatasetId)) {
			setSelectedDatasetId(datasets[0].id);
		}
	}, [datasets, selectedDatasetId]);

	useEffect(() => {
		if (!selectedDatasetId) {
			setVersions([]);
			setReports([]);
			return;
		}
		void loadDatasetDetails(selectedDatasetId);
	}, [selectedDatasetId, loadDatasetDetails]);

	const filteredDatasets = useMemo(() => {
		return datasets.filter((item) => {
			const keyword = datasetKeyword.trim().toLowerCase();
			const hitKeyword =
				!keyword ||
				String(item.name || "").toLowerCase().includes(keyword) ||
				String(item.sourceDatasourceName || "").toLowerCase().includes(keyword) ||
				String(item.ownerDept || "").toLowerCase().includes(keyword);
			const hitStatus = datasetStatus === "ALL" || String(item.status || "").toUpperCase() === datasetStatus;
			return hitKeyword && hitStatus;
		});
	}, [datasets, datasetKeyword, datasetStatus]);

	const selectedDataset = useMemo(
		() => datasets.find((item) => item.id === selectedDatasetId),
		[datasets, selectedDatasetId],
	);

	const openCreateVersion = () => {
		const latestSql = versions[0]?.sqlText || "";
		versionForm.resetFields();
		versionForm.setFieldsValue({
			sqlText: latestSql,
			changeSummary: "",
		});
		setVersionModalOpen(true);
	};

	const handleCreateVersion = async () => {
		if (!selectedDatasetId) return;
		try {
			const values = await versionForm.validateFields();
			setVersionSaving(true);
			await createQueryDatasetVersion(selectedDatasetId, {
				sqlText: values.sqlText,
				changeSummary: values.changeSummary,
			});
			toast.success("新版本已创建");
			setVersionModalOpen(false);
			await Promise.all([loadDatasets(), loadDatasetDetails(selectedDatasetId)]);
		} catch (error: any) {
			if (error?.errorFields) return;
			toast.error(error?.message || "创建版本失败");
		} finally {
			setVersionSaving(false);
		}
	};

	const handlePublish = async (datasetId: string, versionNo?: number) => {
		setPublishingVersionNo(versionNo ?? -1);
		try {
			await publishQueryDataset(datasetId, typeof versionNo === "number" ? { versionNo } : {});
			toast.success(typeof versionNo === "number" ? `已发布版本 v${versionNo}` : "已发布最新版本");
			await Promise.all([loadDatasets(), loadDatasetDetails(datasetId)]);
		} catch (error: any) {
			toast.error(error?.message || "发布失败");
		} finally {
			setPublishingVersionNo(null);
		}
	};

	const handleArchiveDataset = (dataset: QueryDatasetAsset) => {
		Modal.confirm({
			title: "归档查询数据集",
			icon: <ExclamationCircleOutlined />,
			content: `归档后数据集将被禁用：${dataset.name}`,
			okText: "确认归档",
			cancelText: "取消",
			onOk: async () => {
				if (!dataset?.id) return;
				setArchivingDatasetId(dataset.id);
				try {
					await archiveQueryDataset(dataset.id);
					toast.success("数据集已归档");
					await loadDatasets();
				} catch (error: any) {
					toast.error(error?.message || "归档失败");
				} finally {
					setArchivingDatasetId(null);
				}
			},
		});
	};

	const datasetColumns: ColumnsType<QueryDatasetAsset> = [
		{
			title: "名称",
			dataIndex: "name",
			sorter: (a, b) => (a.name || "").localeCompare(b.name || ""),
			width: 220,
			render: (_, record) => (
				<div>
					<div className="font-medium">{record.name}</div>
					<Text type="secondary" className="text-xs">
						{record.id}
					</Text>
				</div>
			),
		},
		{
			title: "状态",
			dataIndex: "status",
			width: 110,
			render: (value) => datasetStatusTag(value),
		},
		{
			title: "已发布",
			dataIndex: "publishedVersion",
			width: 90,
			render: (value) => (typeof value === "number" ? `v${value}` : "-"),
		},
		{
			title: "契约版本",
			dataIndex: "semanticContractVersion",
			width: 140,
			render: (value) => value || "-",
		},
		{
			title: "来源数据源",
			dataIndex: "sourceDatasourceName",
			width: 150,
			render: (value) => value || "-",
		},
		{
			title: "部门",
			dataIndex: "ownerDept",
			width: 120,
			render: (value) => value || "-",
		},
		{
			title: "更新时间",
			dataIndex: "lastModifiedDate",
			sorter: (a, b) => {
				const ta = a.lastModifiedDate ? new Date(a.lastModifiedDate as any).getTime() : 0;
				const tb = b.lastModifiedDate ? new Date(b.lastModifiedDate as any).getTime() : 0;
				return ta - tb;
			},
			width: 180,
			render: (value) => formatTime(value),
		},
	];

	const versionColumns: ColumnsType<QueryDatasetVersion> = [
		{
			title: "版本",
			dataIndex: "versionNo",
			width: 90,
			render: (value) => `v${value}`,
		},
		{
			title: "状态",
			dataIndex: "status",
			width: 110,
			render: (value) => datasetStatusTag(value),
		},
		{
			title: "发布时间",
			dataIndex: "publishedAt",
			sorter: (a, b) => {
				const ta = a.publishedAt ? new Date(a.publishedAt as any).getTime() : 0;
				const tb = b.publishedAt ? new Date(b.publishedAt as any).getTime() : 0;
				return ta - tb;
			},
			width: 180,
			render: (value) => formatTime(value),
		},
		{
			title: "变更说明",
			dataIndex: "changeSummary",
			render: (value) => value || "-",
		},
		{
			title: "操作",
			key: "actions",
			width: 220,
			render: (_, record) => (
				<Space size={8}>
					<Button type="link" size="small" onClick={() => setPreviewVersion(record)}>
						查看 SQL
					</Button>
					<Button
						type="link"
						size="small"
						disabled={String(record.status || "").toUpperCase() === "PUBLISHED"}
						loading={publishingVersionNo === record.versionNo}
						onClick={() => {
							if (!selectedDatasetId) return;
							void handlePublish(selectedDatasetId, record.versionNo);
						}}
					>
						发布
					</Button>
				</Space>
			),
		},
	];

	const reportColumns: ColumnsType<ReportLink> = [
		{
			title: "看板名称",
			dataIndex: "title",
			sorter: (a, b) => (a.title || "").localeCompare(b.title || ""),
			render: (_, record) => (
				<div>
					<div className="font-medium">{record.title || "-"}</div>
					<Text type="secondary" className="text-xs">
						{record.code || "-"}
					</Text>
				</div>
			),
		},
		{
			title: "类型",
			dataIndex: "reportType",
			width: 120,
			render: (value) => value || "-",
		},
		{
			title: "密级",
			dataIndex: "classification",
			width: 120,
			render: (value) => reportClassificationTag(value),
		},
		{
			title: "绑定版本",
			dataIndex: "queryDatasetVersion",
			width: 120,
			render: (value) => (typeof value === "number" ? `v${value}` : "latest"),
		},
		{
			title: "状态",
			dataIndex: "enabled",
			width: 100,
			render: (value) => (value === false ? <Tag>停用</Tag> : <Tag color="green">启用</Tag>),
		},
	];

	return (
		<div className="space-y-4">
			<Card size="small">
				<Space wrap>
					<Input
						style={{ width: 260 }}
						placeholder="按名称/来源数据源/部门搜索"
						value={datasetKeyword}
						onChange={(event) => setDatasetKeyword(event.target.value)}
					/>
					<Select
						style={{ width: 150 }}
						value={datasetStatus}
						onChange={setDatasetStatus}
						options={[
							{ label: "全部状态", value: "ALL" },
							{ label: "DRAFT", value: "DRAFT" },
							{ label: "PUBLISHED", value: "PUBLISHED" },
							{ label: "ARCHIVED", value: "ARCHIVED" },
						]}
					/>
					<Button onClick={() => void loadDatasets()}>
						刷新数据集
					</Button>
				</Space>
			</Card>

			<div className="grid gap-4 lg:grid-cols-[45%_55%]">
				<Card size="small" title={`查询数据集 (${filteredDatasets.length})`}>
					<CompactTable
						rowKey="id"
						size="small"
						loading={loadingDatasets}
						columns={datasetColumns}
						dataSource={filteredDatasets}
						pagination={{ pageSize: 8, showSizeChanger: false }}
						rowClassName={(record) => (record.id === selectedDatasetId ? "bg-muted/40" : "")}
						onRow={(record) => ({
							onClick: () => setSelectedDatasetId(record.id),
						})}
					/>
				</Card>

				<Card
					size="small"
					title={selectedDataset ? `数据集详情：${selectedDataset.name}` : "数据集详情"}
					extra={
						selectedDataset ? (
							<Space>
								<Button
									type="primary"
									onClick={openCreateVersion}
								>
									新建版本
								</Button>
								<Button
									onClick={() => void handlePublish(selectedDataset.id)}
									loading={publishingVersionNo === -1}
								>
									发布最新版本
								</Button>
								<Button
									danger
									onClick={() => handleArchiveDataset(selectedDataset)}
									loading={archivingDatasetId === selectedDataset.id}
								>
									归档
								</Button>
							</Space>
						) : null
					}
				>
					{selectedDataset ? (
						<div className="space-y-3">
							<Descriptions size="small" column={2} bordered>
								<Descriptions.Item label="状态">{datasetStatusTag(selectedDataset.status)}</Descriptions.Item>
								<Descriptions.Item label="发布版本">
									{typeof selectedDataset.publishedVersion === "number" ? `v${selectedDataset.publishedVersion}` : "-"}
								</Descriptions.Item>
								<Descriptions.Item label="来源数据源">{selectedDataset.sourceDatasourceName || "-"}</Descriptions.Item>
								<Descriptions.Item label="刷新策略">{selectedDataset.refreshStrategy || "-"}</Descriptions.Item>
								<Descriptions.Item label="所属部门">{selectedDataset.ownerDept || "-"}</Descriptions.Item>
								<Descriptions.Item label="更新时间">{formatTime(selectedDataset.lastModifiedDate)}</Descriptions.Item>
							</Descriptions>

							<Tabs
								items={[
									{
										key: "versions",
										label: `版本 (${versions.length})`,
										children: (
											<CompactTable
												rowKey="id"
												size="small"
												loading={loadingVersions}
												columns={versionColumns}
												dataSource={versions}
												pagination={{ pageSize: 6, showSizeChanger: false }}
											/>
										),
									},
									{
										key: "dependencies",
										label: `看板依赖 (${reports.length})`,
										children: (
											<CompactTable
												rowKey="id"
												size="small"
												loading={loadingReports}
												columns={reportColumns}
												dataSource={reports}
												pagination={{ pageSize: 6, showSizeChanger: false }}
											/>
										),
									},
								]}
							/>
						</div>
					) : (
						<div className="py-12 text-center text-muted-foreground">
							{loadingDatasets ? <Spin /> : "请选择一个查询数据集"}
						</div>
					)}
				</Card>
			</div>

			<Modal
				title="新建查询数据集版本"
				open={versionModalOpen}
				okText="创建版本"
				cancelText="取消"
				confirmLoading={versionSaving}
				onCancel={() => setVersionModalOpen(false)}
				onOk={() => void handleCreateVersion()}
			>
				<Form form={versionForm} layout="vertical">
					<Form.Item label="变更说明" name="changeSummary">
						<Input placeholder="例如：新增部门维度过滤逻辑" />
					</Form.Item>
					<Form.Item
						label="SQL 文本"
						name="sqlText"
						rules={[{ required: true, message: "SQL 不能为空" }]}
					>
						<TextArea rows={10} placeholder="请输入版本 SQL" />
					</Form.Item>
				</Form>
			</Modal>

			<Modal
				title={previewVersion ? `查看 SQL - v${previewVersion.versionNo}` : "查看 SQL"}
				open={!!previewVersion}
				width={900}
				footer={null}
				onCancel={() => setPreviewVersion(null)}
			>
				<pre className="max-h-[65vh] overflow-auto rounded bg-muted p-3 text-xs leading-6">
					{previewVersion?.sqlText || ""}
				</pre>
			</Modal>
		</div>
	);
}
