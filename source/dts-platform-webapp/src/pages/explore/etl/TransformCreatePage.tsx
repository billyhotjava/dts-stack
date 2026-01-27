import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, Collapse, Divider, Form, Input, Radio, Select, Space, Steps, Switch, Table, Typography } from "antd";
import { SaveOutlined } from "@ant-design/icons";
import { toast } from "sonner";
import { PageHeader } from "@/components/page-header";
import { createIngestionTask } from "@/api/platformApi";
import { useUserInfo } from "@/store/userStore";
import { useParams, useRouter } from "@/routes/hooks";
import { ingestionTaskAPI, type IngestionTaskDTO, type TableInfo } from "@/api/ingestion";
import dataSourcesService, { type InfraDataSource } from "@/api/services/dataSourcesService";

const { Text } = Typography;

const DRAFT_STORAGE_KEY = "ingestion_task_draft";
const CONNECTION_KEYS = [
	"jdbcUrl",
	"host",
	"port",
	"username",
	"password",
	"database",
	"schema",
	"connectionString",
	"dsn",
];

const JDBC_READER_BY_TYPE: Record<string, string> = {
	dm: "dmreader",
	dameng: "dmreader",
	postgres: "postgresqlreader",
	postgresql: "postgresqlreader",
	pg: "postgresqlreader",
	mysql: "mysqlreader",
	mariadb: "mysqlreader",
	oracle: "oraclereader",
	sqlserver: "sqlserverreader",
	mssql: "sqlserverreader",
	clickhouse: "clickhousereader",
	hive: "hivereader",
	db2: "db2reader",
	sqlite: "sqlitereader",
};

const JDBC_READER_BY_URL: Record<string, string> = {
	"jdbc:dm:": "dmreader",
	"jdbc:postgresql:": "postgresqlreader",
	"jdbc:mysql:": "mysqlreader",
	"jdbc:mariadb:": "mysqlreader",
	"jdbc:oracle:": "oraclereader",
	"jdbc:sqlserver:": "sqlserverreader",
	"jdbc:clickhouse:": "clickhousereader",
	"jdbc:hive2:": "hivereader",
	"jdbc:db2:": "db2reader",
	"jdbc:sqlite:": "sqlitereader",
};

const normalizeText = (value?: string) => String(value || "").trim();

const normalizeType = (value?: string) => normalizeText(value).toLowerCase();

const resolveReaderTypeFromDataSource = (source?: InfraDataSource | null) => {
	if (!source) return "";
	const props = source.props || {};
	const direct = normalizeText(String(props.readerType || props.reader || props.type || ""));
	if (direct) return direct;
	const type = normalizeType(source.type);
	if (type && JDBC_READER_BY_TYPE[type]) {
		return JDBC_READER_BY_TYPE[type];
	}
	const jdbcUrl = normalizeText(source.jdbcUrl).toLowerCase();
	if (jdbcUrl) {
		for (const [prefix, reader] of Object.entries(JDBC_READER_BY_URL)) {
			if (jdbcUrl.startsWith(prefix)) {
				return reader;
			}
		}
	}
	return "";
};

const isJdbcSource = (source?: InfraDataSource | null) => {
	if (!source) return false;
	if (normalizeText(source.jdbcUrl)) return true;
	const type = normalizeType(source.type);
	return Boolean(type && JDBC_READER_BY_TYPE[type]);
};

const splitLines = (value?: string) =>
	normalizeText(value)
		.split(/\r?\n/)
		.map((item) => item.trim())
		.filter(Boolean);

const splitColumns = (value?: string) => {
	const text = normalizeText(value);
	if (!text) return ["*"];
	if (text === "*") return ["*"];
	return text
		.split(",")
		.map((item) => item.trim())
		.filter(Boolean);
};

const mergeConfig = (base: Record<string, any>, extra?: Record<string, any>) => {
	if (!extra || Object.keys(extra).length === 0) return base;
	return { ...base, ...extra };
};

const parseJson = (value?: string, label?: string) => {
	const text = normalizeText(value);
	if (!text) return undefined;
	try {
		return JSON.parse(text);
	} catch {
		throw new Error(`${label || "配置"} JSON 格式错误`);
	}
};

