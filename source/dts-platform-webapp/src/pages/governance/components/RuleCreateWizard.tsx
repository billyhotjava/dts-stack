import { useEffect, useState, useMemo } from "react";
import { toast } from "sonner";
import {
	Button,
	Form,
	Input,
	InputNumber,
	Modal,
	Radio,
	Select,
	Steps,
	Tag,
} from "antd";
import {
	CheckCircleOutlined,
	CodeOutlined,
	FileTextOutlined,
} from "@ant-design/icons";
import {
	listQualityTemplates,
	previewTemplateSQL,
	createQualityRule,
	listDatasets,
} from "@/api/platformApi";

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

interface RuleCreateWizardProps {
	open: boolean;
	onClose: () => void;
	onSuccess: () => void;
	editingRule?: any;
}

type CreateMethod = "template" | "custom";

type TemplateOption = {
	id: string;
	code?: string;
	name?: string;
	description?: string;
	category?: string;
	sqlTemplate?: string;
	paramSchema?: string;
	severityDefault?: string;
	actionDefault?: string;
};

type ParamDef = {
	name: string;
	type: string;
	label: string;
	required?: boolean;
	placeholder?: string;
};

/* ------------------------------------------------------------------ */
/*  Constants                                                          */
/* ------------------------------------------------------------------ */

const SEVERITY_OPTIONS = [
	{ label: "致命", value: "CRITICAL" },
	{ label: "高", value: "HIGH" },
	{ label: "中", value: "MEDIUM" },
	{ label: "低", value: "LOW" },
];

const ACTION_OPTIONS = [
	{ label: "BLOCK 阻断入湖", value: "BLOCK" },
	{ label: "ALERT 仅告警", value: "WARN" },
];

const STEP_ITEMS = [
	{ title: "选择方式" },
	{ title: "配置规则" },
	{ title: "绑定数据集" },
];

/* ------------------------------------------------------------------ */
/*  Helpers                                                            */
/* ------------------------------------------------------------------ */

function parseParamSchema(raw?: string): ParamDef[] {
	if (!raw) return [];
	try {
		const parsed = JSON.parse(raw);
		if (Array.isArray(parsed?.params)) return parsed.params;
		if (Array.isArray(parsed)) return parsed;
		return [];
	} catch {
		return [];
	}
}

function renderParamField(param: ParamDef) {
	switch (param.type) {
		case "enum_list":
			return <Input.TextArea rows={3} placeholder={param.placeholder || "每行一个值"} />;
		case "number":
			return <InputNumber style={{ width: "100%" }} placeholder={param.placeholder} />;
		case "table_select":
		case "column_select":
		case "text":
		default:
			return <Input placeholder={param.placeholder || param.label} />;
	}
}

/* ------------------------------------------------------------------ */
/*  Component                                                          */
/* ------------------------------------------------------------------ */

