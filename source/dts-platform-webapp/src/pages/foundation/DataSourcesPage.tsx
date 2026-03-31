import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { getOrgTree, type OrgNode } from "@/api/services/directoryService";
import {
	Alert,
	Button,
	Card,
	Divider,
	Form,
	Input,
	InputNumber,
	Modal,
	Select,
	Space,
	Switch,
	Table,
	Tag,
	Typography,
	Upload,
	message,
} from "antd";
import { PlusOutlined, ReloadOutlined, EditOutlined, DeleteOutlined, ExperimentOutlined, CheckCircleOutlined, CloseCircleOutlined } from "@ant-design/icons";
import RollbackImpactModal, { type RollbackRequest } from "@/components/rollback/RollbackImpactModal";
import dataSourcesService, {
	type ConnectionTestResult,
	type DataSourceUpsertPayload,
	type DataSourceUpdateImpact,
	type ExcelImportParseResponse,
	type ExcelImportPrepareResponse,
	type InfraDataSource,
} from "@/api/services/dataSourcesService";
import jdbcDriversService, { type InfraJdbcDriver } from "@/api/services/jdbcDriversService";
import { formatTime } from "@/utils/textUtils";
type UploadRequestOption = Parameters<NonNullable<import("antd").UploadProps["customRequest"]>>[0];

const { Text } = Typography;

const JDBC_TYPES = new Set([
	"dm",
	"dameng",
	"postgresql",
	"postgres",
	"pg",
	"mysql",
	"mariadb",
	"oracle",
	"sqlserver",
	"mssql",
	"clickhouse",
	"hive",
	"inceptor",
	"db2",
	"sqlite",
	"jdbc",
]);

const TYPE_OPTIONS = [
	{ label: "PostgreSQL", value: "postgresql" },
	{ label: "达梦 DM", value: "dm" },
	{ label: "ClickHouse", value: "clickhouse" },
	{ label: "MySQL / MariaDB", value: "mysql" },
	{ label: "Oracle", value: "oracle" },
	{ label: "SQLServer", value: "sqlserver" },
	{ label: "Hive", value: "hive" },
	{ label: "Inceptor", value: "inceptor" },
	{ label: "Excel", value: "excel" },
	{ label: "CSV", value: "csv" },
	{ label: "JSON 文件", value: "json" },
	{ label: "API", value: "api" },
	{ label: "通用 JDBC", value: "jdbc" },
];

const normalizeType = (value?: string) => String(value || "").trim().toLowerCase();

const isJdbcType = (type?: string, jdbcUrl?: string) => {
	if (jdbcUrl) return true;
	const normalized = normalizeType(type);
	if (!normalized) return false;
	if (normalized.includes("jdbc")) return true;
	return JDBC_TYPES.has(normalized);
};

const isFileSource = (type?: string) => {
	const normalized = normalizeType(type);
	return normalized === "excel" || normalized === "csv";
};

const isAdminManagedSource = (source?: InfraDataSource | null) => {
	if (!source) return false;
	// Check props flag (set by BiadminDataSourceInitializer)
	if (source.props?.source === "admin-data-lake") return true;
	// Fallback: match by name for data sources created before the props fix
	if (source.name === "数仓 (biadmin)") return true;
	return false;
};

const parseJson = (value?: string) => {
	const text = String(value || "").trim();
	if (!text) return undefined;
	return JSON.parse(text);
};

const omitReaderType = (props?: Record<string, any>) => {
	if (!props) return undefined;
	const { readerType, reader, driverClass, driverVersion, ...rest } = props;
	return rest;
};