const hasConnectionOverride = (config: any): boolean => {
	if (!config) return false;
	if (Array.isArray(config)) {
		return config.some((item) => hasConnectionOverride(item));
	}
	if (typeof config !== "object") return false;
	for (const key of CONNECTION_KEYS) {
		if (key in config && normalizeText((config as any)[key])) {
			return true;
		}
	}
	const connection = (config as any).connection;
	if (connection) {
		return hasConnectionOverride(connection);
	}
	return false;
};

const buildTableKey = (table: TableInfo) =>
	normalizeText(table.schema) ? `${table.schema}.${table.name}` : table.name;

const applyTablesToConfig = (rawConfig: Record<string, any> | undefined, tables: string[]) => {
	if (!rawConfig) return rawConfig;
	const config = { ...rawConfig };
	if (Array.isArray(config.connection) && config.connection.length) {
		const first = { ...config.connection[0], table: tables };
		config.connection = [first, ...config.connection.slice(1)];
		return config;
	}
	if (config.connection && typeof config.connection === "object") {
		config.connection = { ...(config.connection as Record<string, any>), table: tables };
		return config;
	}
	config.table = tables;
	return config;
};

const buildReaderConfig = (values: Record<string, any>) => {
	const tables = splitLines(values.readerTables);
	const columns = splitColumns(values.readerColumns);
	const querySql = splitLines(values.readerQuerySql);
	const extra = parseJson(values.readerExtraConfig, "Reader 扩展配置") as Record<string, any> | undefined;
	const sourceSystem = normalizeText(values.sourceSystem);

	const config: Record<string, any> = {
		column: columns,
	};
	const where = normalizeText(values.readerWhere);
	if (where) config.where = where;
	if (sourceSystem) config.sourceSystem = sourceSystem;
	if (tables.length) config.table = tables;
	if (querySql.length) config.querySql = querySql;
	return mergeConfig(config, extra);
};

const buildWriterConfig = (values: Record<string, any>) => {
	const jdbcUrls = splitLines(values.writerJdbcUrls);
	const tables = splitLines(values.writerTables);
	const columns = splitColumns(values.writerColumns);
	const preSql = splitLines(values.writerPreSql);
	const postSql = splitLines(values.writerPostSql);
	const extra = parseJson(values.writerExtraConfig, "Writer 扩展配置") as Record<string, any> | undefined;

	const connection: Record<string, any> = {};
	if (jdbcUrls.length) connection.jdbcUrl = jdbcUrls;
	if (tables.length) connection.table = tables;

	const config: Record<string, any> = {
		column: columns,
	};
	const username = normalizeText(values.writerUsername);
	const password = normalizeText(values.writerPassword);
	const schema = normalizeText(values.writerSchema);
	const writeMode = normalizeText(values.writerWriteMode);
	if (username) config.username = username;
	if (password) config.password = password;
	if (schema) config.schema = schema;
	if (writeMode) config.writeMode = writeMode;
	if (preSql.length) config.preSql = preSql;
	if (postSql.length) config.postSql = postSql;
	if (Object.keys(connection).length) config.connection = [connection];
	return mergeConfig(config, extra);
};

const buildJobPreview = (
	values: Record<string, any>,
	useDefaultDestination: boolean,
	editorMode?: string
) => {
	const readerType = normalizeText(values.readerType);
	if (!readerType) throw new Error("Reader 类型不能为空");
	const jobConfig = parseJson(values.jobConfig, "作业参数") as Record<string, any> | undefined;
	if (jobConfig) return jobConfig;

	const writerType = useDefaultDestination ? "default-writer" : normalizeText(values.writerType);
	if (!writerType) throw new Error("Writer 类型不能为空");

	let readerConfig: Record<string, any> | undefined;
	let writerConfig: Record<string, any> | undefined;
	const sourceSystem = normalizeText(values.sourceSystem);

	if (editorMode === "json") {
		readerConfig = parseJson(values.readerConfig, "Reader 配置") as Record<string, any> | undefined;
		if (sourceSystem && readerConfig && !readerConfig.sourceSystem) {
			readerConfig.sourceSystem = sourceSystem;
		}
		writerConfig = useDefaultDestination
			? { __fromDefault__: true }
			: (parseJson(values.writerConfig, "Writer 配置") as Record<string, any> | undefined);
	} else {
		readerConfig = buildReaderConfig(values);
		writerConfig = useDefaultDestination ? { __fromDefault__: true } : buildWriterConfig(values);
	}

	return {
		job: {
			setting: {
				speed: {
					channel: 1,
				},
			},
			content: [
				{
					reader: {
						name: readerType,
						parameter: readerConfig || {},
					},
					writer: {
						name: writerType,
						parameter: writerConfig || {},
					},
				},
			],
		},
	};
};

