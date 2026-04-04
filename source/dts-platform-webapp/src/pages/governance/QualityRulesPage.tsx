import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
	Button,
	Card,

	Descriptions,
	Drawer,
	Empty,
	Form,
	Input,
	Modal,
	Radio,
	Select,
	Switch,
	Space,
	Table,
	Tabs,
	Tag,
	Typography,
} from "antd";
import type { ColumnsType } from "antd/es/table";
import { PlusOutlined, PlayCircleOutlined, DeleteOutlined, EditOutlined, EyeOutlined } from "@ant-design/icons";
import { useSearchParams } from "react-router";
import { useGovernanceManageAccess } from "@/hooks/useModuleManageAccess";
import {
	listQualityRules,
	listQualityRuleVersions,
	changeQualityRuleVersionStatus,
	createQualityRule,
	updateQualityRule,
	deleteQualityRule,
	toggleQualityRule,
	triggerQualityDryRun,
	triggerQualityRun,
	listDatasets,
	listQualityTemplates,
	previewTemplateSQL,
} from "@/api/platformApi";
import { formatTime } from "@/utils/textUtils";
import QualityDashboard from "./components/QualityDashboard";
import QualityTasksTab from "./components/QualityTasksTab";
import QualityReportTab from "./components/QualityReportTab";
import DataRepairTab from "./components/DataRepairTab";

const SEVERITY_OPTIONS = [
	{ label: "低", value: "LOW" },
	{ label: "中", value: "MEDIUM" },
	{ label: "高", value: "HIGH" },
	{ label: "致命", value: "CRITICAL" },
];

const TYPE_OPTIONS = [
	{ label: "完整性", value: "COMPLETENESS" },
	{ label: "一致性", value: "CONSISTENCY" },
	{ label: "准确性", value: "ACCURACY" },
	{ label: "唯一性", value: "UNIQUENESS" },
	{ label: "及时性", value: "TIMELINESS" },
];

const ACTION_ON_FAIL_OPTIONS = [
	{ label: "告警", value: "WARN" },
	{ label: "阻断", value: "BLOCK" },
];

type TemplateOption = {
	id: string;
	code?: string;
	name?: string;
	category?: string;
	sqlTemplate?: string;
	paramSchema?: string;
	severityDefault?: string;
	actionDefault?: string;
};

type Rule = any;

type RuleForm = {
	code?: string;
	name: string;
	type?: string;
	severity?: string;
	datasetId?: string;
	enabled?: boolean;
	publishNow?: boolean;
	definition?: string;
	createMode?: "custom" | "template";
	templateId?: string;
	templateParams?: string;
	actionOnFail?: string;
	autoTrigger?: boolean;
};

type RuleFilters = {
	keyword?: string;
	type?: string;
	severity?: string;
	enabled?: string;
	datasetId?: string;
};
type QualityRunRow = {
	id?: string;
	ruleId?: string;
	ruleVersionId?: string;
	datasetId?: string;
	triggerType?: string;
	status?: string;
	startedAt?: string;
	finishedAt?: string;
	durationMs?: number;
	message?: string;
	inputParamsJson?: string;
	errorCategory?: string;
	metrics?: Array<Record<string, any>>;
	metricsJson?: string;
};
type QualityRuleVersion = {
	id?: string;
	version?: number;
	status?: string;
	definition?: string;
	notes?: string;
	approvedBy?: string;
	approvedAt?: string;
	createdBy?: string;
	createdDate?: string;
};

const parseJsonText = (value?: string) => {
	try {
		return value ? JSON.parse(value) : undefined;
	} catch {
		return undefined;
	}
};
const prettyText = (value: any) => {
	if (value == null) return "-";
	try {
		return JSON.stringify(value, null, 2);
	} catch {
		return String(value);
	}
};

const parseDefinition = (value?: string) => {
	if (!value) return undefined;
	try {
		return JSON.parse(value);
	} catch (error) {
		return undefined;
	}
};

