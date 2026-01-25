import { useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Alert,
	Button,
	Card,
	Col,
	Descriptions,
	Form,
	Input,
	Modal,
	Row,
	Select,
	Space,
	Table,
	Tag,
	Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { PageHeader } from "@/components/page-header";

const { Text } = Typography;

type ChangeRecord = {
	id: string;
	time: string;
	objType: "DATASOURCE" | "INGEST_JOB";
	obj: string;
	type: "CONN_PARAM" | "SCHEMA_NEW_COL" | "SCHEMA_TYPE_CHANGE" | "CATALOG_CHANGE";
	summary: string;
	risk: "L" | "M" | "H";
	status: "已完成" | "待处理" | "待审批";
	detail?: string;
};

const initialChanges: ChangeRecord[] = [];

const typeLabel = (value: ChangeRecord["type"]) => {
	const map: Record<ChangeRecord["type"], string> = {
		CONN_PARAM: "连接参数变更",
		SCHEMA_NEW_COL: "新增字段",
		SCHEMA_TYPE_CHANGE: "字段类型变更",
		CATALOG_CHANGE: "同步范围变更",
	};
	return map[value] || value;
};

const riskTag = (risk: ChangeRecord["risk"]) => {
	if (risk === "L") return <Tag color="green">低</Tag>;
	if (risk === "M") return <Tag color="orange">中</Tag>;
	return <Tag color="red">高</Tag>;
};

const statusTag = (status: ChangeRecord["status"]) => {
	if (status === "已完成") return <Tag color="green">已完成</Tag>;
	if (status === "待审批") return <Tag color="gold">待审批</Tag>;
	return <Tag color="blue">待处理</Tag>;
};

export default function AccessChangesPage() {
	const [changes, setChanges] = useState<ChangeRecord[]>(initialChanges);
	const [objType, setObjType] = useState("ALL");
	const [changeType, setChangeType] = useState("ALL");
	const [keyword, setKeyword] = useState("");
	const [selected, setSelected] = useState<ChangeRecord | null>(null);
	const [modalOpen, setModalOpen] = useState(false);
	const [form] = Form.useForm();

	const filteredChanges = useMemo(() => {
		const key = keyword.trim().toLowerCase();
		return changes.filter((item) => {
			if (objType !== "ALL" && item.objType !== objType) return false;
			if (changeType !== "ALL" && item.type !== changeType) return false;
			if (!key) return true;
			return `${item.obj} ${item.summary} ${item.detail || ""}`.toLowerCase().includes(key);
		});
	}, [changeType, changes, keyword, objType]);

	const columns: ColumnsType<ChangeRecord> = [
		{ title: "时间", dataIndex: "time" },
		{ title: "对象", dataIndex: "obj" },
		{ title: "类型", dataIndex: "type", render: (value) => typeLabel(value) },
		{ title: "摘要", dataIndex: "summary", render: (value) => value || "-" },
		{ title: "风险等级", dataIndex: "risk", render: (value) => riskTag(value) },
		{ title: "状态", dataIndex: "status", render: (value) => statusTag(value) },
		{
			title: "操作",
			render: (_, row) => (
				<Button type="link" onClick={() => setSelected(row)}>
					查看
				</Button>
			),
		},
	];

	const openModal = () => {
		form.resetFields();
		setModalOpen(true);
	};

	const submitChange = async () => {
		try {
			const values = await form.validateFields();
			const newItem: ChangeRecord = {
				id: `c_${Math.random().toString(36).slice(2, 6)}`,
				time: new Date().toISOString().slice(0, 16).replace("T", " "),
				objType: values.objType,
				obj: values.obj,
				type: values.type,
				summary: values.summary,
				risk: values.risk,
				status: "待处理",
				detail: values.detail,
			};
			setChanges([newItem, ...changes]);
			setModalOpen(false);
			toast.success("变更已登记");
		} catch {
			// Validation handled by form
		}
	};

	const impactView = selected ? (
		<Descriptions column={1} size="small" bordered>
			<Descriptions.Item label="变更对象">{selected.obj}</Descriptions.Item>
			<Descriptions.Item label="变更类型">{typeLabel(selected.type)}</Descriptions.Item>
			<Descriptions.Item label="风险等级">{riskTag(selected.risk)}</Descriptions.Item>
			<Descriptions.Item label="处理状态">{statusTag(selected.status)}</Descriptions.Item>
			<Descriptions.Item label="变更摘要">{selected.summary || "-"}</Descriptions.Item>
			<Descriptions.Item label="变更详情">{selected.detail || "-"}</Descriptions.Item>
		</Descriptions>
	) : (
		<Text type="secondary">请选择一条变更记录查看详情。</Text>
	);

	return (
		<div className="space-y-4">
			<PageHeader
				title="接入变更记录"
				description="统一记录连接与 Schema 变更，提供影响分析与处置闭环。"
				actions={
					<Space>
						<Button>刷新</Button>
						<Button type="primary" onClick={openModal}>
							登记变更
						</Button>
					</Space>
				}
			/>

			<Alert
				type="info"
				showIcon
				message="记录连接参数变更、Schema 变更与同步策略调整，形成影响分析与处置闭环。"
			/>

			<Card
				title="变更查询"
				extra={
					<Space>
						<Button>导出</Button>
						<Button type="primary" onClick={openModal}>
							登记变更
						</Button>
					</Space>
				}
			>
				<Row gutter={12} className="mb-4">
					<Col span={8}>
						<Select
							value={objType}
							onChange={setObjType}
							options={[
								{ label: "全部对象", value: "ALL" },
								{ label: "数据源连接", value: "DATASOURCE" },
								{ label: "入湖任务", value: "INGEST_JOB" },
							]}
						/>
					</Col>
					<Col span={8}>
						<Select
							value={changeType}
							onChange={setChangeType}
							options={[
								{ label: "全部类型", value: "ALL" },
								{ label: "连接参数变更", value: "CONN_PARAM" },
								{ label: "新增字段", value: "SCHEMA_NEW_COL" },
								{ label: "字段类型变更", value: "SCHEMA_TYPE_CHANGE" },
								{ label: "同步范围变更", value: "CATALOG_CHANGE" },
							]}
						/>
					</Col>
					<Col span={8}>
						<Input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="关键字" />
					</Col>
				</Row>
				<Table
					rowKey={(row) => row.id}
					columns={columns}
					dataSource={filteredChanges}
					pagination={{ pageSize: 6 }}
					onRow={(row) => ({
						onClick: () => setSelected(row),
					})}
				/>
			</Card>

			<Card title="影响分析与处置">{impactView}</Card>

			<Modal
				open={modalOpen}
				title="登记变更"
				onCancel={() => setModalOpen(false)}
				onOk={submitChange}
				okText="提交"
				cancelText="取消"
			>
				<Form form={form} layout="vertical">
					<Row gutter={12}>
						<Col span={12}>
							<Form.Item name="objType" label="对象类型" rules={[{ required: true, message: "请选择对象类型" }]}>
								<Select
									options={[
										{ label: "数据源连接", value: "DATASOURCE" },
										{ label: "入湖任务", value: "INGEST_JOB" },
									]}
								/>
							</Form.Item>
						</Col>
						<Col span={12}>
							<Form.Item name="obj" label="对象标识" rules={[{ required: true, message: "请输入对象标识" }]}>
								<Input placeholder="例如：ds_001 / ij_001" />
							</Form.Item>
						</Col>
					</Row>
					<Row gutter={12}>
						<Col span={12}>
							<Form.Item name="type" label="变更类型" rules={[{ required: true, message: "请选择变更类型" }]}>
								<Select
									options={[
										{ label: "连接参数变更", value: "CONN_PARAM" },
										{ label: "新增字段", value: "SCHEMA_NEW_COL" },
										{ label: "字段类型变更", value: "SCHEMA_TYPE_CHANGE" },
										{ label: "同步范围变更", value: "CATALOG_CHANGE" },
									]}
								/>
							</Form.Item>
						</Col>
						<Col span={12}>
							<Form.Item name="risk" label="风险等级" rules={[{ required: true, message: "请选择风险等级" }]}>
								<Select
									options={[
										{ label: "低", value: "L" },
										{ label: "中", value: "M" },
										{ label: "高", value: "H" },
									]}
								/>
							</Form.Item>
						</Col>
					</Row>
					<Form.Item name="summary" label="变更摘要" rules={[{ required: true, message: "请输入摘要" }]}>
						<Input placeholder="例如：新增字段 discount_amt" />
					</Form.Item>
					<Form.Item name="detail" label="变更详情">
						<Input.TextArea rows={4} placeholder="可粘贴 diff 或影响说明" />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