const jsonValidator = (label: string, forbidConnection = false) => (_: any, value: string) => {
	if (!normalizeText(value)) return Promise.resolve();
	try {
		const parsed = JSON.parse(value);
		if (forbidConnection && hasConnectionOverride(parsed)) {
			return Promise.reject(new Error(`${label} 不允许包含连接信息，请仅填写表/字段/过滤等覆盖参数`));
		}
		return Promise.resolve();
	} catch {
		return Promise.reject(new Error(`${label} JSON 格式错误`));
	}
};

const loadDraft = (): Record<string, any> | null => {
	try {
		const stored = localStorage.getItem(DRAFT_STORAGE_KEY);
		if (!stored) return null;
		const draft = JSON.parse(stored);
		if (draft && typeof draft === "object" && draft.savedAt) {
			return draft;
		}
		return null;
	} catch {
		return null;
	}
};

const saveDraft = (values: Record<string, any>) => {
	try {
		const draft = { ...values, savedAt: new Date().toISOString() };
		localStorage.setItem(DRAFT_STORAGE_KEY, JSON.stringify(draft));
		return true;
	} catch {
		return false;
	}
};

const clearDraft = () => {
	try {
		localStorage.removeItem(DRAFT_STORAGE_KEY);
	} catch {
		// ignore
	}
};

const mapTaskToForm = (task: IngestionTaskDTO) => {
	const sourceConfig = task.sourceConfig || {};
	const destinationConfig = task.destinationConfig || {};
	const writerType = normalizeText(task.destinationType);
	const useDefaultDestination = !writerType;
	return {
		editorMode: "json",
		useDefaultDestination,
		airflowEnabled: task.airflowEnabled ?? true,
		runNow: false,
		name: task.name,
		description: task.description,
		sourceSystem:
			sourceConfig.sourceSystem ||
			sourceConfig.sourceApp ||
			sourceConfig.appCode ||
			sourceConfig.system ||
			sourceConfig.name,
		sourceDataSourceId: task.sourceDataSourceId,
		readerType: task.sourceType,
		readerConfig: JSON.stringify(sourceConfig, null, 2),
		writerType,
		writerConfig: JSON.stringify(destinationConfig, null, 2),
		jobConfig: task.addaxConfig ? JSON.stringify(task.addaxConfig, null, 2) : undefined,
	};
};

