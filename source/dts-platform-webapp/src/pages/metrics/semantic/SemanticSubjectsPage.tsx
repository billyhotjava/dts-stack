import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, Col, Empty, Form, Input, Modal, Row, Select, Space, Tag, Typography, message } from "antd";
import { CompactTable, RecordDetailDrawer, appendDetailAction } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { DatabaseOutlined, EditOutlined, ProjectOutlined, ReloadOutlined } from "@ant-design/icons";
import { PageHeader } from "@/components/page-header";
import { getDomainTree, listDatasets } from "@/api/platformApi";
import {
	createSemanticSubjectDomain,
	listSemanticSubjectDomains,
	type SemanticSubjectDomain,
	updateSemanticSubjectDomain,
} from "@/api/semanticModelingApi";
import { SemanticSectionNav } from "./SemanticSectionNav";
import { asArray, isDwdSemanticInput, semanticSectionMeta } from "./semanticModelingShared";

const { Text } = Typography;

type DatasetOption = {
	id: string;
	name: string;
	table?: string;
	layer?: string;
	database?: string;
	schema?: string;
};

type GovernanceDomainOption = {
	id: string;
	code?: string;
	name: string;
	label: string;
};

const flattenGovernanceDomains = (nodes: any[], path: string[] = [], out: GovernanceDomainOption[] = []) => {
	nodes.forEach((node) => {
		if (!node || typeof node !== "object") return;
		const id = String(node.id || node.key || "");
		const name = String(node.name || "");
		if (!id || !name) return;
		const code = node.code ? String(node.code) : undefined;
		const nextPath = [...path, name];
		out.push({
			id,
			code,
			name,
			label: nextPath.join(" / "),
		});
		if (Array.isArray(node.children) && node.children.length) {
			flattenGovernanceDomains(node.children, nextPath, out);
		}
	});
	return out;
};

const normalizeDataset = (item: any): DatasetOption => ({
	id: String(item.id || item.key || item.name || item.tableName || item.hiveTable),
	name: String(item.name || item.displayName || item.hiveTable || item.tableName || item.id || ""),
	table: String(item.hiveTable || item.tableName || item.name || ""),
	layer: String(item.warehouseLayer || item.layer || ""),
	database: String(item.databaseName || item.database || ""),
	schema: String(item.schemaName || item.schema || ""),
});

