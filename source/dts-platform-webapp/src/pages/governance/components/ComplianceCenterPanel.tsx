import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Alert,
	Button,
	Card,
	Form,
	Input,
	Modal,
	Popconfirm,
	Progress,
	Select,
	Space,
	Switch,
	Tag,
} from "antd";
import { CompactTable } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { } from "@ant-design/icons";
import {
	createComplianceBatch,
	deleteComplianceBatch,
	getComplianceBatch,
	listComplianceBatches,
	listQualityRules,
	updateComplianceItem,
} from "@/api/platformApi";
import { useGovernanceManageAccess } from "@/hooks/useModuleManageAccess";
import { useActiveDept } from "@/store/contextStore";

type ComplianceBatch = {
	id?: string;
	name?: string;
	status?: string;
	progressPct?: number;
	evidenceRequired?: boolean;
	dataLevel?: string;
	templateCode?: string;
	triggeredBy?: string;
	ownerDept?: string;
	lastUpdated?: string;
	passedItems?: number;
	failedItems?: number;
	pendingItems?: number;
	totalItems?: number;
	items?: ComplianceItem[];
};

type ComplianceItem = {
	id?: string;
	ruleName?: string;
	ruleCode?: string;
	status?: string;
	severity?: string;
	datasetAlias?: string;
	qualityRunStatus?: string;
	conclusion?: string;
	evidenceRef?: string;
	qualityRunMessage?: string;
};

type BatchForm = {
	name: string;
	templateCode?: string;
	dataLevel?: string;
	evidenceRequired: boolean;
	ownerDept?: string;
	ruleIds?: string[];
};

type ItemForm = {
	status?: string;
	conclusion?: string;
	evidenceRef?: string;
};

const normalizeStatus = (raw: unknown) => String(raw || "").trim().toUpperCase();

const statusColor = (status?: string) => {
	const normalized = normalizeStatus(status);
	if (normalized === "PASSED" || normalized === "SUCCESS" || normalized === "COMPLETED") return "green";
	if (normalized === "FAILED" || normalized === "ERROR") return "red";
	if (normalized === "RUNNING" || normalized === "IN_PROGRESS") return "blue";
	return "default";
};

