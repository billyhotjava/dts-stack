import { useEffect, useState } from "react";
import { toast } from "sonner";
import {
	Button,
	Card,
	Checkbox,
	Drawer,
	Empty,
	Form,
	Input,
	InputNumber,
	Modal,
	Select,
	Space,
	Steps,
	Typography,
} from "antd";
import { CompactTable } from "@/components/table";
import { } from "@ant-design/icons";
import {
	listIndicatorTemplates,
	getIndicatorTemplate,
	applyIndicatorTemplate,
} from "@/api/platformApi";
import type { DatasetField } from "@/api/platformApi";
import { DatasetPicker } from "@/components/catalog/DatasetPicker";

type Props = {
	open: boolean;
	template?: any;
	onClose: () => void;
	onSuccess: () => void;
};

type Blueprint = {
	code?: string;
	name?: string;
	aggregation?: string;
	description?: string;
	[key: string]: any;
};

export default function IndicatorWizard({ open, template, onClose, onSuccess }: Props) {
	const [step, setStep] = useState(0);
	const [submitting, setSubmitting] = useState(false);

	// Step 1: template & blueprint selection
	const [templates, setTemplates] = useState<any[]>([]);
	const [selectedTemplateId, setSelectedTemplateId] = useState<string | null>(null);
	const [blueprints, setBlueprints] = useState<Blueprint[]>([]);
	const [selectedBlueprints, setSelectedBlueprints] = useState<string[]>([]);
	const [requiredFields, setRequiredFields] = useState<string[]>([]);

	// Step 2: source table & field mapping
	const [selectedTable, setSelectedTable] = useState<string>("");
	const [selectedDatasetId, setSelectedDatasetId] = useState<string | undefined>();
	const [pickerFields, setPickerFields] = useState<DatasetField[]>([]);
	const [fieldMapping, setFieldMapping] = useState<Record<string, string>>({});

	// Step 3: parameters
	const [params, setParams] = useState<Record<string, any>>({});
	const [previewSql, setPreviewSql] = useState<string>("");
	const [previewOpen, setPreviewOpen] = useState(false);

	// Reset on open
	useEffect(() => {
		if (open) {
			setStep(0);
			setSelectedBlueprints([]);
			setFieldMapping({});
			setParams({});
			setPreviewSql("");
			setSelectedTable("");
			setSelectedDatasetId(undefined);
			setPickerFields([]);

			if (template) {
				setSelectedTemplateId(template.id);
				loadTemplateDetail(template.id);
			} else {
				setSelectedTemplateId(null);
				setBlueprints([]);
				setRequiredFields([]);
				fetchTemplates();
			}
		}
	}, [open, template]);

	const fetchTemplates = async () => {
		try {
			const res: any = await listIndicatorTemplates();
			setTemplates(Array.isArray(res) ? res : res?.data ?? []);
		} catch {
			// interceptor handles error
		}
	};

	const loadTemplateDetail = async (id: string) => {
		try {
			const res: any = await getIndicatorTemplate(id);
			const data = res?.data ?? res;
			const bp = parseJson(data.blueprintJson ?? data.blueprints) ?? [];
			setBlueprints(Array.isArray(bp) ? bp : []);
			const rf = parseJson(data.requiredFieldsJson ?? data.requiredFields) ?? [];
			setRequiredFields(Array.isArray(rf) ? rf : []);
		} catch {
			// interceptor handles error
		}
	};

	const handleSelectTemplate = async (tpl: any) => {
		setSelectedTemplateId(tpl.id);
		await loadTemplateDetail(tpl.id);
	};

	const handleBlueprintToggle = (code: string, checked: boolean) => {
		setSelectedBlueprints((prev) =>
			checked ? [...prev, code] : prev.filter((c) => c !== code),
		);
	};

	const handleSelectAllBlueprints = (checked: boolean) => {
		setSelectedBlueprints(checked ? blueprints.map((b) => b.code ?? "") : []);
	};

	const handlePreviewSql = async () => {
		// Build a temporary apply payload and preview
		try {
			const payload = buildPayload();
			// Use the first generated indicator for preview
			setPreviewSql("-- 正在生成 SQL 预览...");
			setPreviewOpen(true);
			const res: any = await applyIndicatorTemplate(selectedTemplateId!, {
				...payload,
				previewOnly: true,
			});
			const data = res?.data ?? res;
			const sql = typeof data === "string" ? data : (data?.sql ?? data?.previewSql ?? JSON.stringify(data, null, 2));
			setPreviewSql(sql);
		} catch {
			setPreviewSql("-- 预览失败，请检查参数");
		}
	};

	const buildPayload = () => ({
		blueprintCodes: selectedBlueprints,
		sourceTable: selectedTable,
		fieldMapping,
		params,
	});

	const handleSubmit = async () => {
		if (!selectedTemplateId) {
			toast.error("请选择模板");
			return;
		}
		if (selectedBlueprints.length === 0) {
			toast.error("请至少选择一个蓝图");
			return;
		}
		if (!selectedDatasetId) {
			toast.error("请选择数据集");
			return;
		}
		setSubmitting(true);
		try {
			await applyIndicatorTemplate(selectedTemplateId, buildPayload());
			onSuccess();
		} catch {
			// interceptor handles error
		} finally {
			setSubmitting(false);
		}
	};

	const canNext = () => {
		if (step === 0) return selectedTemplateId && selectedBlueprints.length > 0;
		if (step === 1) return !!selectedDatasetId;
		return true;
	};

	const columnOptions = pickerFields.map((f) => ({
		label: `${f.name}${f.comment ? ` (${f.comment})` : ""}`,
		value: f.name,
	}));

	const mappingColumns = [
		{
			title: "模板占位符",
			dataIndex: "field",
			width: 200,
		},
		{
			title: "实际列",
			dataIndex: "mapping",
			render: (_: any, record: { field: string }) => (
				<Select
					style={{ width: "100%" }}
					placeholder="选择列"
					options={columnOptions}
					value={fieldMapping[record.field] || undefined}
					onChange={(val) => setFieldMapping((prev) => ({ ...prev, [record.field]: val }))}
					allowClear
					showSearch
					filterOption={(input, opt) =>
						(opt?.label as string)?.toLowerCase().includes(input.toLowerCase()) ?? false
					}
				/>
			),
		},
	];

	const mappingData = requiredFields.map((f) => ({ key: f, field: f }));

	const renderStep0 = () => (
		<div>
			<Typography.Text strong style={{ display: "block", marginBottom: 12 }}>
				选择模板
			</Typography.Text>
			{!template && (
				<div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 16 }}>
					{templates.length === 0 && <Empty description="暂无模板" />}
					{templates.map((tpl) => (
						<Card
							key={tpl.id}
							hoverable
							size="small"
							style={{
								width: 220,
								border: selectedTemplateId === tpl.id ? "2px solid #1677ff" : undefined,
							}}
							onClick={() => handleSelectTemplate(tpl)}
						>
							<Typography.Text strong>{tpl.name}</Typography.Text>
							<br />
							<Typography.Text type="secondary" style={{ fontSize: 12 }}>
								{tpl.code} | {tpl.domain ?? "-"}
							</Typography.Text>
						</Card>
					))}
				</div>
			)}
			{template && (
				<Card size="small" style={{ marginBottom: 16, border: "2px solid #1677ff" }}>
					<Typography.Text strong>{template.name}</Typography.Text>
					<br />
					<Typography.Text type="secondary" style={{ fontSize: 12 }}>
						{template.code} | {template.domain ?? "-"}
					</Typography.Text>
				</Card>
			)}

			<Typography.Text strong style={{ display: "block", marginBottom: 8 }}>
				选择蓝图
			</Typography.Text>
			{blueprints.length > 0 && (
				<div style={{ marginBottom: 8 }}>
					<Checkbox
						checked={selectedBlueprints.length === blueprints.length && blueprints.length > 0}
						indeterminate={selectedBlueprints.length > 0 && selectedBlueprints.length < blueprints.length}
						onChange={(e) => handleSelectAllBlueprints(e.target.checked)}
					>
						全选
					</Checkbox>
				</div>
			)}
			<div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
				{blueprints.length === 0 && <Empty description="请先选择模板" />}
				{blueprints.map((bp) => (
					<Card key={bp.code} size="small">
						<Checkbox
							checked={selectedBlueprints.includes(bp.code ?? "")}
							onChange={(e) => handleBlueprintToggle(bp.code ?? "", e.target.checked)}
						>
							<Space>
								<Typography.Text strong>{bp.name ?? bp.code}</Typography.Text>
								<Typography.Text type="secondary" style={{ fontSize: 12 }}>
									{bp.aggregation ?? ""}
								</Typography.Text>
							</Space>
						</Checkbox>
						{bp.description && (
							<Typography.Paragraph type="secondary" style={{ fontSize: 12, margin: "4px 0 0 24px" }}>
								{bp.description}
							</Typography.Paragraph>
						)}
					</Card>
				))}
			</div>
		</div>
	);

	const renderStep1 = () => (
		<div>
			<Typography.Text strong style={{ display: "block", marginBottom: 12 }}>
				绑定源表
			</Typography.Text>
			<DatasetPicker
				value={selectedDatasetId}
				onChange={(id) => {
					setSelectedDatasetId(id);
					if (!id) {
						setSelectedTable("");
						setPickerFields([]);
						setFieldMapping({});
					}
				}}
				onFieldsLoaded={(fields) => {
					setPickerFields(fields);
					setFieldMapping({});
					const tableName = fields[0]?.tableName ?? "";
					setSelectedTable(tableName);
				}}
				placeholder="选择数据集（支持域筛选和关键字搜索）"
				style={{ width: "100%", marginBottom: 16 }}
			/>

			{requiredFields.length > 0 && (
				<>
					<Typography.Text strong style={{ display: "block", marginBottom: 8 }}>
						字段映射
					</Typography.Text>
					<CompactTable
						rowKey="field"
						columns={mappingColumns}
						dataSource={mappingData}
						pagination={false}
						size="small"
					/>
				</>
			)}
		</div>
	);

	const renderStep2 = () => (
		<div>
			<Typography.Text strong style={{ display: "block", marginBottom: 12 }}>
				参数调整
			</Typography.Text>
			<Form layout="vertical">
				<Form.Item label="阈值">
					<InputNumber
						style={{ width: "100%" }}
						placeholder="如 0.95"
						value={params.threshold}
						onChange={(v) => setParams((prev) => ({ ...prev, threshold: v }))}
					/>
				</Form.Item>
				<Form.Item label="过滤条件">
					<Input.TextArea
						rows={3}
						placeholder="如 WHERE status = 'ACTIVE'"
						value={params.filterCondition}
						onChange={(e) => setParams((prev) => ({ ...prev, filterCondition: e.target.value }))}
					/>
				</Form.Item>
				<Form.Item label="其他参数 (JSON)">
					<Input.TextArea
						rows={3}
						placeholder='{"granularity": "DAILY"}'
						value={params.extraJson}
						onChange={(e) => setParams((prev) => ({ ...prev, extraJson: e.target.value }))}
					/>
				</Form.Item>
			</Form>

			<Button onClick={handlePreviewSql}>
				SQL 预览
			</Button>

			<Modal
				title="SQL 预览"
				open={previewOpen}
				onCancel={() => setPreviewOpen(false)}
				footer={null}
				width={720}
			>
				<Input.TextArea value={previewSql} readOnly rows={16} style={{ fontFamily: "monospace" }} />
			</Modal>
		</div>
	);

	const steps = [
		{ title: "选择模板", content: renderStep0 },
		{ title: "绑定源表", content: renderStep1 },
		{ title: "参数调整", content: renderStep2 },
	];

	return (
		<Drawer
			title="指标配置向导"
			open={open}
			onClose={onClose}
			width={720}
			destroyOnClose
			footer={
				<div style={{ display: "flex", justifyContent: "space-between" }}>
					<Button onClick={onClose}>取消</Button>
					<Space>
						{step > 0 && <Button onClick={() => setStep(step - 1)}>上一步</Button>}
						{step < steps.length - 1 && (
							<Button type="primary" disabled={!canNext()} onClick={() => setStep(step + 1)}>
								下一步
							</Button>
						)}
						{step === steps.length - 1 && (
							<Button
								type="primary"
								loading={submitting}
								onClick={handleSubmit}
							>
								提交生成
							</Button>
						)}
					</Space>
				</div>
			}
		>
			<Steps current={step} items={steps.map((s) => ({ title: s.title }))} style={{ marginBottom: 24 }} />
			{steps[step].content()}
		</Drawer>
	);
}

function parseJson(value: any): any {
	if (value == null) return null;
	if (typeof value !== "string") return value;
	try {
		return JSON.parse(value);
	} catch {
		return null;
	}
}