export default function SemanticSubjectsPage() {
	const [form] = Form.useForm();
	const [domains, setDomains] = useState<SemanticSubjectDomain[]>([]);
	const [governanceDomains, setGovernanceDomains] = useState<GovernanceDomainOption[]>([]);
	const [datasets, setDatasets] = useState<DatasetOption[]>([]);
	const [domainsLoading, setDomainsLoading] = useState(false);
	const [governanceLoading, setGovernanceLoading] = useState(false);
	const [datasetsLoading, setDatasetsLoading] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [saving, setSaving] = useState(false);
	const [editingDomain, setEditingDomain] = useState<SemanticSubjectDomain | null>(null);
	const [detailRow, setDetailRow] = useState<SemanticSubjectDomain | null>(null);

	const loadDomains = () => {
		setDomainsLoading(true);
		listSemanticSubjectDomains()
			.then((resp) => setDomains(asArray<SemanticSubjectDomain>(resp)))
			.catch(() => setDomains([]))
			.finally(() => setDomainsLoading(false));
	};

	const loadGovernanceDomains = () => {
		setGovernanceLoading(true);
		getDomainTree()
			.then((resp: any) => setGovernanceDomains(flattenGovernanceDomains(Array.isArray(resp) ? resp : [])))
			.catch(() => setGovernanceDomains([]))
			.finally(() => setGovernanceLoading(false));
	};

	const loadDatasets = () => {
		setDatasetsLoading(true);
		listDatasets({ page: 0, size: 120 })
			.then((resp: any) => setDatasets(asArray<any>(resp).map(normalizeDataset)))
			.catch(() => setDatasets([]))
			.finally(() => setDatasetsLoading(false));
	};

	useEffect(() => {
		loadDomains();
		loadGovernanceDomains();
		loadDatasets();
	}, []);

	const dwdDatasets = useMemo(
		() => datasets.filter(isDwdSemanticInput),
		[datasets],
	);

	const domainBaseColumns: ColumnsType<SemanticSubjectDomain> = [
		{ title: "名称", dataIndex: "name" },
		{ title: "编码", dataIndex: "code", render: (value) => value || "-" },
		{
			title: "来源",
			dataIndex: "governanceDomainId",
			width: 160,
			render: (_, row) =>
				row.governanceDomainId ? (
					<Tag color="blue">{row.governanceDomainName || row.governanceDomainCode || "治理主题域"}</Tag>
				) : (
					<Tag>开发自建</Tag>
				),
		},
		{ title: "状态", dataIndex: "status", width: 100, render: (value) => value || "-" },
		{ title: "说明", dataIndex: "description", ellipsis: true, render: (value) => value || "-" },
		{
			title: "操作",
			dataIndex: "actions",
			width: 160,
			fixed: "right",
			render: (_, row) => (
				<Button size="small" icon={<EditOutlined />} onClick={() => openModal(row)}>
					编辑
				</Button>
			),
		},
	];

	const domainColumns = useMemo(
		() => appendDetailAction(domainBaseColumns, (row) => setDetailRow(row)),
		// eslint-disable-next-line react-hooks/exhaustive-deps
		[],
	);

	const datasetColumns: ColumnsType<DatasetOption> = [
		{ title: "明细模型", dataIndex: "table", render: (value, row) => value || row.name },
		{ title: "库", dataIndex: "database", width: 140, render: (value) => value || "-" },
		{ title: "Schema", dataIndex: "schema", width: 140, render: (value) => value || "-" },
		{ title: "分层", dataIndex: "layer", width: 100, render: (value) => value || "DWD" },
	];

	const openModal = (domain?: SemanticSubjectDomain) => {
		form.resetFields();
		setEditingDomain(domain || null);
		if (domain) {
			form.setFieldsValue({
				governanceDomainId: domain.governanceDomainId,
				code: domain.code,
				name: domain.name,
				description: domain.description,
			});
		}
		setModalOpen(true);
	};

	const closeModal = () => {
		setModalOpen(false);
		setEditingDomain(null);
		form.resetFields();
	};

	const submitDomain = async () => {
		const values = await form.validateFields();
		if (values.governanceDomainId) {
			const governanceDomain = governanceDomains.find((item) => item.id === values.governanceDomainId);
			values.governanceDomainCode = governanceDomain?.code;
			values.governanceDomainName = governanceDomain?.name;
		}
		setSaving(true);
		try {
			if (editingDomain?.id) {
				await updateSemanticSubjectDomain(editingDomain.id, values);
			} else {
				await createSemanticSubjectDomain(values);
			}
			message.success("主题域已保存");
			closeModal();
			loadDomains();
		} finally {
			setSaving(false);
		}
	};

	const refreshAll = () => {
		loadDomains();
		loadGovernanceDomains();
		loadDatasets();
	};

	return (
		<div className="space-y-5 p-5" data-testid="semantic-subjects-page">
			<PageHeader
				title={semanticSectionMeta.subjects.title}
				actions={(
					<Space wrap>
						<Button icon={<ReloadOutlined />} onClick={refreshAll}>刷新</Button>
						<Button type="primary" icon={<ProjectOutlined />} onClick={() => openModal()}>新建主题域</Button>
					</Space>
				)}
			/>

			<SemanticSectionNav activeSection="subjects" />

			<Alert
				type="info"
				showIcon
				message="主题域引用约束"
				description="优先引用治理中心已经沉淀的主题域，便于指标口径、资产目录和血缘后续对齐；没有治理前置时可以直接自建主题域，不阻塞熟悉 SQL/dbt 的工程师继续建模。"
			/>

			<Row gutter={[16, 16]}>
				<Col xs={24} xl={10}>
					<Card
						title="业务主题域"
						extra={<Tag color={domains.length ? "green" : "default"}>{domains.length ? "已接 API" : "暂无数据"}</Tag>}
					>
						{domains.length || domainsLoading ? (
							<CompactTable<SemanticSubjectDomain>
								rowKey="id"
								size="small"
								loading={domainsLoading}
								pagination={{ pageSize: 8 }}
								columns={domainColumns}
								dataSource={domains}
							/>
						) : (
							<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无语义主题域" />
						)}
					</Card>
				</Col>

				<Col xs={24} xl={14}>
					<Card
						title="可用于建模的 DWD 明细模型"
						extra={(
							<Space>
								<DatabaseOutlined />
								<Text type="secondary">{dwdDatasets.length} 个</Text>
							</Space>
						)}
					>
						{dwdDatasets.length || datasetsLoading ? (
							<CompactTable<DatasetOption>
								rowKey="id"
								size="small"
								loading={datasetsLoading}
								pagination={{ pageSize: 8 }}
								columns={datasetColumns}
								dataSource={dwdDatasets}
							/>
						) : (
							<Empty
								image={Empty.PRESENTED_IMAGE_SIMPLE}
								description="资产目录暂无 DWD 明细模型"
							/>
						)}
					</Card>
				</Col>
			</Row>

			<Modal
				open={modalOpen}
				title={editingDomain ? "编辑主题域" : "新建主题域"}
				onCancel={closeModal}
				onOk={submitDomain}
				confirmLoading={saving}
				destroyOnClose
			>
				<Form form={form} layout="vertical">
					<Alert
						type="info"
						showIcon
						className="mb-4"
						message="治理主题域为可选引用"
						description="有治理主题域时建议选择，便于后续口径、资产和血缘对齐；没有治理前置工作时可以留空。"
					/>
					<Form.Item name="governanceDomainId" label="治理主题域（可选）">
						<SelectGovernanceDomain
							loading={governanceLoading}
							options={governanceDomains}
							onSelectDomain={(domain) => {
								const current = form.getFieldsValue(["code", "name"]);
								form.setFieldsValue({
									code: current.code || domain.code,
									name: current.name || domain.name,
								});
							}}
						/>
					</Form.Item>
					<Form.Item name="code" label="编码" rules={[{ required: true, message: "请输入编码" }]}>
						<Input placeholder="唯一编码" />
					</Form.Item>
					<Form.Item name="name" label="名称" rules={[{ required: true, message: "请输入名称" }]}>
						<Input placeholder="业务名称" />
					</Form.Item>
					<Form.Item name="description" label="说明">
						<Input.TextArea rows={3} />
					</Form.Item>
				</Form>
			</Modal>
			<RecordDetailDrawer<SemanticSubjectDomain>
				open={detailRow !== null}
				onClose={() => setDetailRow(null)}
				record={detailRow}
				columns={domainBaseColumns}
				title="主题域详情"
			/>
		</div>
	);
}

function SelectGovernanceDomain({
	loading,
	options,
	onSelectDomain,
}: {
	loading: boolean;
	options: GovernanceDomainOption[];
	onSelectDomain: (domain: GovernanceDomainOption) => void;
}) {
	return (
		<Select
			allowClear
			showSearch
			loading={loading}
			placeholder="可从治理中心主题域带入，也可以留空自建"
			optionFilterProp="label"
			options={options.map((item) => ({
				label: item.code ? `${item.label}（${item.code}）` : item.label,
				value: item.id,
			}))}
			onChange={(value) => {
				const selected = options.find((item) => item.id === value);
				if (selected) onSelectDomain(selected);
			}}
		/>
	);
}