export default function ComplianceCenterPanel() {
	const [batches, setBatches] = useState<ComplianceBatch[]>([]);
	const [loading, setLoading] = useState(false);
	const [statusFilter, setStatusFilter] = useState<string>("ALL");
	const [readOnly, setReadOnly] = useState(false);
	const [createOpen, setCreateOpen] = useState(false);
	const [detailOpen, setDetailOpen] = useState(false);
	const [editingItem, setEditingItem] = useState<ComplianceItem | null>(null);
	const [itemModalOpen, setItemModalOpen] = useState(false);
	const [currentBatch, setCurrentBatch] = useState<ComplianceBatch | null>(null);
	const [batchActionId, setBatchActionId] = useState<string>("");
	const [rules, setRules] = useState<Array<{ id: string; name: string }>>([]);
	const [saving, setSaving] = useState(false);
	const [itemSaving, setItemSaving] = useState(false);
	const [batchForm] = Form.useForm<BatchForm>();
	const [itemForm] = Form.useForm<ItemForm>();

	const activeDept = useActiveDept();
	const hasManageAccess = useGovernanceManageAccess();

	const canManage = useMemo(() => {
		return !readOnly && hasManageAccess;
	}, [readOnly, hasManageAccess]);

	const ruleOptions = useMemo(() => rules.map((item) => ({ label: item.name, value: item.id })), [rules]);

	const parseWriteError = (error: any, fallback: string) => {
		const message = String(error?.message || fallback);
		if (message.toLowerCase().includes("access denied") || message.includes("403")) {
			setReadOnly(true);
		}
		return message;
	};

	const loadRules = async () => {
		try {
			const list = await listQualityRules();
			const data = Array.isArray(list) ? list : [];
			setRules(data.map((item: any) => ({ id: String(item.id), name: String(item.name || item.id) })));
		} catch (error: any) {
			toast.error(error?.message || "合规规则加载失败");
		}
	};

	const loadBatches = async () => {
		setLoading(true);
		try {
			const params: any = { limit: 50 };
			if (statusFilter !== "ALL") {
				params.status = statusFilter;
			}
			const list = await listComplianceBatches(params);
			setBatches(Array.isArray(list) ? (list as ComplianceBatch[]) : []);
		} catch (error: any) {
			toast.error(error?.message || "合规批次加载失败");
		} finally {
			setLoading(false);
		}
	};

	const loadBatchDetail = async (id?: string) => {
		if (!id) return;
		try {
			const detail = (await getComplianceBatch(id)) as ComplianceBatch;
			setCurrentBatch(detail || null);
		} catch (error: any) {
			toast.error(error?.message || "合规批次详情加载失败");
		}
	};

	useEffect(() => {
		void loadRules();
	}, []);

	useEffect(() => {
		void loadBatches();
	}, [statusFilter]);

	const openCreate = () => {
		batchForm.setFieldsValue({
			name: "",
			templateCode: "DEFAULT",
			dataLevel: "DATA_INTERNAL",
			evidenceRequired: true,
			ownerDept: activeDept || "",
			ruleIds: [],
		});
		setCreateOpen(true);
	};

	const saveBatch = async () => {
		try {
			const values = await batchForm.validateFields();
			setSaving(true);
			const payload = {
				name: values.name,
				templateCode: values.templateCode || "DEFAULT",
				dataLevel: values.dataLevel || "DATA_INTERNAL",
				evidenceRequired: values.evidenceRequired !== false,
				ownerDept: values.ownerDept ? values.ownerDept.trim() : undefined,
				ruleIds: Array.isArray(values.ruleIds) ? values.ruleIds : [],
				metadata: {
					source: "ui",
					createdFrom: "governance-compliance-panel",
				},
			};
			await createComplianceBatch(payload);
			toast.success("合规批次已创建");
			setCreateOpen(false);
			await loadBatches();
		} catch (error: any) {
			if (error?.errorFields) return;
			toast.error(parseWriteError(error, "合规批次创建失败"));
		} finally {
			setSaving(false);
		}
	};

	const handleDeleteBatch = async (batch: ComplianceBatch) => {
		if (!batch?.id) return;
		try {
			setBatchActionId(batch.id);
			await deleteComplianceBatch(batch.id);
			toast.success("合规批次已删除");
			if (currentBatch?.id === batch.id) {
				setCurrentBatch(null);
				setDetailOpen(false);
			}
			await loadBatches();
		} catch (error: any) {
			toast.error(parseWriteError(error, "删除合规批次失败"));
		} finally {
			setBatchActionId("");
		}
	};

	const openItemEdit = (item: ComplianceItem) => {
		setEditingItem(item);
		itemForm.setFieldsValue({
			status: item.status || "PENDING",
			conclusion: item.conclusion || "",
			evidenceRef: item.evidenceRef || "",
		});
		setItemModalOpen(true);
	};

	const saveItem = async () => {
		if (!editingItem?.id) return;
		try {
			const values = await itemForm.validateFields();
			setItemSaving(true);
			await updateComplianceItem(editingItem.id, values);
			toast.success("合规项已更新");
			setItemModalOpen(false);
			await loadBatchDetail(currentBatch?.id);
			await loadBatches();
		} catch (error: any) {
			if (error?.errorFields) return;
			toast.error(parseWriteError(error, "合规项更新失败"));
		} finally {
			setItemSaving(false);
		}
	};

	const batchColumns: ColumnsType<ComplianceBatch> = [
		{ title: "批次名称", dataIndex: "name", render: (value) => value || "-" , sorter: (a, b) => (a.name || "").localeCompare(b.name || "") },
		{
			title: "状态",
			dataIndex: "status",
			width: 120,
			render: (value) => <Tag color={statusColor(value)}>{normalizeStatus(value) || "-"}</Tag>,
		},
		{
			title: "进度",
			dataIndex: "progressPct",
			width: 180,
			render: (value) => <Progress percent={Number(value || 0)} size="small" />,
		},
		{
			title: "结果统计",
			width: 200,
			render: (_, record) =>
				`通过 ${record.passedItems || 0} / 失败 ${record.failedItems || 0} / 待处理 ${record.pendingItems || 0}`,
		},
		{
			title: "触发信息",
			width: 180,
			render: (_, record) => `${record.triggeredBy || "-"} / ${record.ownerDept || "-"}`,
		},
		{
			title: "最近更新时间",
			dataIndex: "lastUpdated",
			width: 200,
			render: (value) => value || "-",
		},
		{
			title: "操作",
			width: 240,
			render: (_, record) => {
				const busy = batchActionId === record.id;
				return (
					<Space wrap>
						<Button
							size="small"
							onClick={async () => {
								setDetailOpen(true);
								await loadBatchDetail(record.id);
							}}
						>
							查看详情
						</Button>
						<Popconfirm
							title="确认删除该合规批次？"
							okText="删除"
							cancelText="取消"
							onConfirm={() => handleDeleteBatch(record)}
							disabled={!canManage}
						>
							<Button size="small" danger disabled={!canManage} loading={busy}>
								删除
							</Button>
						</Popconfirm>
					</Space>
				);
			},
		},
	];

	const itemColumns: ColumnsType<ComplianceItem> = [
		{ title: "规则", render: (_, item) => item.ruleName || item.ruleCode || "-" },
		{
			title: "状态",
			dataIndex: "status",
			width: 120,
			render: (value) => <Tag color={statusColor(value)}>{normalizeStatus(value) || "-"}</Tag>,
		},
		{
			title: "质量执行",
			width: 140,
			render: (_, item) => <Tag color={statusColor(item.qualityRunStatus)}>{normalizeStatus(item.qualityRunStatus) || "-"}</Tag>,
		},
		{ title: "数据集", dataIndex: "datasetAlias", render: (value) => value || "-" },
		{ title: "结论", dataIndex: "conclusion", render: (value) => value || "-" },
		{
			title: "证据链接",
			dataIndex: "evidenceRef",
			render: (value) => (value ? <a href={value} target="_blank" rel="noreferrer">{value}</a> : "-"),
		},
		{
			title: "操作",
			width: 120,
			render: (_, item) => (
				<Button size="small" disabled={!canManage} onClick={() => openItemEdit(item)}>
					更新
				</Button>
			),
		},
	];

	return (
		<Card
			title="合规检查中心"
			extra={
				<Space>
					<Select
						value={statusFilter}
						onChange={(value) => setStatusFilter(value)}
						options={[
							{ label: "全部状态", value: "ALL" },
							{ label: "进行中", value: "RUNNING" },
							{ label: "已完成", value: "COMPLETED" },
							{ label: "失败", value: "FAILED" },
							{ label: "草稿", value: "DRAFT" },
						]}
						style={{ width: 140 }}
					/>
					<Button onClick={() => void loadBatches()}>
						刷新
					</Button>
					<Button type="primary" disabled={!canManage} onClick={openCreate}>
						新建批次
					</Button>
				</Space>
			}
		>
			<Space direction="vertical" className="w-full" size={12}>
				<Alert
					type={canManage ? "info" : "warning"}
					showIcon
					message={
						canManage
							? `当前部门上下文：${activeDept || "未设置"}，可执行创建/审核操作。`
							: "当前账号为只读模式，仅可查看合规批次。"
					}
				/>
				<CompactTable rowKey={(record) => record.id || record.name || "batch"} columns={batchColumns} dataSource={batches} loading={loading} />
			</Space>

			<Modal
				open={createOpen}
				title="新建合规批次"
				onCancel={() => setCreateOpen(false)}
				onOk={saveBatch}
				okText="创建"
				confirmLoading={saving}
				destroyOnClose
				width={760}
			>
				<Form form={batchForm} layout="vertical" initialValues={{ templateCode: "DEFAULT", dataLevel: "DATA_INTERNAL", evidenceRequired: true }}>
					<Form.Item label="批次名称" name="name" rules={[{ required: true, message: "请输入批次名称" }]}>
						<Input placeholder="例如：周度质量合规检查" />
					</Form.Item>
					<Form.Item label="模板编码" name="templateCode">
						<Input placeholder="DEFAULT" />
					</Form.Item>
					<Form.Item label="数据密级" name="dataLevel">
						<Select
							options={[
								{ label: "公开", value: "DATA_PUBLIC" },
								{ label: "内部", value: "DATA_INTERNAL" },
								{ label: "秘密", value: "DATA_SECRET" },
								{ label: "机密", value: "DATA_CONFIDENTIAL" },
								{ label: "绝密", value: "DATA_TOP_SECRET" },
							]}
						/>
					</Form.Item>
					<Form.Item label="规则范围" name="ruleIds" rules={[{ required: true, message: "请选择至少一个规则" }]}>
						<Select mode="multiple" options={ruleOptions} placeholder="选择质量规则" />
					</Form.Item>
					<Form.Item label="归属部门(可选)" name="ownerDept">
						<Input placeholder="默认按当前上下文处理" />
					</Form.Item>
					<Form.Item label="要求证据" name="evidenceRequired" valuePropName="checked">
						<Switch />
					</Form.Item>
				</Form>
			</Modal>

			<Modal
				open={detailOpen}
				title={`合规批次详情：${currentBatch?.name || "-"}`}
				onCancel={() => setDetailOpen(false)}
				footer={null}
				width={1200}
			>
				<Space direction="vertical" className="w-full" size={12}>
					<Alert
						type={(currentBatch?.failedItems || 0) > 0 ? "warning" : "success"}
						showIcon
						message={`总项 ${currentBatch?.totalItems || 0}，通过 ${currentBatch?.passedItems || 0}，失败 ${
							currentBatch?.failedItems || 0
						}，待处理 ${currentBatch?.pendingItems || 0}`}
					/>
					<CompactTable
						rowKey={(item) => item.id || item.ruleCode || Math.random().toString()}
						columns={itemColumns}
						dataSource={Array.isArray(currentBatch?.items) ? currentBatch?.items : []}
						pagination={{ pageSize: 8 }}
					/>
				</Space>
			</Modal>

			<Modal
				open={itemModalOpen}
				title="更新合规检查项"
				onCancel={() => setItemModalOpen(false)}
				onOk={saveItem}
				okText="保存"
				confirmLoading={itemSaving}
				destroyOnClose
			>
				<Form form={itemForm} layout="vertical">
					<Form.Item label="状态" name="status">
						<Select
							options={[
								{ label: "待处理", value: "PENDING" },
								{ label: "通过", value: "PASSED" },
								{ label: "失败", value: "FAILED" },
								{ label: "已豁免", value: "WAIVED" },
							]}
						/>
					</Form.Item>
					<Form.Item label="结论" name="conclusion">
						<Input.TextArea rows={4} placeholder="请输入审查结论" />
					</Form.Item>
					<Form.Item label="证据链接" name="evidenceRef">
						<Input placeholder="http://... 或对象存储地址" />
					</Form.Item>
				</Form>
			</Modal>
		</Card>
	);
}
