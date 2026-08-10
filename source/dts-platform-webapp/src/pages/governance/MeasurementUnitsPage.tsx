import {
	Button,
	Card,
	Descriptions,
	Empty,
	Form,
	Input,
	InputNumber,
	List,
	Modal,
	Select,
	Space,
	Tag,
	Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { toast } from "sonner";
import {
	createMeasurementUnit,
	deactivateMeasurementUnit,
	getMeasurementUnitReferences,
	listMeasurementUnits,
	listMeasurementUnitVersions,
	type MeasurementUnitCommand,
	type MeasurementUnitReferenceImpact,
	type MeasurementUnitView,
	updateMeasurementUnit,
} from "@/api/platformApi";
import { CompactTable } from "@/components/table";
import { useGovernanceManageAccess } from "@/hooks/useModuleManageAccess";

const { Text, Title } = Typography;

const safeInternalPath = (value: string | null) => {
	const path = String(value || "").trim();
	return path.startsWith("/") && !path.startsWith("//") ? path : "";
};

export default function MeasurementUnitsPage() {
	const navigate = useNavigate();
	const [searchParams] = useSearchParams();
	const canManage = useGovernanceManageAccess();
	const [form] = Form.useForm<MeasurementUnitCommand>();
	const [units, setUnits] = useState<MeasurementUnitView[]>([]);
	const [loading, setLoading] = useState(false);
	const [saving, setSaving] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [editing, setEditing] = useState<MeasurementUnitView | null>(null);
	const [history, setHistory] = useState<MeasurementUnitView[]>([]);
	const [historyOpen, setHistoryOpen] = useState(false);
	const [references, setReferences] = useState<MeasurementUnitReferenceImpact | null>(null);
	const [referencesOpen, setReferencesOpen] = useState(false);
	const returnTo = safeInternalPath(searchParams.get("returnTo"));
	const modelSpecId = String(searchParams.get("modelSpecId") || "").trim();
	const returnPath =
		returnTo ||
		(modelSpecId
			? `/data-modeling/dimensions/workbench?modelSpecId=${encodeURIComponent(modelSpecId)}&tab=standards`
			: "");

	const load = useCallback(async () => {
		setLoading(true);
		try {
			const result = await listMeasurementUnits();
			setUnits(Array.isArray(result) ? result : []);
		} catch (error: any) {
			setUnits([]);
			toast.error(error?.message || "计量单位加载失败");
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => void load(), [load]);

	const openEditor = (unit?: MeasurementUnitView) => {
		setEditing(unit || null);
		form.setFieldsValue(
			unit
				? {
						code: unit.code,
						name: unit.name,
						symbol: unit.symbol,
						quantityKind: unit.quantityKind,
						conversionFactor: Number(unit.conversionFactor),
						baseUnitRef: unit.baseUnitRef || null,
						precision: unit.precision,
					}
				: { conversionFactor: 1, precision: 2 },
		);
		setModalOpen(true);
	};

	const save = async () => {
		const values = await form.validateFields();
		setSaving(true);
		try {
			if (editing) await updateMeasurementUnit(editing, values);
			else await createMeasurementUnit(values);
			toast.success(editing ? "计量单位已生成新版本" : "计量单位已创建");
			setModalOpen(false);
			form.resetFields();
			await load();
		} catch (error: any) {
			toast.error(error?.message || "计量单位保存失败");
		} finally {
			setSaving(false);
		}
	};

	const openHistory = async (unit: MeasurementUnitView) => {
		try {
			setHistory(await listMeasurementUnitVersions(unit.id));
			setHistoryOpen(true);
		} catch (error: any) {
			toast.error(error?.message || "版本历史加载失败");
		}
	};

	const openReferences = async (unit: MeasurementUnitView) => {
		try {
			setReferences(await getMeasurementUnitReferences(unit.id));
			setReferencesOpen(true);
		} catch (error: any) {
			toast.error(error?.message || "引用影响加载失败");
		}
	};

	const deactivate = (unit: MeasurementUnitView) => {
		Modal.confirm({
			title: `停用 ${unit.name}？`,
			content: "已保存的模型引用会显示为过期，不会被静默改写。",
			onOk: async () => {
				await deactivateMeasurementUnit(unit);
				toast.success("计量单位已停用");
				await load();
			},
		});
	};

	const activeOptions = useMemo(
		() =>
			units
				.filter((unit) => unit.status === "ACTIVE")
				.map((unit) => ({ value: unit.id, label: `${unit.name} (${unit.symbol}) · v${unit.version}` })),
		[units],
	);
	const columns: ColumnsType<MeasurementUnitView> = [
		{ title: "编码", dataIndex: "code", width: 150 },
		{ title: "名称", dataIndex: "name" },
		{ title: "符号", dataIndex: "symbol", width: 100 },
		{ title: "量纲", dataIndex: "quantityKind", width: 150 },
		{ title: "换算因子", dataIndex: "conversionFactor", width: 120 },
		{ title: "精度", dataIndex: "precision", width: 80 },
		{ title: "版本", dataIndex: "version", width: 80, render: (value) => `v${value}` },
		{
			title: "状态",
			dataIndex: "status",
			width: 100,
			render: (value) => (
				<Tag color={value === "ACTIVE" ? "green" : "default"}>{value === "ACTIVE" ? "启用" : "停用"}</Tag>
			),
		},
		{
			title: "操作",
			width: 270,
			render: (_, unit) => (
				<Space wrap>
					<Button size="small" onClick={() => void openHistory(unit)}>
						版本
					</Button>
					<Button size="small" onClick={() => void openReferences(unit)}>
						引用
					</Button>
					<Button size="small" disabled={!canManage || unit.status !== "ACTIVE"} onClick={() => openEditor(unit)}>
						编辑
					</Button>
					<Button
						size="small"
						danger
						disabled={!canManage || unit.status !== "ACTIVE"}
						onClick={() => deactivate(unit)}
					>
						停用
					</Button>
				</Space>
			),
		},
	];

	return (
		<div className="p-5" data-testid="measurement-units-page">
			<div className="mb-4 flex flex-wrap items-start justify-between gap-3">
				<div>
					<Title level={3} className="!mb-1">
						计量单位
					</Title>
					<Text type="secondary">统一维护稳定 ID、不可变版本、换算关系与引用影响；模型只保存版本化引用。</Text>
				</div>
				<Space>
					{returnPath ? <Button onClick={() => navigate(returnPath)}>返回模型标准绑定</Button> : null}
					<Button onClick={() => void load()} loading={loading}>
						刷新
					</Button>
					<Button type="primary" disabled={!canManage} onClick={() => openEditor()}>
						新建计量单位
					</Button>
				</Space>
			</div>
			<Card>
				<CompactTable
					rowKey="id"
					loading={loading}
					columns={columns}
					dataSource={units}
					pagination={{ pageSize: 20 }}
				/>
			</Card>

			<Modal
				title={editing ? "编辑计量单位" : "新建计量单位"}
				open={modalOpen}
				onCancel={() => setModalOpen(false)}
				onOk={() => void save()}
				confirmLoading={saving}
				destroyOnClose
			>
				<Form form={form} layout="vertical" preserve={false}>
					<Form.Item name="code" label="稳定编码" rules={[{ required: true }]}>
						<Input disabled={Boolean(editing)} placeholder="例如 CNY" />
					</Form.Item>
					<Form.Item name="name" label="名称" rules={[{ required: true }]}>
						<Input />
					</Form.Item>
					<Form.Item name="symbol" label="符号" rules={[{ required: true }]}>
						<Input />
					</Form.Item>
					<Form.Item name="quantityKind" label="量纲编码" rules={[{ required: true }]}>
						<Input placeholder="例如 CURRENCY" />
					</Form.Item>
					<div className="grid grid-cols-2 gap-3">
						<Form.Item name="conversionFactor" label="相对基准换算因子" rules={[{ required: true }]}>
							<InputNumber min={0.000000000000000001} className="w-full" />
						</Form.Item>
						<Form.Item name="precision" label="小数精度" rules={[{ required: true }]}>
							<InputNumber min={0} max={18} className="w-full" />
						</Form.Item>
					</div>
					<Form.Item name="baseUnitRef" label="基准单位">
						<Select
							allowClear
							showSearch
							optionFilterProp="label"
							options={activeOptions.filter((item) => item.value !== editing?.id)}
						/>
					</Form.Item>
				</Form>
			</Modal>

			<Modal title="版本历史" open={historyOpen} onCancel={() => setHistoryOpen(false)} footer={null} width={760}>
				<CompactTable
					rowKey={(row) => `${row.id}-${row.version}`}
					dataSource={history}
					pagination={false}
					columns={columns.slice(0, 8)}
				/>
			</Modal>
			<Modal title="引用影响" open={referencesOpen} onCancel={() => setReferencesOpen(false)} footer={null} width={720}>
				<Descriptions size="small" column={2} className="mb-3">
					<Descriptions.Item label="总引用">{references?.totalReferences ?? 0}</Descriptions.Item>
					<Descriptions.Item label="受限引用">{references?.restrictedReferences ?? 0}</Descriptions.Item>
				</Descriptions>
				{references?.items.length ? (
					<List
						dataSource={references.items}
						renderItem={(item) => (
							<List.Item
								actions={
									item.repairRoute && !item.restricted
										? [
												<Button key="repair" type="link" onClick={() => item.repairRoute && navigate(item.repairRoute)}>
													修复
												</Button>,
											]
										: []
								}
							>
								<List.Item.Meta
									title={item.restricted ? "受限资源" : item.displayName}
									description={`${item.resourceType} · 引用 v${item.referencedVersion ?? "?"} / 当前 v${item.currentVersion ?? "?"}`}
								/>
								<Tag
									color={item.driftStatus === "CURRENT" ? "green" : item.driftStatus === "STALE" ? "red" : "default"}
								>
									{item.driftStatus}
								</Tag>
							</List.Item>
						)}
					/>
				) : (
					<Empty description="暂无引用" />
				)}
			</Modal>
		</div>
	);
}