export default function Page() {
	const [searchParams, setSearchParams] = useSearchParams();
	const [rules, setRules] = useState<Rule[]>([]);
	const [loading, setLoading] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [detailOpen, setDetailOpen] = useState(false);
	const [detailRule, setDetailRule] = useState<Rule | null>(null);
	const [dryRunModalOpen, setDryRunModalOpen] = useState(false);
	const [dryRunRows, setDryRunRows] = useState<QualityRunRow[]>([]);
	const [versionModalOpen, setVersionModalOpen] = useState(false);
	const [versionRows, setVersionRows] = useState<QualityRuleVersion[]>([]);
	const [versionLoading, setVersionLoading] = useState(false);
	const [compareLeft, setCompareLeft] = useState<number>();
	const [compareRight, setCompareRight] = useState<number>();
	const [editing, setEditing] = useState<Rule | null>(null);
	const [form] = Form.useForm<RuleForm>();
	const [datasets, setDatasets] = useState<{ id: string; name: string }[]>([]);
	const canManage = useGovernanceManageAccess();
	const [templateOptions, setTemplateOptions] = useState<TemplateOption[]>([]);
	const [previewedSql, setPreviewedSql] = useState<string>("");
	const [filters, setFilters] = useState<RuleFilters>({
		keyword: searchParams.get("keyword") || "",
		type: searchParams.get("type") || undefined,
		severity: searchParams.get("severity") || undefined,
		enabled: searchParams.get("enabled") || undefined,
		datasetId: searchParams.get("datasetId") || undefined,
	});

	const datasetOptions = useMemo(
		() => datasets.map((item) => ({ label: item.name, value: item.id })),
		[datasets],
	);

	const syncFilterParams = (next: RuleFilters) => {
		const params = new URLSearchParams(searchParams);
		const setOrDelete = (key: string, value?: string) => {
			if (!value) {
				params.delete(key);
				return;
			}
			params.set(key, value);
		};
		setOrDelete("keyword", next.keyword?.trim());
		setOrDelete("type", next.type);
		setOrDelete("severity", next.severity);
		setOrDelete("enabled", next.enabled);
		setOrDelete("datasetId", next.datasetId);
		setSearchParams(params, { replace: true });
	};


	const handleFilterChange = (patch: Partial<RuleFilters>) => {
		setFilters((prev) => {
			const next = { ...prev, ...patch };
			syncFilterParams(next);
			return next;
		});
	};

	const resetFilters = () => {
		const next: RuleFilters = { keyword: "" };
		setFilters(next);
		syncFilterParams(next);
	};

	const loadRules = async () => {
		setLoading(true);
		try {
			const list = await listQualityRules();
			setRules(Array.isArray(list) ? (list as Rule[]) : []);
		} catch (error: any) {
			toast.error(error?.message || "规则加载失败");
		} finally {
			setLoading(false);
		}
	};

	const loadDatasets = async () => {
		try {
			const resp: any = await listDatasets({ page: 0, size: 200 });
			const list = Array.isArray(resp?.content) ? resp.content : [];
			setDatasets(list.map((item: any) => ({ id: String(item.id), name: item.name || item.id })));
		} catch (error: any) {
			toast.error(error?.message || "数据集加载失败");
		}
	};

	const loadTemplates = async () => {
		try {
			const list = await listQualityTemplates();
			setTemplateOptions(Array.isArray(list) ? (list as TemplateOption[]) : []);
		} catch {
			// template loading failure is non-critical
		}
	};

	useEffect(() => {
		void loadRules();
		void loadDatasets();
		void loadTemplates();
	}, []);


	const openVersionModal = async (rule: Rule) => {
		if (!rule?.id) return;
		setVersionLoading(true);
		try {
			const rows = (await listQualityRuleVersions(String(rule.id))) as QualityRuleVersion[];
			const sorted = Array.isArray(rows)
				? [...rows].sort((a, b) => Number(b?.version || 0) - Number(a?.version || 0))
				: [];
			setVersionRows(sorted);
			setCompareLeft(sorted?.[0]?.version);
			setCompareRight(sorted?.[1]?.version ?? sorted?.[0]?.version);
			setDetailRule(rule);
			setVersionModalOpen(true);
		} catch (error: any) {
			toast.error(error?.message || "版本历史加载失败");
		} finally {
			setVersionLoading(false);
		}
	};

	const openModal = (rule?: Rule) => {
		setEditing(rule || null);
		setPreviewedSql("");
		form.setFieldsValue({
			code: rule?.code || "",
			name: rule?.name || "",
			type: rule?.type || "COMPLETENESS",
			severity: rule?.severity || "MEDIUM",
			datasetId: rule?.datasetId || undefined,
			enabled: rule?.enabled ?? true,
			publishNow: true,
			definition: rule?.latestVersion?.definition
				? rule.latestVersion.definition
				: rule?.definition
					? JSON.stringify(rule.definition, null, 2)
					: "",
			createMode: "custom",
			templateId: undefined,
			templateParams: "",
			actionOnFail: rule?.actionOnFail || "WARN",
			autoTrigger: rule?.autoTrigger ?? false,
		});
		setModalOpen(true);
	};

	const handleTemplatePreview = async () => {
		const templateId = form.getFieldValue("templateId");
		if (!templateId) {
			toast.error("请先选择模板");
			return;
		}
		try {
			const paramsText = form.getFieldValue("templateParams") || "{}";
			const params = JSON.parse(paramsText);
			const result: any = await previewTemplateSQL(templateId, params);
			const sql = typeof result === "string" ? result : (result?.sql || JSON.stringify(result, null, 2));
			setPreviewedSql(sql);
			toast.success("SQL 预览成功");
		} catch (error: any) {
			toast.error(error?.message || "预览失败，请检查参数格式");
		}
	};

	const saveRule = async () => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		try {
			const values = await form.validateFields();
			const parsedDefinition = parseDefinition(values.definition);
			if (values.definition && !parsedDefinition) {
				toast.error("规则定义不是有效的 JSON");
				return;
			}
			const payload: any = {
				code: values.code || undefined,
				name: values.name,
				type: values.type,
				severity: values.severity,
				datasetId: values.datasetId || undefined,
				enabled: values.enabled ?? true,
				publishNow: values.publishNow ?? true,
				definition: parsedDefinition || undefined,
				actionOnFail: values.actionOnFail || "WARN",
				autoTrigger: values.autoTrigger ?? false,
				templateId: values.createMode === "template" ? values.templateId : undefined,
				templateParams: values.createMode === "template" ? values.templateParams : undefined,
			};
			if (editing?.id) {
				await updateQualityRule(editing.id, payload);
				toast.success("规则已更新");
			} else {
				await createQualityRule(payload);
				toast.success("规则已新增");
			}
			setModalOpen(false);
			await loadRules();
		} catch (error: any) {
			if (error?.errorFields) return;
			toast.error(error?.message || "保存失败");
		}
	};

	const removeRule = async (id?: string) => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		if (!id) return;
		try {
			await deleteQualityRule(id);
			toast.success("规则已删除");
			await loadRules();
		} catch (error: any) {
			toast.error(error?.message || "删除失败");
		}
	};

	const toggleRule = async (rule: Rule, enabled: boolean) => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		try {
			await toggleQualityRule(rule.id, enabled);
			toast.success(enabled ? "规则已启用" : "规则已停用");
			await loadRules();
		} catch (error: any) {
			toast.error(error?.message || "操作失败");
		}
	};

	const triggerRun = async (rule: Rule) => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		if (!rule?.id) return;
		try {
			await triggerQualityRun({ ruleId: rule.id });
			toast.success("已触发执行");
		} catch (error: any) {
			toast.error(error?.message || "触发失败");
		}
	};

	const triggerDryRun = async (rule: Rule) => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		if (!rule?.id) return;
		try {
			const rows = (await triggerQualityDryRun({ ruleId: rule.id, datasetId: rule.datasetId || undefined })) as QualityRunRow[];
			setDryRunRows(Array.isArray(rows) ? rows : []);
			setDryRunModalOpen(true);
			toast.success("试跑完成");
		} catch (error: any) {
			toast.error(error?.message || "试跑失败");
		}
	};

	const changeLatestVersionStatus = async (rule: Rule, status: "PUBLISHED" | "ARCHIVED") => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		const latestVersion = Number(rule?.latestVersion?.version);
		if (!rule?.id || !latestVersion) {
			toast.error("未找到可变更的规则版本");
			return;
		}
		try {
			await changeQualityRuleVersionStatus(String(rule.id), latestVersion, { status });
			toast.success(status === "PUBLISHED" ? "版本已发布" : "版本已归档");
			await loadRules();
		} catch (error: any) {
			toast.error(error?.message || "版本状态变更失败");
		}
	};

	const columns: ColumnsType<Rule> = [
		{ title: "规则名称", dataIndex: "name", render: (v) => v || "-" },
		{ title: "类型", dataIndex: "type", width: 120, render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "严重性", dataIndex: "severity", width: 120, render: (v) => <Tag>{v || "-"}</Tag> },
		{
			title: "版本",
			width: 140,
			render: (_, record) => {
				const version = record?.latestVersion?.version;
				const status = record?.latestVersion?.status;
				return (
					<Space size={4}>
						<Tag color="blue">{version ? `v${version}` : "-"}</Tag>
						<Tag>{status || "-"}</Tag>
					</Space>
				);
			},
		},
		{ title: "数据集", dataIndex: "datasetId", render: (v) => datasets.find((d) => d.id === v)?.name || v || "-" },
		{
			title: "策略",
			dataIndex: "actionOnFail",
			width: 90,
			render: (v) => (
				<Tag color={v === "BLOCK" ? "red" : "blue"}>
					{v === "BLOCK" ? "阻断" : "告警"}
				</Tag>
			),
		},
		{
			title: "自动触发",
			dataIndex: "autoTrigger",
			width: 90,
			render: (v) => <Tag color={v ? "green" : "default"}>{v ? "是" : "否"}</Tag>,
		},
		{ title: "状态", dataIndex: "enabled", width: 100, render: (v) => <Tag color={v ? "green" : "default"}>{v ? "启用" : "停用"}</Tag> },
		{
			title: "操作",
			width: 360,
			render: (_, record) => (
				<Space>
					<Button size="small" onClick={() => triggerDryRun(record)} disabled={!canManage}>
						试跑
					</Button>
					<Button size="small" onClick={() => void openVersionModal(record)}>
						版本
					</Button>
					<Button size="small" icon={<EyeOutlined />} onClick={() => {
						setDetailRule(record);
						setDetailOpen(true);
					}}>
						详情
					</Button>
					<Button size="small" icon={<PlayCircleOutlined />} onClick={() => triggerRun(record)} disabled={!canManage}>
						执行
					</Button>
					<Button size="small" icon={<EditOutlined />} onClick={() => openModal(record)} disabled={!canManage}>
						编辑
					</Button>
					<Button
						size="small"
						onClick={() => void changeLatestVersionStatus(record, "PUBLISHED")}
						disabled={!canManage || String(record?.latestVersion?.status || "").toUpperCase() === "PUBLISHED"}
					>
						发布
					</Button>
					<Button
						size="small"
						onClick={() => void changeLatestVersionStatus(record, "ARCHIVED")}
						disabled={!canManage || String(record?.latestVersion?.status || "").toUpperCase() !== "PUBLISHED"}
					>
						归档
					</Button>
					<Button size="small" onClick={() => toggleRule(record, !record.enabled)} disabled={!canManage}>
						{record.enabled ? "停用" : "启用"}
					</Button>
					<Button size="small" danger icon={<DeleteOutlined />} onClick={() => removeRule(record.id)} disabled={!canManage}>
						删除
					</Button>
				</Space>
			),
		},
	];

	const filteredRules = useMemo(() => {
		const keyword = String(filters.keyword || "")
			.trim()
			.toLowerCase();
		return rules.filter((rule) => {
			if (filters.type && rule?.type !== filters.type) return false;
			if (filters.severity && rule?.severity !== filters.severity) return false;
			if (filters.datasetId && String(rule?.datasetId || "") !== filters.datasetId) return false;
			if (filters.enabled) {
				const enabled = String(rule?.enabled) === "true";
				if (filters.enabled === "ENABLED" && !enabled) return false;
				if (filters.enabled === "DISABLED" && enabled) return false;
			}
			if (!keyword) return true;
			const haystacks = [rule?.name, rule?.code, rule?.type, rule?.severity, rule?.datasetId]
				.map((item) => String(item || "").toLowerCase())
				.join(" ");
			return haystacks.includes(keyword);
		});
	}, [filters, rules]);

	const leftVersion = useMemo(
		() => versionRows.find((item) => Number(item.version) === Number(compareLeft)),
		[versionRows, compareLeft],
	);
	const rightVersion = useMemo(
		() => versionRows.find((item) => Number(item.version) === Number(compareRight)),
		[versionRows, compareRight],
	);
	const changedKeys = useMemo(() => {
		const leftObj = parseJsonText(leftVersion?.definition || "");
		const rightObj = parseJsonText(rightVersion?.definition || "");
		if (!leftObj || !rightObj || typeof leftObj !== "object" || typeof rightObj !== "object") return [];
		const keys = new Set([...Object.keys(leftObj), ...Object.keys(rightObj)]);
		return Array.from(keys).filter((key) => JSON.stringify((leftObj as any)[key]) !== JSON.stringify((rightObj as any)[key]));
	}, [leftVersion, rightVersion]);


	const activeTab = searchParams.get("tab") || "overview";
	const handleTabChange = (key: string) => {
		const params = new URLSearchParams(searchParams);
		params.set("tab", key);
		setSearchParams(params, { replace: true });
	};

	return (
		<div className="space-y-4">
			<Tabs
				activeKey={activeTab}
				onChange={handleTabChange}
				items={[
					{
						key: "overview",
						label: "概览",
						children: <QualityDashboard />,
					},
					{
						key: "rules",
						label: "质量规则",
						children: (
							<div className="space-y-4">
								<RulesTabContent />
							</div>
						),
					},
					{
						key: "tasks",
						label: "检查任务",
						children: <QualityTasksTab />,
					},
					{
						key: "report",
						label: "质量报告",
						children: <QualityReportTab />,
					},
					{
						key: "repair",
						label: "数据修复",
						children: <DataRepairTab />,
					},
				]}
			/>

			{/* Modals and Drawers shared across tabs */}
			<Modal
				open={modalOpen}
				title={editing ? "编辑规则" : "新增规则"}
				onCancel={() => setModalOpen(false)}
				onOk={saveRule}
				okText="保存"
				destroyOnClose
				width={720}
			>
				<Form form={form} layout="vertical">
					{!editing && (
						<Form.Item label="创建方式" name="createMode">
							<Radio.Group>
								<Radio value="custom">自定义 SQL</Radio>
								<Radio value="template">从模板创建</Radio>
							</Radio.Group>
						</Form.Item>
					)}
					<Form.Item label="规则名称" name="name" rules={[{ required: true, message: "请输入规则名称" }]}>
						<Input placeholder="例如：订单金额非空" />
					</Form.Item>
					<Form.Item label="规则编码" name="code">
						<Input placeholder="可选" />
					</Form.Item>
					<Form.Item label="类型" name="type">
						<Select options={TYPE_OPTIONS} />
					</Form.Item>
					<Form.Item label="严重性" name="severity">
						<Select options={SEVERITY_OPTIONS} />
					</Form.Item>
					<Form.Item label="数据集" name="datasetId">
						<Select options={datasetOptions} allowClear />
					</Form.Item>
					<Form.Item label="失败策略" name="actionOnFail">
						<Select options={ACTION_ON_FAIL_OPTIONS} />
					</Form.Item>
					<Form.Item label="自动触发" name="autoTrigger" valuePropName="checked">
						<Switch checkedChildren="开启" unCheckedChildren="关闭" />
					</Form.Item>
					<Form.Item label="保存策略" name="publishNow" valuePropName="checked">
						<Switch checkedChildren="直接发布" unCheckedChildren="草稿" />
					</Form.Item>
					<Form.Item noStyle shouldUpdate={(prev, cur) => prev.createMode !== cur.createMode}>
						{({ getFieldValue }) =>
							getFieldValue("createMode") === "template" ? (
								<>
									<Form.Item label="选择模板" name="templateId">
										<Select
											placeholder="选择规则模板"
											options={templateOptions.map((t) => ({
												label: `${t.name || t.code || t.id}`,
												value: t.id,
											}))}
											allowClear
										/>
									</Form.Item>
									<Form.Item label="模板参数 (JSON)" name="templateParams">
										<Input.TextArea rows={4} placeholder='{"column":"amount","table":"orders"}' />
									</Form.Item>
									<div className="mb-4 flex items-center gap-2">
										<Button onClick={handleTemplatePreview}>预览 SQL</Button>
										{previewedSql && (
											<Tag color="green">已生成</Tag>
										)}
									</div>
									{previewedSql && (
										<pre className="mb-4 rounded bg-gray-50 p-3 text-sm" style={{ whiteSpace: "pre-wrap", maxHeight: 200, overflow: "auto" }}>
											{previewedSql}
										</pre>
									)}
								</>
							) : (
								<Form.Item label="规则定义(JSON)" name="definition">
									<Input.TextArea rows={6} placeholder='例如: {"column":"amount","rule":"not_null"}' />
								</Form.Item>
							)
						}
					</Form.Item>
				</Form>
			</Modal>

			<Drawer
				open={detailOpen}
				title="规则详情"
				width={720}
				onClose={() => setDetailOpen(false)}
				extra={
					canManage ? (
						<Space>
							<Button
								onClick={() => {
									if (!detailRule) return;
									openModal(detailRule);
								}}
							>
								编辑
							</Button>
							<Button
								type="primary"
								icon={<PlayCircleOutlined />}
								onClick={() => {
									if (!detailRule) return;
									void triggerRun(detailRule);
								}}
							>
								执行
							</Button>
						</Space>
					) : undefined
				}
			>
				<Descriptions column={1} bordered size="small">
					<Descriptions.Item label="规则名称">{detailRule?.name || "-"}</Descriptions.Item>
					<Descriptions.Item label="规则编码">{detailRule?.code || "-"}</Descriptions.Item>
					<Descriptions.Item label="规则类型">{detailRule?.type || "-"}</Descriptions.Item>
					<Descriptions.Item label="严重性">{detailRule?.severity || "-"}</Descriptions.Item>
					<Descriptions.Item label="数据集">
						{datasets.find((item) => item.id === detailRule?.datasetId)?.name || detailRule?.datasetId || "-"}
					</Descriptions.Item>
					<Descriptions.Item label="失败策略">
						<Tag color={detailRule?.actionOnFail === "BLOCK" ? "red" : "blue"}>
							{detailRule?.actionOnFail === "BLOCK" ? "阻断" : "告警"}
						</Tag>
					</Descriptions.Item>
					<Descriptions.Item label="自动触发">
						<Tag color={detailRule?.autoTrigger ? "green" : "default"}>
							{detailRule?.autoTrigger ? "是" : "否"}
						</Tag>
					</Descriptions.Item>
					<Descriptions.Item label="状态">
						<Tag color={detailRule?.enabled ? "green" : "default"}>{detailRule?.enabled ? "启用" : "停用"}</Tag>
					</Descriptions.Item>
					<Descriptions.Item label="最新版本状态">
						{detailRule?.latestVersion?.version ? `v${detailRule.latestVersion.version}` : "-"} / {detailRule?.latestVersion?.status || "-"}
					</Descriptions.Item>
					<Descriptions.Item label="定义(JSON)">
						<pre style={{ whiteSpace: "pre-wrap", margin: 0 }}>
							{detailRule?.latestVersion?.definition
								? JSON.stringify(detailRule.latestVersion.definition, null, 2)
								: detailRule?.definition
									? JSON.stringify(detailRule.definition, null, 2)
									: "-"}
						</pre>
					</Descriptions.Item>
				</Descriptions>
			</Drawer>

			<Modal
				open={dryRunModalOpen}
				onCancel={() => setDryRunModalOpen(false)}
				footer={null}
				title="规则试跑结果"
				width={980}
			>
				<Table
					rowKey={(row) => row.id || Math.random().toString(36)}
					dataSource={dryRunRows}
					pagination={false}
					columns={[
						{ title: "运行ID", dataIndex: "id", width: 220, render: (v) => v || "-" },
						{ title: "状态", dataIndex: "status", width: 120, render: (v) => <Tag>{v || "-"}</Tag> },
						{ title: "触发方式", dataIndex: "triggerType", width: 120, render: (v) => v || "-" },
						{ title: "开始时间", dataIndex: "startedAt", width: 180, render: formatTime },
						{ title: "结束时间", dataIndex: "finishedAt", width: 180, render: formatTime },
						{ title: "说明", dataIndex: "message", render: (v) => v || "-" },
					]}
				/>
			</Modal>

			<Modal
				open={versionModalOpen}
				onCancel={() => setVersionModalOpen(false)}
				footer={null}
				title={`规则版本对比${detailRule?.name ? ` - ${detailRule.name}` : ""}`}
				width={1100}
			>
				<Space wrap style={{ marginBottom: 12 }}>
					<Select
						style={{ width: 220 }}
						placeholder="左侧版本"
						value={compareLeft}
						options={versionRows.map((row) => ({ label: `v${row.version}`, value: row.version }))}
						onChange={setCompareLeft}
					/>
					<Select
						style={{ width: 220 }}
						placeholder="右侧版本"
						value={compareRight}
						options={versionRows.map((row) => ({ label: `v${row.version}`, value: row.version }))}
						onChange={setCompareRight}
					/>
				</Space>
				{versionLoading ? (
					<Card loading />
				) : versionRows.length === 0 ? (
					<Empty description="暂无版本数据" />
				) : (
					<Space direction="vertical" size={12} style={{ width: "100%" }}>
						<Space wrap>
							<Typography.Text strong>变更键：</Typography.Text>
							{changedKeys.length > 0 ? changedKeys.map((key) => <Tag key={key}>{key}</Tag>) : <Tag>无差异</Tag>}
						</Space>
						<div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
							<Card size="small" title={`v${leftVersion?.version ?? "-"}`}>
								<pre style={{ whiteSpace: "pre-wrap", margin: 0 }}>{prettyText(parseJsonText(leftVersion?.definition || "") || leftVersion?.definition)}</pre>
							</Card>
							<Card size="small" title={`v${rightVersion?.version ?? "-"}`}>
								<pre style={{ whiteSpace: "pre-wrap", margin: 0 }}>{prettyText(parseJsonText(rightVersion?.definition || "") || rightVersion?.definition)}</pre>
							</Card>
						</div>
					</Space>
				)}
			</Modal>
		</div>
	);

	function RulesTabContent() {
		return (
			<>
			<Card
				title="质量规则"
				extra={
					<Button className="rounded-2xl" type="primary" icon={<PlusOutlined />} onClick={() => openModal()} disabled={!canManage}>
						新增规则
					</Button>
				}
			>
				<div className="mb-3 flex flex-wrap items-center gap-2">
					<Input
						style={{ width: 240 }}
						allowClear
						placeholder="按名称/编码搜索"
						value={filters.keyword}
						onChange={(event) => handleFilterChange({ keyword: event.target.value })}
					/>
					<Select
						style={{ width: 160 }}
						allowClear
						placeholder="规则类型"
						options={TYPE_OPTIONS}
						value={filters.type}
						onChange={(value) => handleFilterChange({ type: value })}
					/>
					<Select
						style={{ width: 140 }}
						allowClear
						placeholder="严重性"
						options={SEVERITY_OPTIONS}
						value={filters.severity}
						onChange={(value) => handleFilterChange({ severity: value })}
					/>
					<Select
						style={{ width: 160 }}
						allowClear
						placeholder="启停状态"
						options={[
							{ label: "启用", value: "ENABLED" },
							{ label: "停用", value: "DISABLED" },
						]}
						value={filters.enabled}
						onChange={(value) => handleFilterChange({ enabled: value })}
					/>
					<Select
						style={{ width: 220 }}
						allowClear
						placeholder="数据集"
						options={datasetOptions}
						value={filters.datasetId}
						onChange={(value) => handleFilterChange({ datasetId: value })}
					/>
					<Button className="rounded-2xl" onClick={resetFilters}>
						重置筛选
					</Button>
				</div>
				<Table
					rowKey={(record) => record.id}
					columns={columns}
					dataSource={filteredRules}
					loading={loading}
					scroll={{ x: 1200 }}
					pagination={{ showSizeChanger: true }}
				/>
			</Card>
			</>
		);
	}
}
