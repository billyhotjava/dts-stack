import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Alert,
	Button,
	Card,
	Form,
	Input,
	Modal,
	Select,
	Space,
	Tag,
} from "antd";
import { CompactTable, RecordDetailDrawer, appendDetailAction } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { } from "@ant-design/icons";
import { PageHeader } from "@/components/page-header";
import { JourneyContextBar } from "@/components/journey";
import apiServicesService, {
	type ApiServiceSummary,
	type ApiServiceUpsert,
} from "@/api/services/apiServicesService";
import { listDatasets } from "@/api/platformApi";

const METHOD_OPTIONS = [
	{ label: "GET", value: "GET" },
	{ label: "POST", value: "POST" },
	{ label: "PUT", value: "PUT" },
	{ label: "DELETE", value: "DELETE" },
];

const CLASSIFICATION_OPTIONS = [
	{ label: "PUBLIC", value: "PUBLIC" },
	{ label: "INTERNAL", value: "INTERNAL" },
	{ label: "SECRET", value: "SECRET" },
	{ label: "CONFIDENTIAL", value: "CONFIDENTIAL" },
];

export default function Page() {
	const [services, setServices] = useState<ApiServiceSummary[]>([]);
	const [loading, setLoading] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [editing, setEditing] = useState<ApiServiceSummary | null>(null);
	const [form] = Form.useForm<ApiServiceUpsert>();
	const [datasets, setDatasets] = useState<{ id: string; name: string }[]>([]);
	const [testResult, setTestResult] = useState<any>(null);
	const [testModal, setTestModal] = useState(false);
	const [detailRow, setDetailRow] = useState<ApiServiceSummary | null>(null);

	const datasetOptions = useMemo(
		() => datasets.map((item) => ({ label: item.name, value: item.id })),
		[datasets],
	);

	const loadServices = async () => {
		setLoading(true);
		try {
			const list = await apiServicesService.list();
			setServices(Array.isArray(list) ? (list as ApiServiceSummary[]) : []);
		} catch {
			// global interceptor handles the error toast
		} finally {
			setLoading(false);
		}
	};

	const loadDatasets = async () => {
		try {
			const resp: any = await listDatasets({ page: 0, size: 200 });
			const list = Array.isArray(resp?.content) ? resp.content : [];
			setDatasets(list.map((item: any) => ({ id: String(item.id), name: item.name || item.id })));
		} catch {
			// global interceptor handles the error toast
		}
	};

	useEffect(() => {
		void loadServices();
		void loadDatasets();
	}, []);

	const openModal = (item?: ApiServiceSummary) => {
		setEditing(item || null);
		form.setFieldsValue({
			code: item?.code || "",
			name: item?.name || "",
			method: item?.method || "GET",
			path: item?.path || "",
			classification: item?.classification || "INTERNAL",
			datasetId: item?.datasetId || undefined,
			qpsLimit: item?.qpsLimit || undefined,
			dailyLimit: item?.dailyLimit || undefined,
			description: "",
		});
		setModalOpen(true);
	};

	const saveService = async () => {
		try {
			const values = await form.validateFields();
			if (editing?.id) {
				await apiServicesService.update(editing.id, values);
				toast.success("API 服务已更新");
			} else {
				await apiServicesService.create(values);
				toast.success("API 服务已新建");
			}
			setModalOpen(false);
			await loadServices();
		} catch (error: any) {
			if (error?.errorFields) return;
		}
	};

	const disableService = async (id?: string) => {
		if (!id) return;
		try {
			await apiServicesService.disable(id);
			toast.success("API 服务已下线");
			await loadServices();
		} catch {
			// global interceptor handles the error toast
		}
	};

	const tryInvoke = async (id?: string) => {
		if (!id) return;
		try {
			const result = await apiServicesService.tryInvoke(id, {});
			setTestResult(result);
			setTestModal(true);
		} catch {
			// global interceptor handles the error toast
		}
	};

	const baseColumns: ColumnsType<ApiServiceSummary> = [
		{ title: "名称", dataIndex: "name", render: (v) => v || "-" , sorter: (a, b) => (a.name || "").localeCompare(b.name || "") },
		{ title: "方法", dataIndex: "method", width: 90, render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "路径", dataIndex: "path", render: (v) => v || "-" },
		{ title: "分类", dataIndex: "classification", width: 120, render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "状态", dataIndex: "status", width: 120, render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "近7日调用", dataIndex: "recentCalls", width: 120, render: (v) => v ?? 0 },
		{
			title: "操作",
			dataIndex: "actions",
			width: 320,
			fixed: "right",
			render: (_, record) => (
				<Space>
					<Button size="small" onClick={() => tryInvoke(record.id)}>
						测试调用
					</Button>
					<Button size="small" disabled title="当前后端未开放启用接口，保存后按服务状态进入发布流程">
						启用
					</Button>
					<Button size="small" onClick={() => openModal(record)}>
						编辑
					</Button>
					<Button size="small" onClick={() => setDetailRow(record)}>
						查看调用
					</Button>
					<Button size="small" disabled title="审计流水接口尚未接入，先在服务详情中核对发布状态和调用指标">
						查看审计
					</Button>
					<Button size="small" danger onClick={() => disableService(record.id)}>
						下线
					</Button>
				</Space>
			),
		},
	];

	const columns = useMemo(
		() => appendDetailAction(baseColumns, (row) => setDetailRow(row)),
		// eslint-disable-next-line react-hooks/exhaustive-deps
		[],
	);

	return (
		<div className="space-y-6">
			<PageHeader
				title="数据服务中心 / 数据 API 管理"
				actions={
					<Button type="primary" onClick={() => openModal()}>
						新建 API
					</Button>
				}
			/>
			<JourneyContextBar stage="service" />
			<Card>
				<Alert
					className="mb-4"
					type="info"
					showIcon
					message="API 发布前需绑定数据集、密级、限流与调用验证；查看调用用于验收近 7 日服务表现。"
				/>
				<CompactTable rowKey={(record) => record.id} columns={columns} dataSource={services} loading={loading} />
			</Card>

			<Modal
				open={modalOpen}
				title={editing ? "编辑 API 服务" : "新建 API 服务"}
				onCancel={() => setModalOpen(false)}
				onOk={saveService}
				okText="保存"
				destroyOnClose
				width={720}
			>
				<Form form={form} layout="vertical">
					<Form.Item label="服务名称" name="name" rules={[{ required: true, message: "请输入名称" }]}>
						<Input />
					</Form.Item>
					<Form.Item label="服务编码" name="code" rules={[{ required: true, message: "请输入编码" }]}>
						<Input />
					</Form.Item>
					<Form.Item label="HTTP 方法" name="method">
						<Select options={METHOD_OPTIONS} />
					</Form.Item>
					<Form.Item label="路径" name="path" rules={[{ required: true, message: "请输入路径" }]}>
						<Input placeholder="例如 /api/orders" />
					</Form.Item>
					<Form.Item label="数据集" name="datasetId">
						<Select options={datasetOptions} allowClear />
					</Form.Item>
					<Form.Item label="密级" name="classification">
						<Select options={CLASSIFICATION_OPTIONS} />
					</Form.Item>
					<Form.Item label="QPS 限制" name="qpsLimit">
						<Input type="number" placeholder="可选" />
					</Form.Item>
					<Form.Item label="日调用限制" name="dailyLimit">
						<Input type="number" placeholder="可选" />
					</Form.Item>
					<Form.Item label="描述" name="description">
						<Input.TextArea rows={3} />
					</Form.Item>
				</Form>
			</Modal>

			<Modal
				open={testModal}
				onCancel={() => setTestModal(false)}
				footer={null}
				title="API 测试结果"
				width={720}
			>
				<pre className="whitespace-pre-wrap text-xs">{JSON.stringify(testResult, null, 2)}</pre>
			</Modal>
			<RecordDetailDrawer<ApiServiceSummary>
				open={detailRow !== null}
				onClose={() => setDetailRow(null)}
				record={detailRow}
				columns={baseColumns}
				title="API 服务详情"
			/>
		</div>
	);
}