export default function RuleCreateWizard({ open, onClose, onSuccess, editingRule: _editingRule }: RuleCreateWizardProps) {
	const [current, setCurrent] = useState(0);
	const [method, setMethod] = useState<CreateMethod | null>(null);
	const [selectedTemplate, setSelectedTemplate] = useState<TemplateOption | null>(null);
	const [templates, setTemplates] = useState<TemplateOption[]>([]);
	const [datasets, setDatasets] = useState<{ id: string; name: string }[]>([]);
	const [previewSql, setPreviewSql] = useState("");
	const [saving, setSaving] = useState(false);

	const [form] = Form.useForm();

	/* --- data loading --- */

	useEffect(() => {
		if (!open) return;
		// reset state on open
		setCurrent(0);
		setMethod(null);
		setSelectedTemplate(null);
		setPreviewSql("");
		form.resetFields();

		void loadTemplates();
		void loadDatasets();
	}, [open]);

	const loadTemplates = async () => {
		try {
			const list = await listQualityTemplates();
			setTemplates(Array.isArray(list) ? (list as TemplateOption[]) : []);
		} catch {
			/* non-critical */
		}
	};

	const loadDatasets = async () => {
		try {
			const resp: any = await listDatasets({ page: 0, size: 200 });
			const list = Array.isArray(resp?.content) ? resp.content : [];
			setDatasets(list.map((d: any) => ({ id: String(d.id), name: d.name || d.id })));
		} catch {
			/* non-critical */
		}
	};

	const datasetOptions = useMemo(
		() => datasets.map((d) => ({ label: d.name, value: d.id })),
		[datasets],
	);

	/* --- template params --- */

	const templateParams = useMemo(
		() => parseParamSchema(selectedTemplate?.paramSchema),
		[selectedTemplate],
	);

	/* --- SQL preview --- */

	const handlePreviewSql = async () => {
		if (!selectedTemplate) return;
		try {
			const paramValues: Record<string, any> = {};
			for (const p of templateParams) {
				paramValues[p.name] = form.getFieldValue(["templateParams", p.name]);
			}
			const result: any = await previewTemplateSQL(selectedTemplate.id, paramValues);
			const sql = typeof result === "string" ? result : (result?.sql || JSON.stringify(result, null, 2));
			setPreviewSql(sql);
			toast.success("SQL 预览成功");
		} catch (error: any) {
			toast.error(error?.message || "预览失败，请检查参数");
		}
	};

	/* --- navigation --- */

	const canNext = () => {
		if (current === 0) return method !== null && (method === "custom" || selectedTemplate !== null);
		if (current === 1) return true;
		return true;
	};

	const handleNext = async () => {
		if (current === 1) {
			try {
				await form.validateFields();
			} catch {
				return;
			}
		}
		setCurrent((s) => Math.min(s + 1, 2));
	};

	const handlePrev = () => setCurrent((s) => Math.max(s - 1, 0));

	/* --- save --- */

	const handleSave = async (publishNow: boolean) => {
		try {
			await form.validateFields();
		} catch {
			return;
		}

		setSaving(true);
		try {
			const values = form.getFieldsValue(true);

			// Build templateParams JSON string if template mode
			let templateParamsStr: string | undefined;
			if (method === "template" && selectedTemplate) {
				const paramValues: Record<string, any> = {};
				for (const p of templateParams) {
					paramValues[p.name] = values.templateParams?.[p.name];
				}
				templateParamsStr = JSON.stringify(paramValues);
			}

			// Build definition for custom SQL mode
			let definition: any;
			if (method === "custom" && values.customSql) {
				definition = { sql: values.customSql };
			}

			const payload: any = {
				name: values.name,
				severity: values.severity || "MEDIUM",
				actionOnFail: values.actionOnFail || "WARN",
				enabled: true,
				publishNow,
				datasetIds: values.datasetIds || [],
				datasetId: values.datasetIds?.[0] || undefined,
				templateId: method === "template" ? selectedTemplate?.id : undefined,
				templateParams: templateParamsStr,
				definition,
			};

			await createQualityRule(payload);
			toast.success(publishNow ? "规则已发布" : "规则已保存为草稿");
			onSuccess();
		} catch (error: any) {
			if (error?.errorFields) return;
			toast.error(error?.message || "保存失败");
		} finally {
			setSaving(false);
		}
	};

	/* --- render steps --- */

	const renderStep0 = () => (
		<div className="py-4">
			<div className="mb-6 text-center text-gray-500">选择规则的创建方式</div>
			<div className="mx-auto flex max-w-xl gap-4">
				{/* Template card */}
				<div
					onClick={() => { setMethod("template"); setSelectedTemplate(null); }}
					className={`flex flex-1 cursor-pointer flex-col items-center rounded-xl border-2 p-6 transition-all hover:shadow-md ${
						method === "template"
							? "border-blue-500 bg-blue-50 shadow-md"
							: "border-gray-200 bg-white hover:border-blue-300"
					}`}
				>
					<FileTextOutlined className="mb-3 text-3xl text-blue-500" />
					<div className="mb-1 text-base font-semibold">从模板创建</div>
					<div className="text-center text-xs text-gray-400">选择预置模板，填写参数即可</div>
				</div>
				{/* Custom card */}
				<div
					onClick={() => { setMethod("custom"); setSelectedTemplate(null); }}
					className={`flex flex-1 cursor-pointer flex-col items-center rounded-xl border-2 p-6 transition-all hover:shadow-md ${
						method === "custom"
							? "border-blue-500 bg-blue-50 shadow-md"
							: "border-gray-200 bg-white hover:border-blue-300"
					}`}
				>
					<CodeOutlined className="mb-3 text-3xl text-blue-500" />
					<div className="mb-1 text-base font-semibold">自定义 SQL</div>
					<div className="text-center text-xs text-gray-400">编写自定义检查 SQL 语句</div>
				</div>
			</div>

			{/* Template grid when template method selected */}
			{method === "template" && (
				<div className="mt-6">
					<div className="mb-3 text-sm font-medium text-gray-600">选择模板：</div>
					{templates.length === 0 ? (
						<div className="py-8 text-center text-gray-400">暂无可用模板</div>
					) : (
						<div className="grid grid-cols-4 gap-3">
							{templates.map((tpl) => (
								<div
									key={tpl.id}
									onClick={() => setSelectedTemplate(tpl)}
									className={`cursor-pointer rounded-lg border px-3 py-3 text-center transition-all hover:shadow ${
										selectedTemplate?.id === tpl.id
											? "border-blue-500 bg-blue-50 shadow"
											: "border-gray-200 hover:border-blue-300"
									}`}
								>
									{selectedTemplate?.id === tpl.id && (
										<CheckCircleOutlined className="float-right text-blue-500" />
									)}
									<div className="truncate text-sm font-medium">{tpl.name || tpl.code || tpl.id}</div>
									{tpl.description && (
										<div className="mt-1 truncate text-xs text-gray-400">{tpl.description}</div>
									)}
									{tpl.category && (
										<Tag className="mt-1" color="blue">{tpl.category}</Tag>
									)}
								</div>
							))}
						</div>
					)}
				</div>
			)}
		</div>
	);

	const renderStep1 = () => (
		<div className="py-4">
			<Form form={form} layout="vertical" className="mx-auto max-w-lg">
				<Form.Item
					label="规则名称"
					name="name"
					rules={[{ required: true, message: "请输入规则名称" }]}
				>
					<Input placeholder="例如：订单金额非空检查" />
				</Form.Item>

				<Form.Item label="严重性" name="severity" initialValue="MEDIUM">
					<Radio.Group>
						{SEVERITY_OPTIONS.map((opt) => (
							<Radio.Button key={opt.value} value={opt.value}>{opt.label}</Radio.Button>
						))}
					</Radio.Group>
				</Form.Item>

				<Form.Item label="失败策略" name="actionOnFail" initialValue="WARN">
					<Radio.Group>
						{ACTION_OPTIONS.map((opt) => (
							<Radio.Button key={opt.value} value={opt.value}>{opt.label}</Radio.Button>
						))}
					</Radio.Group>
				</Form.Item>

				{/* Template params */}
				{method === "template" && selectedTemplate && templateParams.length > 0 && (
					<>
						<div className="mb-3 mt-4 text-sm font-medium text-gray-500">
							模板参数 — {selectedTemplate.name || selectedTemplate.code}
						</div>
						{templateParams.map((param) => (
							<Form.Item
								key={param.name}
								label={param.label}
								name={["templateParams", param.name]}
								rules={param.required ? [{ required: true, message: `请输入${param.label}` }] : undefined}
							>
								{renderParamField(param)}
							</Form.Item>
						))}
						<div className="mb-4 flex items-center gap-2">
							<Button onClick={handlePreviewSql}>预览 SQL</Button>
							{previewSql && <Tag color="green">已生成</Tag>}
						</div>
						{previewSql && (
							<pre className="mb-4 rounded bg-gray-50 p-3 text-sm" style={{ whiteSpace: "pre-wrap", maxHeight: 200, overflow: "auto" }}>
								{previewSql}
							</pre>
						)}
					</>
				)}

				{/* Custom SQL editor */}
				{method === "custom" && (
					<Form.Item
						label="自定义 SQL"
						name="customSql"
						rules={[{ required: true, message: "请输入检查 SQL" }]}
					>
						<Input.TextArea
							rows={8}
							placeholder={"SELECT count(*) AS total,\n       sum(CASE WHEN amount IS NULL THEN 1 ELSE 0 END) AS fail_count\nFROM your_table"}
							style={{ fontFamily: "monospace" }}
						/>
					</Form.Item>
				)}
			</Form>
		</div>
	);

	const renderStep2 = () => (
		<div className="py-4">
			<Form form={form} layout="vertical" className="mx-auto max-w-lg">
				<Form.Item
					label="绑定数据集"
					name="datasetIds"
					extra="可选择多个数据集绑定此规则"
				>
					<Select
						mode="multiple"
						placeholder="搜索并选择数据集"
						options={datasetOptions}
						showSearch
						filterOption={(input, option) =>
							String(option?.label || "").toLowerCase().includes(input.toLowerCase())
						}
						allowClear
					/>
				</Form.Item>
			</Form>
		</div>
	);

	/* --- footer --- */

	const renderFooter = () => (
		<div className="flex items-center justify-between">
			<Button onClick={onClose}>取消</Button>
			<div className="flex gap-2">
				{current > 0 && (
					<Button onClick={handlePrev}>上一步</Button>
				)}
				{current < 2 && (
					<Button type="primary" disabled={!canNext()} onClick={handleNext}>
						下一步
					</Button>
				)}
				{current === 2 && (
					<>
						<Button onClick={() => void handleSave(false)} loading={saving}>
							保存草稿
						</Button>
						<Button type="primary" onClick={() => void handleSave(true)} loading={saving}>
							发布
						</Button>
					</>
				)}
			</div>
		</div>
	);

	return (
		<Modal
			open={open}
			title="新建质量规则"
			onCancel={onClose}
			footer={renderFooter()}
			destroyOnClose
			width={800}
			styles={{ body: { minHeight: 360 } }}
		>
			<Steps current={current} items={STEP_ITEMS} className="mb-4" />
			{current === 0 && renderStep0()}
			{current === 1 && renderStep1()}
			{current === 2 && renderStep2()}
		</Modal>
	);
}
