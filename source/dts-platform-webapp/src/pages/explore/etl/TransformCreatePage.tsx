import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, Collapse, Divider, Form, Input, Radio, Space, Switch, Typography } from "antd";
import { SaveOutlined } from "@ant-design/icons";
import { toast } from "sonner";
import { PageHeader } from "@/components/page-header";
import { createIngestionTask } from "@/api/platformApi";
import { useUserInfo } from "@/store/userStore";
import { useParams, useRouter } from "@/routes/hooks";
import { ingestionTaskAPI, type IngestionTaskDTO } from "@/api/ingestion";

const { Text } = Typography;

const DRAFT_STORAGE_KEY = "ingestion_task_draft";

const normalizeText = (value?: string) => String(value || "").trim();

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

const buildReaderConfig = (values: Record<string, any>) => {
	const jdbcUrls = splitLines(values.readerJdbcUrls);
	const tables = splitLines(values.readerTables);
	const columns = splitColumns(values.readerColumns);
	const querySql = splitLines(values.readerQuerySql);
	const extra = parseJson(values.readerExtraConfig, "Reader 扩展配置") as Record<string, any> | undefined;
	const sourceSystem = normalizeText(values.sourceSystem);

	const connection: Record<string, any> = {};
	if (jdbcUrls.length) connection.jdbcUrl = jdbcUrls;
	if (tables.length) connection.table = tables;
	if (querySql.length) connection.querySql = querySql;

	const config: Record<string, any> = {
		column: columns,
	};
	const username = normalizeText(values.readerUsername);
	const password = normalizeText(values.readerPassword);
	const where = normalizeText(values.readerWhere);
	if (username) config.username = username;
	if (password) config.password = password;
	if (where) config.where = where;
	if (sourceSystem) config.sourceSystem = sourceSystem;
	if (Object.keys(connection).length) config.connection = [connection];
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

const jsonValidator = (label: string) => (_: any, value: string) => {
	if (!normalizeText(value)) return Promise.resolve();
	try {
		JSON.parse(value);
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
	const [form] = Form.useForm();
	const router = useRouter();
	const params = useParams<{ id?: string }>();
	const editId = params?.id ? Number(params.id) : undefined;
	const isEdit = Number.isFinite(editId);
	const userInfo = useUserInfo() as any;
	const useDefaultDestination = Form.useWatch("useDefaultDestination", form);
	const editorMode = Form.useWatch("editorMode", form);
	const formValues = Form.useWatch([], form);

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

	const handleSubmit = async (values: any) => {
		try {
			setSaving(true);
			const isJsonMode = values.editorMode === "json";
			const readerConfig = isJsonMode
				? parseJson(values.readerConfig, "Reader 配置")
				: buildReaderConfig(values);
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
					sourceConfig: (readerConfig as Record<string, any>) || {},
					destinationType: values.useDefaultDestination ? undefined : normalizeText(values.writerType),
					destinationConfig: writerConfig as Record<string, any> | undefined,
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
						type: normalizeText(values.readerType),
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
						<Button type="primary" loading={saving} onClick={() => form.submit()} disabled={loadingTask}>
							{isEdit ? "保存修改" : "提交任务"}
						</Button>
					</Space>
				}
			/>
			<Card>
				<Alert
					message="提示"
					description="请填写 Addax Reader/Writer 类型与配置。Writer 可选择平台默认数据湖。"
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
					<Divider orientation="left">Reader 配置</Divider>
					<Form.Item
						name="readerType"
						label="Reader 类型"
						rules={[{ required: true, message: "请输入 Reader 类型" }]}
					>
						<Input placeholder="例如：postgresqlreader" />
					</Form.Item>
					{editorMode === "json" ? (
						<Form.Item
							name="readerConfig"
							label="Reader 配置 (JSON)"
							rules={[{ required: true, message: "请输入 Reader 配置" }, { validator: jsonValidator("Reader 配置") }]}
						>
							<Input.TextArea rows={6} placeholder='{"username":"xxx","password":"xxx","column":["*"]}' />
						</Form.Item>
					) : (
						<>
							<div className="grid gap-4 md:grid-cols-2">
								<Form.Item
									name="readerJdbcUrls"
									label="Reader JDBC URL（每行一个）"
									rules={[{ required: true, message: "请输入 JDBC URL" }]}
								>
									<Input.TextArea rows={3} placeholder="jdbc:postgresql://host:5432/db" />
								</Form.Item>
								<Form.Item
									name="readerTables"
									label="Reader 表（每行一个）"
									rules={[{ required: true, message: "请输入表名" }]}
								>
									<Input.TextArea rows={3} placeholder="source_table" />
								</Form.Item>
							</div>
							<div className="grid gap-4 md:grid-cols-2">
								<Form.Item name="readerColumns" label="Reader 字段（逗号分隔）">
									<Input placeholder="* 或 id,name,created_at" />
								</Form.Item>
								<Form.Item name="readerWhere" label="Reader 过滤条件">
									<Input placeholder="可选，例如：status = 1" />
								</Form.Item>
							</div>
							<div className="grid gap-4 md:grid-cols-2">
								<Form.Item name="readerUsername" label="Reader 用户名">
									<Input placeholder="数据库账号" />
								</Form.Item>
								<Form.Item name="readerPassword" label="Reader 密码">
									<Input.Password placeholder="******" />
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
									rules={[{ required: true, message: "请输入 Writer 配置" }, { validator: jsonValidator("Writer 配置") }]}
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
				</Form>
			</Card>
		</div>
	);
}