export default function DataSourcesPage() {
	const navigate = useNavigate();
	const [list, setList] = useState<InfraDataSource[]>([]);
	const [loading, setLoading] = useState(false);
	const [drivers, setDrivers] = useState<InfraJdbcDriver[]>([]);
	const [driversLoading, setDriversLoading] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [saving, setSaving] = useState(false);
	const [testingId, setTestingId] = useState<string | null>(null);
	const [editing, setEditing] = useState<InfraDataSource | null>(null);
	const [excelModalOpen, setExcelModalOpen] = useState(false);
	const [excelUploading, setExcelUploading] = useState(false);
	const [excelParsing, setExcelParsing] = useState(false);
	const [excelPrepared, setExcelPrepared] = useState<ExcelImportPrepareResponse | null>(null);
	const [excelSheetName, setExcelSheetName] = useState<string | undefined>();
	const [excelHeaderRow, setExcelHeaderRow] = useState(1);
	const [excelDataStartRow, setExcelDataStartRow] = useState(2);
	const [excelDelimiter, setExcelDelimiter] = useState(",");
	const [excelDateFormat, setExcelDateFormat] = useState("yyyy-MM-dd HH:mm:ss");
	const [excelSkipErrors, setExcelSkipErrors] = useState(true);
	const [excelFillMerged, setExcelFillMerged] = useState(true);
	const [excelParseResult, setExcelParseResult] = useState<ExcelImportParseResponse | null>(null);
	const [rollbackOpen, setRollbackOpen] = useState(false);
	const [rollbackRequest, setRollbackRequest] = useState<RollbackRequest | null>(null);
	const [deptOptions, setDeptOptions] = useState<{ label: string; value: string }[]>([]);
	const [form] = Form.useForm();

	const loadDepts = useCallback(async () => {
		try {
			const tree = (await getOrgTree()) as OrgNode[];
			const flat: { label: string; value: string }[] = [];
			const walk = (nodes: OrgNode[]) => {
				for (const n of nodes) {
					if (n.deptCode) flat.push({ label: n.name, value: n.deptCode });
					if (n.children) walk(n.children);
				}
			};
			walk(Array.isArray(tree) ? tree : []);
			setDeptOptions(flat);
		} catch { /* ignore */ }
	}, []);

	const showImpact = (impact: DataSourceUpdateImpact | null) => {
		if (!impact || !impact.connectionChanged) {
			return;
		}
		const affected = impact.affectedTasks ?? 0;
		if (affected <= 0) {
			message.info("数据源连接已更新，未发现关联入湖任务。");
			return;
		}
		Modal.info({
			title: "数据源连接已更新",
			content: (
				<div className="space-y-2">
					<div>已影响入湖任务：{affected} 个。</div>
					<div>已登记接入变更：{impact.changeLogCreated ?? 0} 条。</div>
					<div className="text-xs text-slate-500">请前往“接入变更记录”确认任务变更影响。</div>
				</div>
			),
		});
	};

	const loadList = async () => {
		setLoading(true);
		try {
			const data = await dataSourcesService.list();
			setList(Array.isArray(data) ? data : []);
		} catch {
			setList([]);
		} finally {
			setLoading(false);
		}
	};

	const loadDrivers = async () => {
		setDriversLoading(true);
		try {
			const data = await jdbcDriversService.list();
			setDrivers(Array.isArray(data) ? data : []);
		} catch {
			setDrivers([]);
		} finally {
			setDriversLoading(false);
		}
	};

	useEffect(() => {
		loadList();
		loadDrivers();
		loadDepts();
	}, []);

	useEffect(() => {
		if (!editing || drivers.length === 0) return;
		const match = resolveDriverMatch(editing, drivers);
		if (match?.id) {
			form.setFieldsValue({ driverId: match.id });
		}
	}, [editing, drivers, form]);

	const openCreate = () => {
		setEditing(null);
		form.resetFields();
		setExcelParseResult(null);
		setModalOpen(true);
		loadDrivers();
	};

	const resolveDriverMatch = (record: InfraDataSource | null, driverList: InfraJdbcDriver[]) => {
		if (!record || !Array.isArray(driverList) || driverList.length === 0) return undefined;
		const props = record.props || {};
		const driverClass = String(props?.driverClass || "").trim().toLowerCase();
		const driverVersion = String(props?.driverVersion || "").trim().toLowerCase();
		if (!driverClass && !driverVersion) return undefined;
		return driverList.find((driver) => {
			const classMatch =
				driverClass &&
				driver.driverClass &&
				driver.driverClass.trim().toLowerCase() === driverClass;
			const versionMatch =
				driverVersion &&
				(driver.fileName?.trim().toLowerCase() === driverVersion ||
					driver.version?.trim().toLowerCase() === driverVersion);
			return classMatch || versionMatch;
		});
	};

	const openEdit = (record: InfraDataSource) => {
		if (isAdminManagedSource(record)) {
			message.info("默认数据湖由系统管理端维护，平台侧不支持编辑");
			return;
		}
		setEditing(record);
		form.setFieldsValue({
			name: record.name,
			type: record.type,
			jdbcUrl: record.jdbcUrl,
			username: record.username,
			description: record.description,
			readerType: record.props?.readerType || record.props?.reader,
			driverClass: record.props?.driverClass,
			driverVersion: record.props?.driverVersion,
			propsJson: record.props ? JSON.stringify(omitReaderType(record.props), null, 2) : "",
		});
		const match = resolveDriverMatch(record, drivers);
		if (match?.id) {
			form.setFieldsValue({ driverId: match.id });
		}
		setExcelParseResult(null);
		setModalOpen(true);
		loadDrivers();
	};

	const handleDelete = (record: InfraDataSource) => {
		if (isAdminManagedSource(record)) {
			message.info("默认数据湖由系统管理端维护，平台侧不支持删除");
			return;
		}
		Modal.confirm({
			title: "确认删除",
			content: `确定删除数据源 "${record.name}" 吗？`,
			okType: "danger",
			onOk: async () => {
				try {
					await dataSourcesService.remove(record.id);
					message.success("已删除数据源");
					loadList();
				} catch {
					// handled by global interceptor
				}
			},
		});
	};

	const showTestResult = (name: string, result: ConnectionTestResult) => {
		const isOk = result.success;
		const details: string[] = [];
		if (result.elapsedMillis != null) details.push(`耗时：${result.elapsedMillis} ms`);
		if (result.engineVersion) details.push(`数据库版本：${result.engineVersion}`);
		if (result.driverVersion) details.push(`驱动版本：${result.driverVersion}`);
		if (result.warnings?.length) details.push(`警告：${result.warnings.join("; ")}`);

		Modal[isOk ? "success" : "error"]({
			title: isOk ? `${name} 连接成功` : `${name} 连接失败`,
			icon: isOk ? <CheckCircleOutlined /> : <CloseCircleOutlined />,
			content: (
				<div style={{ maxHeight: 300, overflow: "auto" }}>
					{result.message && <div style={{ marginBottom: 8, wordBreak: "break-all" }}>{result.message}</div>}
					{details.length > 0 && (
						<div style={{ fontSize: 12, color: "#666" }}>
							{details.map((d, i) => (
								<div key={i}>{d}</div>
							))}
						</div>
					)}
				</div>
			),
			width: 520,
		});
	};

	const handleTest = async (record: InfraDataSource) => {
		if (!record.jdbcUrl) {
			message.warning("非 JDBC 数据源无需测试连接");
			return;
		}
		try {
			setTestingId(record.id);
			const result = await dataSourcesService.test(record.id);
			if (result?.success) {
				showTestResult(record.name, result);
			} else {
				showTestResult(record.name, result || { success: false, message: "未获取到测试结果" });
			}
			loadList();
		} catch (error: any) {
			Modal.error({
				title: `${record.name} 连接测试异常`,
				content: error?.message || "连接测试请求失败，请检查网络或服务状态。",
				width: 520,
			});
		} finally {
			setTestingId(null);
		}
	};

	const handleDriverSelect = (id?: string) => {
		if (!id) return;
		const driver = drivers.find((item) => item.id === id);
		if (!driver) return;
		const next: Record<string, string> = {};
		if (driver.driverClass) {
			next.driverClass = driver.driverClass;
		}
		const versionHint = driver.fileName || driver.version;
		if (versionHint) {
			next.driverVersion = versionHint;
		}
		form.setFieldsValue(next);
	};

	const handleSave = async () => {
		try {
			const values = await form.validateFields();
			setSaving(true);
			const jdbc = isJdbcType(values.type, values.jdbcUrl);
			let props = values.propsJson ? parseJson(values.propsJson) : undefined;
			if (values.readerType) {
				props = { ...(props || {}), readerType: values.readerType };
			}
			const selectedDriver = drivers.find((item) => item.id === values.driverId);
			const resolvedDriverClass = String(values.driverClass || selectedDriver?.driverClass || "").trim();
			const resolvedDriverVersion = String(
				values.driverVersion || selectedDriver?.fileName || selectedDriver?.version || ""
			).trim();
			if (resolvedDriverClass) {
				props = { ...(props || {}), driverClass: resolvedDriverClass };
			}
			if (resolvedDriverVersion) {
				props = { ...(props || {}), driverVersion: resolvedDriverVersion };
			}
			const payload: DataSourceUpsertPayload = {
				name: String(values.name).trim(),
				type: String(values.type).trim(),
				jdbcUrl: jdbc ? String(values.jdbcUrl || "").trim() || undefined : undefined,
				username: jdbc ? String(values.username || "").trim() || undefined : undefined,
				description: String(values.description || "").trim() || undefined,
				props: props && Object.keys(props).length ? props : undefined,
				secrets: values.password ? { password: values.password } : undefined,
			};
			if (editing) {
				const impact = await dataSourcesService.updateWithImpact(editing.id, payload);
				message.success("数据源已更新");
				showImpact(impact || null);
			} else {
				await dataSourcesService.create(payload);
				message.success("数据源已创建");
			}
			setModalOpen(false);
			loadList();
		} catch (error: any) {
			if (error?.errorFields) return;
		} finally {
			setSaving(false);
		}
	};

	const resetExcelModal = () => {
		setExcelPrepared(null);
		setExcelParseResult(null);
		setExcelSheetName(undefined);
		setExcelHeaderRow(1);
		setExcelDataStartRow(2);
		setExcelDelimiter(",");
		setExcelDateFormat("yyyy-MM-dd HH:mm:ss");
		setExcelSkipErrors(true);
		setExcelFillMerged(true);
	};

	const openExcelModal = () => {
		resetExcelModal();
		setExcelModalOpen(true);
	};

	const handleExcelUpload = async (options: UploadRequestOption) => {
		const file = options.file as File;
		if (!file) return;
		const maxSize = 200 * 1024 * 1024;
		if (file.size > maxSize) {
			message.error("文件超过 200MB 限制");
			options.onError?.(new Error("file_too_large"));
			return;
		}
		setExcelUploading(true);
		try {
			const resp = await dataSourcesService.excelPrepare(file);
			setExcelPrepared(resp);
			setExcelSheetName(resp.sheets?.[0]?.name);
			message.success("文件已上传，请选择 Sheet 并解析");
			options.onSuccess?.(resp as any);
		} catch (error: any) {
			options.onError?.(error);
		} finally {
			setExcelUploading(false);
		}
	};

	const applyExcelResultToForm = (resp: ExcelImportParseResponse) => {
		const columns = resp.columns || [];
		const readerConfig = {
			path: [resp.csvContainerPath || resp.csvPath],
			column: columns.map((col, index) => ({
				index,
				name: col.name,
				type: col.dataType || "string",
			})),
			fieldDelimiter: excelDelimiter || ",",
			encoding: "UTF-8",
			skipHeader: true,
			fileType: "csv",
		};
		form.setFieldsValue({
			readerType: "txtfilereader",
			propsJson: JSON.stringify({ readerConfig }, null, 2),
		});
	};

	const handleExcelParse = async () => {
		if (!excelPrepared?.fileId) {
			message.warning("请先上传 Excel/CSV");
			return;
		}
		setExcelParsing(true);
		try {
			const resp = await dataSourcesService.excelParse({
				fileId: excelPrepared.fileId,
				sheetName: excelSheetName,
				headerRow: excelHeaderRow,
				dataStartRow: excelDataStartRow,
				delimiter: excelDelimiter,
				previewLimit: 20,
				skipErrors: excelSkipErrors,
				fillMerged: excelFillMerged,
				dateFormat: excelDateFormat,
			});
			setExcelParseResult(resp);
			applyExcelResultToForm(resp);
			message.success("解析完成，字段配置已填充");
			setExcelModalOpen(false);
		} catch {
			// handled by global interceptor
		} finally {
			setExcelParsing(false);
		}
	};

	const columns = useMemo(
		() => [
			{ title: "名称", dataIndex: "name", key: "name", width: 180 },
			{ title: "类型", dataIndex: "type", key: "type", width: 120 },
			{ title: "JDBC URL", dataIndex: "jdbcUrl", key: "jdbcUrl", ellipsis: true },
			{ title: "用户名", dataIndex: "username", key: "username", width: 140 },
			{
				title: "状态",
				dataIndex: "status",
				key: "status",
				width: 120,
				render: (value: string) => {
					const text = value || "active";
					return <Tag color={text === "active" ? "success" : "default"}>{text}</Tag>;
				},
			},
			{
				title: "最近验证",
				dataIndex: "lastVerifiedAt",
				key: "lastVerifiedAt",
				width: 180,
				render: (value: string) => formatTime(value),
			},
			{
				title: "操作",
				key: "action",
				width: 220,
				render: (_: any, record: InfraDataSource) => {
					const adminManaged = isAdminManagedSource(record);
					return (
						<Space>
							<Button size="small" icon={<ExperimentOutlined />} loading={testingId === record.id} onClick={() => handleTest(record)}>
								测试
							</Button>
							<Button size="small" icon={<EditOutlined />} disabled={adminManaged} onClick={() => openEdit(record)}>
								编辑
							</Button>
							<Button size="small" danger icon={<DeleteOutlined />} disabled={adminManaged} onClick={() => handleDelete(record)}>
								删除
							</Button>
							{record.id && (
								<Button size="small" danger onClick={() => { setRollbackRequest({ level: 3, scope: "datasource", dataSourceId: record.id }); setRollbackOpen(true); }}>
									全链路回退
								</Button>
							)}
						</Space>
					);
				},
			},
		],
		[testingId]
	);

	const driverOptions = useMemo(
		() =>
			drivers.map((driver) => ({
				value: driver.id,
				label: `${driver.fileName}${driver.version ? ` (${driver.version})` : ""}${driver.driverClass ? ` · ${driver.driverClass}` : ""}`,
			})),
		[drivers]
	);

	const typeValue = Form.useWatch("type", form);
	const jdbcValue = Form.useWatch("jdbcUrl", form);
	const jdbcRequired = isJdbcType(typeValue, jdbcValue);
	const fileSource = isFileSource(typeValue);

	return (
		<Card
			title="数据源连接"
			extra={
				<Space>
					<Button icon={<ReloadOutlined />} onClick={loadList} disabled={loading}>
						刷新
					</Button>
					<Button onClick={() => navigate("/foundation/jdbc-drivers")}>JDBC 驱动管理</Button>
					<Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
						新增数据源
					</Button>
				</Space>
			}
		>
			<Table
				rowKey="id"
				columns={columns as any}
				dataSource={list}
				loading={loading}
				scroll={{ x: 1100 }}
				pagination={{ pageSize: 12 }}
			/>

			<Modal
				title={editing ? "编辑数据源" : "新增数据源"}
				open={modalOpen}
				onCancel={() => setModalOpen(false)}
				onOk={handleSave}
				okText="保存"
				confirmLoading={saving}
				destroyOnClose
			>
				<Form layout="vertical" form={form} preserve={false}>
					<Form.Item name="name" label="名称" rules={[{ required: true, message: "请输入名称" }]}>
						<Input placeholder="例如：ERP 数据库" />
					</Form.Item>
					<Form.Item name="type" label="类型" rules={[{ required: true, message: "请选择类型" }]}>
						<Select options={TYPE_OPTIONS} placeholder="请选择数据源类型" />
					</Form.Item>
					{jdbcRequired && (
						<Form.Item name="driverId" label="JDBC 驱动">
							<Select
								options={driverOptions}
								placeholder={driversLoading ? "驱动加载中..." : "选择驱动以自动填充"}
								loading={driversLoading}
								allowClear
								showSearch
								optionFilterProp="label"
								onChange={(value) => handleDriverSelect(value as string)}
							/>
						</Form.Item>
					)}
					{jdbcRequired && (
						<Form.Item name="driverClass" label="驱动主类">
							<Input placeholder="可自动填充，例如：org.postgresql.Driver" />
						</Form.Item>
					)}
					{jdbcRequired && (
						<Form.Item name="driverVersion" label="驱动文件/版本">
							<Input placeholder="可填 jar 文件名或版本号" />
						</Form.Item>
					)}
					<Form.Item
						name="jdbcUrl"
						label="JDBC URL"
						rules={[{ required: jdbcRequired, message: "请输入 JDBC URL" }]}
					>
						<Input placeholder="jdbc:xxx://host:port/db" />
					</Form.Item>
					<Form.Item
						name="username"
						label="用户名"
						rules={[{ required: jdbcRequired, message: "请输入用户名" }]}
					>
						<Input placeholder="数据库账号" />
					</Form.Item>
					<Form.Item
						name="password"
						label={editing ? "密码（留空保持不变）" : "密码"}
						rules={[{ required: jdbcRequired && !editing, message: "请输入密码" }]}
					>
						<Input.Password placeholder="******" />
					</Form.Item>
					{!jdbcRequired && (
						<Form.Item
							name="readerType"
							label="Reader 类型"
							rules={[{ required: true, message: "请输入 Reader 类型" }]}
						>
							<Input placeholder="例如：excelreader、httpreader" />
						</Form.Item>
					)}
					{!jdbcRequired && fileSource && (
						<Form.Item label="字段解析">
							<Space>
								<Button onClick={openExcelModal}>上传 Excel/CSV 并解析</Button>
								{excelParseResult?.columns?.length ? (
									<Text type="secondary">已解析 {excelParseResult.columns.length} 列</Text>
								) : null}
							</Space>
						</Form.Item>
					)}
					<Form.Item name="description" label="描述">
						<Input.TextArea rows={2} placeholder="可选" />
					</Form.Item>
					<Form.Item name="ownerDept" label="归属部门" rules={[{ required: true, message: "请选择归属部门" }]}>
						<Select showSearch placeholder="选择归属部门" options={deptOptions}
							filterOption={(input, option) =>
								(option?.label ?? "").toLowerCase().includes(input.toLowerCase())
							}
						/>
					</Form.Item>
					<Form.Item
						name="propsJson"
						label="扩展配置 JSON（可选）"
						rules={[
							{
								validator: async (_: any, value: string) => {
									if (!value) return Promise.resolve();
									try {
										parseJson(value);
										return Promise.resolve();
									} catch {
										return Promise.reject(new Error("JSON 格式错误"));
									}
								},
							},
						]}
					>
						<Input.TextArea rows={4} placeholder='{"readerConfig":{"column":["*"]}}' />
					</Form.Item>
					{editing?.lastError && (
						<Text type="danger">最近错误：{editing.lastError}</Text>
					)}
				</Form>
			</Modal>

			<Modal
				title="Excel/CSV 字段解析"
				open={excelModalOpen}
				onCancel={() => setExcelModalOpen(false)}
				onOk={handleExcelParse}
				okText="解析并应用"
				confirmLoading={excelParsing}
				destroyOnClose
			>
				<Space direction="vertical" style={{ width: "100%" }}>
					<Alert type="warning" showIcon message="非密模块禁止上传涉密数据" />
					<Upload.Dragger
						name="file"
						multiple={false}
						maxCount={1}
						showUploadList={false}
						accept=".xlsx,.csv"
						customRequest={handleExcelUpload}
						disabled={excelUploading}
					>
						<p className="ant-upload-drag-icon">
							<PlusOutlined />
						</p>
						<p className="ant-upload-text">点击或拖拽上传 Excel/CSV 文件（≤200MB）</p>
						<p className="ant-upload-hint">{excelPrepared?.fileName || "支持 .xlsx / .csv"}</p>
					</Upload.Dragger>

					{excelPrepared?.sheets?.length ? (
						<Form layout="vertical">
							<Form.Item label="工作表">
								<Select
									value={excelSheetName}
									onChange={(value) => setExcelSheetName(value)}
									options={excelPrepared.sheets.map((sheet) => ({
										label: sheet.name,
										value: sheet.name,
									}))}
								/>
							</Form.Item>
						</Form>
					) : null}

					<Divider />

					<Form layout="vertical">
						<Form.Item label="表头行（1-based）">
							<InputNumber min={1} value={excelHeaderRow} onChange={(v) => setExcelHeaderRow(v || 1)} />
						</Form.Item>
						<Form.Item label="数据起始行（1-based）">
							<InputNumber min={1} value={excelDataStartRow} onChange={(v) => setExcelDataStartRow(v || 2)} />
						</Form.Item>
						<Form.Item label="分隔符">
							<Input value={excelDelimiter} onChange={(e) => setExcelDelimiter(e.target.value || ",")} />
						</Form.Item>
						<Form.Item label="日期格式">
							<Input value={excelDateFormat} onChange={(e) => setExcelDateFormat(e.target.value)} />
						</Form.Item>
						<Form.Item label="合并单元格填充">
							<Switch checked={excelFillMerged} onChange={setExcelFillMerged} />
						</Form.Item>
						<Form.Item label="容错跳过">
							<Switch checked={excelSkipErrors} onChange={setExcelSkipErrors} />
						</Form.Item>
					</Form>
				</Space>
			</Modal>

			<RollbackImpactModal
				open={rollbackOpen}
				request={rollbackRequest}
				onClose={() => setRollbackOpen(false)}
				onSuccess={() => { loadList(); }}
			/>
		</Card>
	);
}