export default function TransformCreatePage() {
	const [saving, setSaving] = useState(false);
	const [savingDraft, setSavingDraft] = useState(false);
	const [hasDraft, setHasDraft] = useState(false);
	const [loadingTask, setLoadingTask] = useState(false);
	const [editingTask, setEditingTask] = useState<IngestionTaskDTO | null>(null);
	const [discoveringTables, setDiscoveringTables] = useState(false);
	const [discoveredTables, setDiscoveredTables] = useState<TableInfo[]>([]);
	const [selectedTableKeys, setSelectedTableKeys] = useState<string[]>([]);
	const [discoverError, setDiscoverError] = useState("");
	const [currentStep, setCurrentStep] = useState(0);
	const [dataSources, setDataSources] = useState<InfraDataSource[]>([]);
	const [loadingDataSources, setLoadingDataSources] = useState(false);
	const [form] = Form.useForm();
	const router = useRouter();
	const params = useParams();
	const editId = params?.id ? Number(params.id) : undefined;
	const isEdit = Number.isFinite(editId);
	const userInfo = useUserInfo() as any;
	const useDefaultDestination = Form.useWatch("useDefaultDestination", form);
	const editorMode = Form.useWatch("editorMode", form);
	const formValues = Form.useWatch([], form);
	const selectedDataSourceId = Form.useWatch("sourceDataSourceId", form);
	const selectedDataSource = useMemo(
		() => dataSources.find((item) => String(item.id) === String(selectedDataSourceId)),
		[dataSources, selectedDataSourceId]
	);

	const initialValues = useMemo(
		() => ({
			editorMode: "visual",
			useDefaultDestination: true,
			airflowEnabled: true,
			runNow: true,
			readerColumns: "*",
			writerColumns: "*",
		}),
		[]
	);

	useEffect(() => {
		const loadSources = async () => {
			try {
				setLoadingDataSources(true);
				const list = await dataSourcesService.list();
				setDataSources(Array.isArray(list) ? list : []);
			} catch (error: any) {
				toast.error(error?.message || "数据源列表加载失败");
				setDataSources([]);
			} finally {
				setLoadingDataSources(false);
			}
		};
		loadSources();
	}, []);

	useEffect(() => {
		if (!selectedDataSource) {
			if (!selectedDataSourceId && form.getFieldValue("readerType")) {
				form.setFieldValue("readerType", undefined);
			}
			return;
		}
		const readerType = resolveReaderTypeFromDataSource(selectedDataSource);
		if (readerType && form.getFieldValue("readerType") !== readerType) {
			form.setFieldValue("readerType", readerType);
		}
	}, [form, selectedDataSource]);

	useEffect(() => {
		setDiscoveredTables([]);
		setSelectedTableKeys([]);
		setDiscoverError("");
	}, [selectedDataSourceId]);

	const stepItems = useMemo(
		() => [
			{ key: "basic", title: "基础信息" },
			{ key: "reader", title: "源端配置" },
			{ key: "writer", title: "目标配置" },
			{ key: "review", title: "预览与执行" },
		],
		[]
	);

	// Load draft on mount
	useEffect(() => {
		if (isEdit) {
			return;
		}
		const draft = loadDraft();
		if (draft) {
			setHasDraft(true);
			const { savedAt, ...formValues } = draft;
			form.setFieldsValue(formValues);
			toast.info(`已恢复草稿 (${new Date(savedAt).toLocaleString()})`);
		}
	}, [form, isEdit]);

	useEffect(() => {
		if (!isEdit || !editId) {
			return;
		}
		const loadTask = async () => {
			try {
				setLoadingTask(true);
				const task = await ingestionTaskAPI.getTask(editId);
				setEditingTask(task);
				form.setFieldsValue(mapTaskToForm(task));
			} catch (error: any) {
				toast.error(`加载任务失败: ${error?.message || "未知错误"}`);
			} finally {
				setLoadingTask(false);
			}
		};
		loadTask();
	}, [editId, form, isEdit]);

	const handleSaveDraft = async () => {
		if (isEdit) {
			toast.info("编辑模式不支持保存草稿");
			return;
		}
		try {
			setSavingDraft(true);
			const values = form.getFieldsValue();
			if (saveDraft(values)) {
				setHasDraft(true);
				toast.success("草稿已保存");
			} else {
				toast.error("保存草稿失败");
			}
		} finally {
			setSavingDraft(false);
		}
	};

	const handleDiscoverTables = async () => {
		try {
			setDiscoverError("");
			setDiscoveringTables(true);
			const values = form.getFieldsValue(true);
			const dataSourceId = normalizeText(values.sourceDataSourceId);
			if (!dataSourceId) {
				throw new Error("请先选择数据源连接");
			}
			if (!isJdbcSource(selectedDataSource)) {
				throw new Error("当前数据源不支持表发现");
			}
			const schema = normalizeText(values.readerSchema);
			const tablePattern = normalizeText(values.readerTablePattern);
			const rawTables = await ingestionTaskAPI.discoverTables({
				source: {
					dataSourceId,
				},
				filter: {
					schema: schema || undefined,
					tablePattern: tablePattern || undefined,
					limit: 500,
				},
			});
			const tables = Array.isArray(rawTables) ? rawTables : [];
			setDiscoveredTables(tables);
			setSelectedTableKeys([]);
			if (tables.length === 0) {
				setDiscoverError("未发现可用表");
			}
		} catch (err: any) {
			setDiscoverError(err?.message || "获取表清单失败");
		} finally {
			setDiscoveringTables(false);
		}
	};

	const handleApplyTables = () => {
		if (!selectedTableKeys.length) {
			toast.error("请先选择表");
			return;
		}
		try {
			const tables = selectedTableKeys;
			const values = form.getFieldsValue(true);
			const isJsonMode = values.editorMode === "json";
			if (isJsonMode) {
				const readerConfig = parseJson(values.readerConfig, "Reader 配置") as Record<string, any> | undefined;
				const nextReader = applyTablesToConfig(readerConfig, tables);
				form.setFieldValue("readerConfig", JSON.stringify(nextReader || {}, null, 2));
				if (!values.useDefaultDestination) {
					const writerConfig = parseJson(values.writerConfig, "Writer 配置") as Record<string, any> | undefined;
					const nextWriter = applyTablesToConfig(writerConfig, tables);
					form.setFieldValue("writerConfig", JSON.stringify(nextWriter || {}, null, 2));
				}
			} else {
				form.setFieldValue("readerTables", tables.join("\n"));
				if (!values.useDefaultDestination) {
					form.setFieldValue("writerTables", tables.join("\n"));
				}
			}
			toast.success("已更新表清单");
		} catch (err: any) {
			toast.error(err?.message || "更新表清单失败");
		}
	};

	const resolveStepFields = (stepIndex: number, values: Record<string, any>) => {
		const isJsonMode = values?.editorMode === "json";
		const useDefault = Boolean(values?.useDefaultDestination);
		switch (stepIndex) {
			case 0:
				return ["editorMode", "name", "description", "sourceSystem"];
			case 1:
				return isJsonMode
					? ["sourceDataSourceId", "readerType", "readerConfig"]
					: ["sourceDataSourceId", "readerType", "readerTables"];
			case 2:
				if (useDefault) return ["useDefaultDestination"];
				return isJsonMode
					? ["useDefaultDestination", "writerType", "writerConfig"]
					: ["useDefaultDestination", "writerType", "writerJdbcUrls", "writerTables"];
			case 3:
				return ["jobConfig", "airflowEnabled", "runNow"];
			default:
				return [];
		}
	};

	const validateStep = async (stepIndex: number) => {
		const values = form.getFieldsValue(true);
		const fields = resolveStepFields(stepIndex, values);
		if (!fields.length) return true;
		try {
			await form.validateFields(fields);
			return true;
		} catch {
			return false;
		}
	};

	const handleStepChange = async (nextStep: number) => {
		if (nextStep <= currentStep) {
			setCurrentStep(nextStep);
			return;
		}
		const ok = await validateStep(currentStep);
		if (!ok) {
			toast.error("请先完成当前步骤必填项");
			return;
		}
		setCurrentStep(nextStep);
	};

	const handleNextStep = async () => {
		if (currentStep >= stepItems.length - 1) return;
		const ok = await validateStep(currentStep);
		if (!ok) {
			toast.error("请先完成当前步骤必填项");
			return;
		}
		setCurrentStep((prev) => prev + 1);
	};

	const handlePrevStep = () => {
		setCurrentStep((prev) => Math.max(prev - 1, 0));
	};

	const handleSubmit = async (values: any) => {
		try {
			setSaving(true);
			const isJsonMode = values.editorMode === "json";
			const readerConfig = isJsonMode
				? parseJson(values.readerConfig, "Reader 配置")
				: buildReaderConfig(values);
			if (hasConnectionOverride(readerConfig)) {
				throw new Error("入湖任务必须使用已配置的数据源连接，Reader 配置中不可包含连接信息");
			}
			const sourceSystem = normalizeText(values.sourceSystem);
			if (sourceSystem && readerConfig && typeof readerConfig === "object" && !readerConfig.sourceSystem) {
				readerConfig.sourceSystem = sourceSystem;
			}
			const writerConfig = values.useDefaultDestination
				? undefined
				: isJsonMode
					? parseJson(values.writerConfig, "Writer 配置")
					: buildWriterConfig(values);
			const jobConfig = parseJson(values.jobConfig, "作业参数");
			if (isEdit && editId) {
				const updatePayload: IngestionTaskDTO = {
					...(editingTask || {}),
					id: editId,
					name: normalizeText(values.name),
					description: normalizeText(values.description) || undefined,
					sourceType: normalizeText(values.readerType),
					sourceDataSourceId: values.sourceDataSourceId,
					sourceConfig: (readerConfig as Record<string, any>) || {},
					destinationType: values.useDefaultDestination ? undefined : normalizeText(values.writerType),
					destinationConfig: writerConfig as Record<string, any> | undefined,
					syncMode: editingTask?.syncMode || "full",
					addaxConfig: (jobConfig as Record<string, any>) || editingTask?.addaxConfig,
					airflowEnabled: Boolean(values.airflowEnabled),
				};
				await ingestionTaskAPI.updateTask(editId, updatePayload);
				toast.success("入湖任务已更新");
				router.push(`/explore/etl/transform/${editId}`);
			} else {
				const payload = {
					name: normalizeText(values.name),
					description: normalizeText(values.description) || undefined,
					owner: userInfo?.username || userInfo?.login,
					source: {
						dataSourceId: values.sourceDataSourceId,
						config: readerConfig || {},
					},
					destination: {
						usePlatformDefault: Boolean(values.useDefaultDestination),
						type: values.useDefaultDestination ? undefined : normalizeText(values.writerType),
						config: writerConfig || undefined,
					},
					airflow: {
						enabled: Boolean(values.airflowEnabled),
					},
					runNow: Boolean(values.runNow),
					jobConfig: jobConfig || undefined,
				};
				await createIngestionTask(payload);
				clearDraft();
				setHasDraft(false);
				toast.success("入湖任务已提交");
				router.push("/explore/etl/transform/new");
			}
		} catch (err: any) {
			toast.error(err?.message || (isEdit ? "更新入湖任务失败" : "创建入湖任务失败"));
		} finally {
			setSaving(false);
		}
	};

	const previewState = useMemo(() => {
		if (!formValues) return { config: null, error: "" };
		try {
			const config = buildJobPreview(formValues, Boolean(useDefaultDestination), editorMode);
			return { config, error: "" };
		} catch (error: any) {
			return { config: null, error: error?.message || "无法生成预览" };
		}
	}, [formValues, useDefaultDestination, editorMode]);

	return (
		<div className="flex flex-col gap-6">
			<PageHeader
				title={isEdit ? "编辑入湖任务" : "创建入湖任务"}
				description="使用 Addax 生成作业配置，并由 Airflow 触发执行。"
				actions={
					<Space>
						<Button
							onClick={() => {
								if (isEdit && editingTask) {
									form.setFieldsValue(mapTaskToForm(editingTask));
								} else {
									form.resetFields();
								}
								setCurrentStep(0);
							}}
						>
							重置表单
						</Button>
						<Button
							icon={<SaveOutlined />}
							loading={savingDraft}
							onClick={handleSaveDraft}
							disabled={isEdit}
						>
							保存草稿{hasDraft ? " ✓" : ""}
						</Button>
					</Space>
				}
			/>
			<Card>
				<Alert
					message="提示"
					description="请选择已配置的数据源连接并填写 Addax Reader/Writer 覆盖参数。Writer 可选择平台默认数据湖。"
					type="info"
					showIcon
					className="mb-6"
				/>
				<Form
					form={form}
					layout="vertical"
					initialValues={initialValues}
					onFinish={handleSubmit}
				>
					<Steps
						current={currentStep}
						items={stepItems}
						onChange={handleStepChange}
						className="mb-6"
					/>
					{currentStep === 0 && (
						<>
							<Form.Item name="editorMode" label="配置方式">
								<Radio.Group>
									<Radio.Button value="visual">可视化</Radio.Button>
									<Radio.Button value="json">JSON</Radio.Button>
								</Radio.Group>
							</Form.Item>
							<Form.Item
								name="name"
								label="任务名称"
								rules={[{ required: true, message: "请输入任务名称" }]}
							>
								<Input placeholder="例如：pg-lake-task1" />
							</Form.Item>
							<Form.Item name="description" label="任务描述">
								<Input.TextArea rows={2} placeholder="可选，说明任务用途" />
							</Form.Item>
							<Form.Item name="sourceSystem" label="源系统标识">
								<Input placeholder="可选，例如：erp、crm（用于绑定 DAG）" />
							</Form.Item>
						</>
					)}
					{currentStep === 1 && (
						<>
							<Divider orientation="left">Reader 配置</Divider>
							<Form.Item
								name="sourceDataSourceId"
								label="数据源连接"
								rules={[{ required: true, message: "请选择数据源连接" }]}
							>
								<Select
									loading={loadingDataSources}
									placeholder={loadingDataSources ? "加载中..." : "请选择数据源连接"}
									options={dataSources.map((item) => ({
										label: `${item.name} (${item.type || "unknown"})`,
										value: item.id,
									}))}
									showSearch
									optionFilterProp="label"
								/>
							</Form.Item>
							<Form.Item
								name="readerType"
								label="Reader 类型"
								rules={[{ required: true, message: "Reader 类型未解析，请检查数据源配置" }]}
							>
								<Input placeholder="将根据数据源自动生成" disabled />
							</Form.Item>
							{editorMode === "json" ? (
								<Form.Item
									name="readerConfig"
									label="Reader 配置 (JSON)"
									rules={[
										{ required: true, message: "请输入 Reader 配置" },
										{ validator: jsonValidator("Reader 配置", true) },
									]}
								>
									<Input.TextArea rows={6} placeholder='{"column":["*"],"table":["table_a"]}' />
								</Form.Item>
							) : (
								<>
									<div className="grid gap-4 md:grid-cols-2">
										<Form.Item
											name="readerTables"
											label="Reader 表（每行一个）"
											rules={[{ required: true, message: "请输入表名" }]}
										>
											<Input.TextArea rows={3} placeholder="source_table" />
										</Form.Item>
										<Form.Item name="readerColumns" label="Reader 字段（逗号分隔）">
											<Input placeholder="* 或 id,name,created_at" />
										</Form.Item>
									</div>
									<div className="grid gap-4 md:grid-cols-2">
										<Form.Item name="readerWhere" label="Reader 过滤条件">
											<Input placeholder="可选，例如：status = 1" />
										</Form.Item>
									</div>
									<Collapse
										ghost
										items={[
											{
												key: "reader-advanced",
												label: "Reader 高级参数",
												children: (
													<div className="space-y-4">
														<Form.Item name="readerQuerySql" label="Reader 查询 SQL（每行一条）">
															<Input.TextArea rows={3} placeholder="select * from t where ..." />
														</Form.Item>
														<Form.Item name="readerExtraConfig" label="Reader 扩展配置 JSON">
															<Input.TextArea rows={4} placeholder='{"splitPk":"id"}' />
														</Form.Item>
													</div>
												),
											},
										]}
									/>
								</>
							)}
							<Divider orientation="left">源端表发现</Divider>
							<Card type="inner">
								<div className="grid gap-4 md:grid-cols-3">
									<Form.Item name="readerSchema" label="Schema（可选）">
										<Input placeholder="例如 public" />
									</Form.Item>
									<Form.Item name="readerTablePattern" label="表名筛选（可选）">
										<Input placeholder="支持 SQL LIKE，例如 ods_%" />
									</Form.Item>
									<Form.Item label="操作">
										<Space>
											<Button onClick={handleDiscoverTables} loading={discoveringTables}>
												获取表清单
											</Button>
											<Button onClick={handleApplyTables} disabled={!selectedTableKeys.length}>
												应用选择
											</Button>
										</Space>
									</Form.Item>
								</div>
								{discoverError ? (
									<Alert type="warning" message={discoverError} showIcon className="mb-3" />
								) : null}
								<Table
									rowKey={(record) => buildTableKey(record)}
									size="small"
									loading={discoveringTables}
									dataSource={discoveredTables}
									rowSelection={{
										selectedRowKeys: selectedTableKeys,
										onChange: (keys) => setSelectedTableKeys(keys.map((key) => String(key))),
									}}
									columns={[
										{ title: "Schema", dataIndex: "schema", width: 140 },
										{ title: "表名", dataIndex: "name" },
										{ title: "类型", dataIndex: "type", width: 120 },
									]}
									pagination={{ pageSize: 8 }}
								/>
								<Text type="secondary" className="block mt-2">
									已选择 {selectedTableKeys.length} 张表
								</Text>
							</Card>
						</>
					)}
					{currentStep === 2 && (
						<>
							<Divider orientation="left">目标端配置</Divider>
							<Form.Item name="useDefaultDestination" label="使用平台默认数据湖" valuePropName="checked">
								<Switch />
							</Form.Item>
							{!useDefaultDestination && (
								<>
									<Divider orientation="left">Writer 配置</Divider>
									<Form.Item
										name="writerType"
										label="Writer 类型"
										rules={[{ required: true, message: "请输入 Writer 类型" }]}
									>
										<Input placeholder="例如：postgresqlwriter" />
									</Form.Item>
									{editorMode === "json" ? (
										<Form.Item
											name="writerConfig"
											label="Writer 配置 (JSON)"
											rules={[
												{ required: true, message: "请输入 Writer 配置" },
												{ validator: jsonValidator("Writer 配置") },
											]}
										>
											<Input.TextArea rows={6} placeholder='{"username":"xxx","password":"xxx","column":["*"]}' />
										</Form.Item>
									) : (
										<>
											<div className="grid gap-4 md:grid-cols-2">
												<Form.Item
													name="writerJdbcUrls"
													label="Writer JDBC URL（每行一个）"
													rules={[{ required: true, message: "请输入 JDBC URL" }]}
												>
													<Input.TextArea rows={3} placeholder="jdbc:postgresql://host:5432/db" />
												</Form.Item>
												<Form.Item
													name="writerTables"
													label="Writer 表（每行一个）"
													rules={[{ required: true, message: "请输入表名" }]}
												>
													<Input.TextArea rows={3} placeholder="target_table" />
												</Form.Item>
											</div>
											<div className="grid gap-4 md:grid-cols-2">
												<Form.Item name="writerColumns" label="Writer 字段（逗号分隔）">
													<Input placeholder="* 或 id,name,created_at" />
												</Form.Item>
												<Form.Item name="writerWriteMode" label="Writer 写入模式">
													<Input placeholder="insert / replace / update" />
												</Form.Item>
											</div>
											<div className="grid gap-4 md:grid-cols-2">
												<Form.Item name="writerUsername" label="Writer 用户名">
													<Input placeholder="数据库账号" />
												</Form.Item>
												<Form.Item name="writerPassword" label="Writer 密码">
													<Input.Password placeholder="******" />
												</Form.Item>
											</div>
											<Form.Item name="writerSchema" label="Writer Schema">
												<Input placeholder="可选，例如 public" />
											</Form.Item>
											<Collapse
												ghost
												items={[
													{
														key: "writer-advanced",
														label: "Writer 高级参数",
														children: (
															<div className="space-y-4">
																<Form.Item name="writerPreSql" label="Writer 前置 SQL（每行一条）">
																	<Input.TextArea rows={3} placeholder="delete from t where ..." />
																</Form.Item>
																<Form.Item name="writerPostSql" label="Writer 后置 SQL（每行一条）">
																	<Input.TextArea rows={3} placeholder="analyze table t" />
																</Form.Item>
																<Form.Item name="writerExtraConfig" label="Writer 扩展配置 JSON">
																	<Input.TextArea rows={4} placeholder='{"batchSize":1000}' />
																</Form.Item>
															</div>
														),
													},
												]}
											/>
										</>
									)}
								</>
							)}
						</>
					)}
					{currentStep === 3 && (
						<>
							<Form.Item name="jobConfig" label="作业参数 (JSON，可选)" rules={[{ validator: jsonValidator("作业参数") }]}>
								<Input.TextArea rows={4} placeholder='{"setting":{"speed":{"channel":3}}}' />
							</Form.Item>
							<Card type="inner" title="作业预览">
								{previewState.error ? (
									<Alert type="warning" message={previewState.error} showIcon />
								) : (
									<pre className="bg-gray-50 p-4 rounded overflow-auto">
										{JSON.stringify(previewState.config, null, 2)}
									</pre>
								)}
								{useDefaultDestination ? (
									<Text type="secondary" className="block mt-2">
										Writer 使用平台默认配置，预览中仅展示占位参数。
									</Text>
								) : null}
							</Card>
							<Card type="inner" title="Airflow 触发">
								<Form.Item name="airflowEnabled" label="启用 Airflow" valuePropName="checked">
									<Switch />
								</Form.Item>
								<Form.Item name="runNow" label="立即触发" valuePropName="checked">
									<Switch />
								</Form.Item>
								<Text type="secondary">
									若未勾选立即触发，仅保存作业配置，后续可在 Airflow 中手动运行。
								</Text>
							</Card>
						</>
					)}
					<Divider />
					<Space>
						<Button onClick={handlePrevStep} disabled={currentStep === 0}>
							上一步
						</Button>
						{currentStep < stepItems.length - 1 ? (
							<Button type="primary" onClick={handleNextStep}>
								下一步
							</Button>
						) : (
							<Button type="primary" loading={saving} onClick={() => form.submit()} disabled={loadingTask}>
								{isEdit ? "保存修改" : "提交任务"}
							</Button>
						)}
					</Space>
				</Form>
			</Card>
		</div>
	);
}
