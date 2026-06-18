import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import {
	Alert,
	Button,
	Card,
	Divider,
	Dropdown,
	Input,
	Modal,
	Select,
	Space,
	Switch,
	Tag,
	Typography,
	message,
} from "antd";
import { CompactTable } from "@/components/table";
import { PageHeader } from "@/components/page-header";
import { CheckCircleOutlined, CloseCircleOutlined, SearchOutlined, RollbackOutlined } from "@ant-design/icons";
import RollbackImpactModal, { type RollbackRequest } from "@/components/rollback/RollbackImpactModal";
import dataSourcesService, {
	type ConnectionTestResult,
	type InfraDataSource,
	type OdsGenerationPreviewResponse,
	type OdsGenerationRequest,
	type OdsPrecheckResponse,
	type OdsSourceColumnRequest,
	type OdsTablePlan,
	type SchemaDiscoverColumn,
	type SchemaDiscoverResponse,
	type SchemaDiscoverTable,
} from "@/api/services/dataSourcesService";
import { createIngestionTask } from "@/api/platformApi";
import { formatTime } from "@/utils/textUtils";
import DataSourceFormModal from "./DataSourceFormModal";
import {
	inferConnectorKey,
	isAdminManagedSource,
	isApiSourceType,
	isFileSource,
	readApiBaseUrl,
} from "./dataSources/helpers";

const { Text } = Typography;

const normalizeOdsCode = (value?: string, fallback = "src") => {
	const text = String(value || "").trim().toLowerCase();
	const safe = text.replace(/[^a-z0-9_]+/g, "_").replace(/_+/g, "_").replace(/^_|_$/g, "");
	if (!safe) return fallback;
	return /^\d/.test(safe) ? `c_${safe}` : safe;
};

type OdsColumnOverride = Pick<OdsSourceColumnRequest, "include" | "targetName" | "targetDataType">;

type OdsColumnOverrides = Record<string, Record<string, OdsColumnOverride>>;

const resolveTableKey = (table: Pick<SchemaDiscoverTable, "schema" | "name">) => `${table.schema || ""}.${table.name}`;

const toOdsSourceTable = (
	table: SchemaDiscoverTable,
	overrides?: Record<string, OdsColumnOverride>
) => ({
	schema: table.schema,
	name: table.name,
	comment: table.comment,
	primaryKeys: table.primaryKeys || [],
	incrementalCandidates: table.incrementalCandidates || [],
	columns: (table.columns || []).map((column) => {
		const override = overrides?.[column.name] || {};
		return {
			name: column.name,
			include: override.include === false ? false : undefined,
			targetName: normalizeOdsCode(override.targetName || "", "") || undefined,
			dataType: column.dataType,
			nativeType: column.nativeType,
			targetDataType: String(override.targetDataType || "").trim() || undefined,
			nullable: column.nullable,
			comment: column.comment,
			primaryKey: column.primaryKey,
			indexed: column.indexed,
			incrementalCandidate: column.incrementalCandidate,
		};
	}),
});

type OdsWizardConfig = {
	odsSchema: string;
	systemCode: string;
	bizCode: string;
	syncMode: string;
	entityCode?: string;
};

const ODS_TYPE_OPTIONS = [
	{ label: "自动映射", value: "" },
	{ label: "text", value: "text" },
	{ label: "varchar(255)", value: "varchar(255)" },
	{ label: "integer", value: "integer" },
	{ label: "bigint", value: "bigint" },
	{ label: "numeric(18,2)", value: "numeric(18,2)" },
	{ label: "boolean", value: "boolean" },
	{ label: "date", value: "date" },
	{ label: "timestamp", value: "timestamp" },
	{ label: "jsonb", value: "jsonb" },
];

const defaultOdsWizardConfig = (record?: InfraDataSource | null): OdsWizardConfig => ({
	odsSchema: "ods",
	systemCode: normalizeOdsCode(record?.connectorKey || record?.type || record?.name),
	bizCode: "default",
	syncMode: "full_refresh",
});

const errorMessage = (error: unknown, fallback: string) => {
	if (error instanceof Error && error.message) return error.message;
	if (typeof error === "string" && error.trim()) return error;
	return fallback;
};

const resolveConnectorCapability = (record: InfraDataSource) => {
	if (record.jdbcUrl) {
		return {
			color: "green",
			text: "JDBC 正式",
			description: "支持连接测试、Schema 探测、ODS 预检和入湖任务生成",
		};
	}
	if (isApiSourceType(record.type)) {
		return {
			color: "blue",
			text: "API 预览",
			description: "支持连接测试和接口契约配置；ODS 生成需通过入湖任务绑定资源路径",
		};
	}
	if (isFileSource(record.type)) {
		return {
			color: "gold",
			text: "文件预览",
			description: "支持文件接入元数据登记；Schema 探测和 ODS 预检不按 JDBC 路径执行",
		};
	}
	return {
		color: "default",
		text: "需补契约",
		description: "请补齐 readerType、资源路径或 JDBC 地址后再进入黄金链路",
	};
};

export default function DataSourcesPage() {
	const navigate = useNavigate();
	const [searchParams, setSearchParams] = useSearchParams();
	const [list, setList] = useState<InfraDataSource[]>([]);
	const [loading, setLoading] = useState(false);
	const [listError, setListError] = useState<string | null>(null);
	const [formOpen, setFormOpen] = useState(false);
	const [initialConnectorKey, setInitialConnectorKey] = useState<string | undefined>();
	const [testingId, setTestingId] = useState<string | null>(null);
	const [schemaDiscoveringId, setSchemaDiscoveringId] = useState<string | null>(null);
	const [schemaModalOpen, setSchemaModalOpen] = useState(false);
	const [schemaDiscoverSource, setSchemaDiscoverSource] = useState<InfraDataSource | null>(null);
	const [schemaDiscoverResult, setSchemaDiscoverResult] = useState<SchemaDiscoverResponse | null>(null);
	const [odsPreviewingKey, setOdsPreviewingKey] = useState<string | null>(null);
	const [odsApplying, setOdsApplying] = useState(false);
	const [syncTaskCreating, setSyncTaskCreating] = useState(false);
	const [odsPrechecking, setOdsPrechecking] = useState(false);
	const [odsPreview, setOdsPreview] = useState<OdsGenerationPreviewResponse | null>(null);
	const [odsPrecheck, setOdsPrecheck] = useState<OdsPrecheckResponse | null>(null);
	const [odsRequest, setOdsRequest] = useState<OdsGenerationRequest | null>(null);
	const [odsConfig, setOdsConfig] = useState<OdsWizardConfig>(defaultOdsWizardConfig());
	const [odsColumnOverrides, setOdsColumnOverrides] = useState<OdsColumnOverrides>({});
	const [rollbackOpen, setRollbackOpen] = useState(false);
	const [rollbackRequest, setRollbackRequest] = useState<RollbackRequest | null>(null);

	const loadList = async () => {
		setLoading(true);
		try {
			const data = await dataSourcesService.list();
			setList(Array.isArray(data) ? data : []);
			setListError(null);
		} catch (error) {
			setListError(errorMessage(error, "数据源列表加载失败，请检查平台服务、权限或数据库连接状态"));
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		loadList();
	}, []);

	const openCreate = useCallback((connectorKey?: string) => {
		setInitialConnectorKey(connectorKey);
		setFormOpen(true);
	}, []);

	const closeCreate = useCallback(() => {
		setFormOpen(false);
		setInitialConnectorKey(undefined);
	}, []);

	useEffect(() => {
		const createRequested = searchParams.get("create") === "1";
		if (!createRequested) return;
		openCreate(searchParams.get("connectorKey") || undefined);
		const next = new URLSearchParams(searchParams);
		next.delete("create");
		next.delete("connectorKey");
		setSearchParams(next, { replace: true });
	}, [openCreate, searchParams, setSearchParams]);

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
		if (result.errorType) details.push(`失败类型：${result.errorType}`);
		if (result.suggestion) details.push(`处理建议：${result.suggestion}`);
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
		if (!record.jdbcUrl && !isApiSourceType(record.type)) {
			message.warning("当前数据源类型暂不支持连接测试");
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

	const showSchemaDiscoverResult = (record: InfraDataSource, result: SchemaDiscoverResponse) => {
		setSchemaDiscoverSource(record);
		setSchemaDiscoverResult(result);
		setOdsConfig(defaultOdsWizardConfig(record));
		setOdsPreview(null);
		setOdsPrecheck(null);
		setOdsRequest(null);
		setOdsColumnOverrides({});
		setSchemaModalOpen(true);
	};

	const updateOdsConfig = (patch: Partial<OdsWizardConfig>) => {
		setOdsConfig((prev) => ({ ...prev, ...patch }));
		setOdsPreview(null);
		setOdsPrecheck(null);
		setOdsRequest(null);
	};

	const updateOdsColumnOverride = (
		table: SchemaDiscoverTable,
		column: SchemaDiscoverColumn,
		patch: OdsColumnOverride
	) => {
		const tableKey = resolveTableKey(table);
		setOdsColumnOverrides((prev) => {
			const current = prev[tableKey]?.[column.name] || {};
			return {
				...prev,
				[tableKey]: {
					...(prev[tableKey] || {}),
					[column.name]: { ...current, ...patch },
				},
			};
		});
		setOdsPreview(null);
		setOdsPrecheck(null);
		setOdsRequest(null);
	};

	const buildOdsRequest = (record: InfraDataSource, tables: SchemaDiscoverTable[]): OdsGenerationRequest => ({
		odsSchema: normalizeOdsCode(odsConfig.odsSchema, "ods"),
		systemCode: normalizeOdsCode(odsConfig.systemCode || record.connectorKey || record.type || record.name),
		bizCode: normalizeOdsCode(odsConfig.bizCode || "default", "default"),
		entityCode: tables.length === 1 && odsConfig.entityCode ? normalizeOdsCode(odsConfig.entityCode, tables[0].name) : undefined,
		includeTechnicalColumns: true,
		includeRawJson: true,
		syncMode: odsConfig.syncMode || "full_refresh",
		tables: tables.map((table) => toOdsSourceTable(table, odsColumnOverrides[resolveTableKey(table)])),
	});

	const resolveCommonIncrementalCandidate = (tables?: OdsGenerationRequest["tables"]) => {
		if (!tables?.length) return "";
		const originalNames = new Map<string, string>();
		const normalizeCandidates = (table: OdsGenerationRequest["tables"][number]) =>
			(table.incrementalCandidates || [])
				.filter(Boolean)
				.map((candidate) => {
					const normalized = String(candidate).trim().toLowerCase();
					originalNames.set(normalized, String(candidate).trim());
					return normalized;
				});
		let commonKeys = normalizeCandidates(tables[0]);
		for (const table of tables.slice(1)) {
			const candidates = normalizeCandidates(table);
			commonKeys = commonKeys.filter((candidate) => candidates.includes(candidate));
		}
		if (!commonKeys?.length) return "";
		for (const preferred of ["updated_at", "update_time", "modified_at", "modify_time", "last_updated_at"]) {
			if (commonKeys.includes(preferred)) return originalNames.get(preferred) || preferred;
		}
		const first = commonKeys[0];
		return originalNames.get(first) || first;
	};

	const handleOdsPreview = async (record: InfraDataSource, tables: SchemaDiscoverTable[], loadingKey?: string) => {
		if (!tables.length) {
			message.warning("请选择至少一张表");
			return;
		}
		const request = buildOdsRequest(record, tables);
		const key = loadingKey || tables.map((table) => `${table.schema || ""}.${table.name}`).join("|");
		setOdsPreviewingKey(key);
		try {
			const result = await dataSourcesService.odsPreview(record.id, request);
			setOdsRequest(request);
			setOdsPreview(result);
			setOdsPrecheck(null);
		} catch {
			// handled by global interceptor
		} finally {
			setOdsPreviewingKey(null);
		}
	};

	const renderOdsPrecheckSummary = (result: OdsPrecheckResponse) => {
		const failed = (result.rules || []).filter((rule) => String(rule.status).toUpperCase() === "FAIL");
		const warned = (result.rules || []).filter((rule) => String(rule.status).toUpperCase() === "WARN");
		const rows = [...failed, ...warned].slice(0, 6);
		return (
			<div className="space-y-2">
				<div>
					规则 {result.totalRules || 0} 条，通过 {result.passedRules || 0}，警告 {result.warningRules || 0}，失败 {result.failedRules || 0}。
				</div>
				{rows.length ? (
					<div className="space-y-1 text-xs text-slate-500">
						{rows.map((rule) => (
							<div key={`${rule.code}.${rule.target}`}>
								<Tag color={String(rule.status).toUpperCase() === "FAIL" ? "red" : "gold"}>{rule.status}</Tag>
								{rule.target ? `${rule.target}：` : ""}
								{rule.message}
								{rule.suggestion ? `；建议：${rule.suggestion}` : ""}
							</div>
						))}
					</div>
				) : null}
			</div>
		);
	};

	const runOdsPrecheck = async (silent = false) => {
		if (!schemaDiscoverSource || !odsRequest) {
			message.warning("请先预览 ODS 生成结果");
			return null;
		}
		setOdsPrechecking(true);
		try {
			const result = await dataSourcesService.odsPrecheck(schemaDiscoverSource.id, odsRequest);
			setOdsPrecheck(result);
			if (!silent) {
				const status = String(result?.status || "").toUpperCase();
				if (status === "FAIL") {
					Modal.error({ title: "提交前预检失败", content: renderOdsPrecheckSummary(result), width: 640 });
				} else if (status === "WARN") {
					Modal.warning({ title: "提交前预检存在警告", content: renderOdsPrecheckSummary(result), width: 640 });
				} else {
					Modal.success({ title: "提交前预检通过", content: renderOdsPrecheckSummary(result), width: 640 });
				}
			}
			return result;
		} catch {
			return null;
		} finally {
			setOdsPrechecking(false);
		}
	};

	const confirmWarnPrecheck = (result: OdsPrecheckResponse) =>
		new Promise<boolean>((resolve) => {
			Modal.confirm({
				title: "预检存在警告，是否继续生成同步任务？",
				content: renderOdsPrecheckSummary(result),
				okText: "继续生成",
				cancelText: "返回调整",
				width: 680,
				onOk: () => resolve(true),
				onCancel: () => resolve(false),
			});
		});

	const ensureOdsPrecheckBeforeCreate = async () => {
		const result = await runOdsPrecheck(true);
		if (!result) return false;
		const status = String(result.status || "").toUpperCase();
		if (status === "FAIL") {
			Modal.error({ title: "提交前预检失败", content: renderOdsPrecheckSummary(result), width: 640 });
			return false;
		}
		if (status === "WARN") {
			return confirmWarnPrecheck(result);
		}
		return true;
	};

	const handleOdsApply = async () => {
		if (!schemaDiscoverSource || !odsRequest) {
			message.warning("请先预览 ODS 生成结果");
			return;
		}
		setOdsApplying(true);
		try {
			const result = await dataSourcesService.odsApply(schemaDiscoverSource.id, odsRequest);
			message.success(
				`已生成 ${result?.mappingsUpserted || 0} 个 ODS 映射，写入 ${result?.columnsUpserted || 0} 个字段`
			);
			setOdsPreview((prev) => (prev ? { ...prev, warnings: result?.warnings || prev.warnings } : prev));
		} catch {
			// handled by global interceptor
		} finally {
			setOdsApplying(false);
		}
	};

	const handleCreateSyncTask = async () => {
		if (!schemaDiscoverSource || !odsRequest) {
			message.warning("请先预览 ODS 生成结果");
			return;
		}
		if (
			["incremental", "timestamp_incremental"].includes(String(odsRequest.syncMode || "").toLowerCase()) &&
			!resolveCommonIncrementalCandidate(odsRequest.tables)
		) {
			message.warning("增量同步需要所选表存在相同的增量字段候选");
			return;
		}
		setSyncTaskCreating(true);
		try {
			const precheckOk = await ensureOdsPrecheckBeforeCreate();
			if (!precheckOk) return;
			const applyResult = await dataSourcesService.odsApply(schemaDiscoverSource.id, odsRequest);
			const draft = await dataSourcesService.syncTaskDraft(schemaDiscoverSource.id, odsRequest);
			if (!draft?.payload) {
				throw new Error("未生成同步任务配置");
			}
			const createResult: any = await createIngestionTask(draft.payload);
			const task = createResult?.task || createResult?.data?.task || createResult?.data;
			const taskId = task?.id ? ` #${task.id}` : "";
			message.success(`已生成同步任务${taskId}：${draft.taskName || task?.name || "未命名任务"}`);
			setOdsPreview((prev) => (prev ? { ...prev, warnings: applyResult?.warnings || prev.warnings } : prev));
		} catch (error: any) {
			message.error(error?.message || "生成同步任务失败");
		} finally {
			setSyncTaskCreating(false);
		}
	};

	const renderOdsColumnEditor = (table: SchemaDiscoverTable) => {
		const tableKey = resolveTableKey(table);
		const overrides = odsColumnOverrides[tableKey] || {};
		const columns = table.columns || [];
		if (!columns.length) {
			return null;
		}
		return (
			<details className="mt-2">
				<summary className="cursor-pointer text-xs text-slate-600">字段选择、重命名和类型覆盖</summary>
				<div className="mt-2">
					<CompactTable<SchemaDiscoverColumn>
						size="small"
						rowKey="name"
						pagination={false}
						dataSource={columns}
						scroll={{ x: 840, y: 240 }}
						columns={[
							{
								title: "包含",
								key: "include",
								width: 72,
								render: (_: any, column: SchemaDiscoverColumn) => (
									<Switch
										size="small"
										checked={overrides[column.name]?.include !== false}
										onChange={(checked) => updateOdsColumnOverride(table, column, { include: checked })}
									/>
								),
							},
							{
								title: "源字段",
								dataIndex: "name",
								sorter: (a, b) => (a.name || "").localeCompare(b.name || ""),
								key: "name",
								width: 180,
								render: (value: string, column: SchemaDiscoverColumn) => (
									<Space size={4} wrap>
										<Text code>{value}</Text>
										{column.primaryKey ? <Tag color="blue">PK</Tag> : null}
										{column.incrementalCandidate ? <Tag color="green">增量</Tag> : null}
									</Space>
								),
							},
							{
								title: "目标字段",
								key: "targetName",
								width: 180,
								render: (_: any, column: SchemaDiscoverColumn) => (
									<Input
										size="small"
										allowClear
										disabled={overrides[column.name]?.include === false}
										placeholder={normalizeOdsCode(column.name, column.name)}
										value={overrides[column.name]?.targetName}
										onChange={(event) =>
											updateOdsColumnOverride(table, column, { targetName: event.target.value })
										}
									/>
								),
							},
							{
								title: "目标类型",
								key: "targetDataType",
								width: 170,
								render: (_: any, column: SchemaDiscoverColumn) => (
									<Select
										size="small"
										className="w-full"
										showSearch
										disabled={overrides[column.name]?.include === false}
										value={overrides[column.name]?.targetDataType || ""}
										options={ODS_TYPE_OPTIONS}
										onChange={(value) => updateOdsColumnOverride(table, column, { targetDataType: value })}
									/>
								),
							},
							{
								title: "源类型",
								key: "sourceType",
								width: 160,
								render: (_: any, column: SchemaDiscoverColumn) => column.dataType || column.nativeType || "-",
							},
							{
								title: "说明",
								dataIndex: "comment",
								key: "comment",
								ellipsis: true,
								render: (value?: string) => value || "-",
							},
						]}
					/>
				</div>
			</details>
		);
	};

	const renderOdsPlan = (plan: OdsTablePlan) => (
		<div key={`${plan.odsSchema}.${plan.odsTable}`} className="rounded border border-slate-200 p-3">
			<div className="mb-2 flex flex-wrap items-center gap-2">
				<Text strong>{plan.odsSchema}.{plan.odsTable}</Text>
				<Tag color="blue">{plan.columns?.length || 0} 源字段</Tag>
				<Tag>{plan.technicalColumns?.length || 0} 技术字段</Tag>
			</div>
			<div className="mb-2 text-xs text-slate-500">
				{plan.sourceSchema ? `${plan.sourceSchema}.` : ""}{plan.sourceTable}
				{plan.incrementalCandidates?.length ? ` · 增量候选：${plan.incrementalCandidates.join(", ")}` : ""}
			</div>
			{plan.warnings?.length ? (
				<Alert type="warning" showIcon className="mb-2" message={plan.warnings.join("；")} />
			) : null}
			<div className="mb-2 text-xs text-slate-500">
				{(plan.columns || [])
					.slice(0, 10)
					.map((column) => `${column.targetName}:${column.odsType || "text"}`)
					.join(" · ") || "未生成字段"}
			</div>
			<details>
				<summary className="cursor-pointer text-xs text-slate-600">查看 DDL / dbt source 片段</summary>
				<pre className="mt-2 max-h-64 overflow-auto rounded bg-slate-950 p-3 text-xs text-slate-50">
					{`${plan.createTableSql || ""}\n\n${plan.dbtSourceYaml || ""}`}
				</pre>
			</details>
		</div>
	);

	const handleSchemaDiscover = async (record: InfraDataSource, forceRefresh = false) => {
		if (!record.jdbcUrl) {
			message.info("API / 文件数据源不适用 JDBC Schema 探测，请在入湖任务中配置资源路径后测试执行。");
			return;
		}
		setSchemaDiscoveringId(record.id);
		try {
			const result = await dataSourcesService.schemaDiscover(record.id, {
				maxTables: 100,
				sampleLimit: 0,
				includeColumns: true,
				includeIndexes: true,
				includeSample: false,
				useCache: true,
				forceRefresh,
			});
			if (!result || result.status === "FAILED") {
				Modal.error({
					title: `${record.name} Schema 探测失败`,
					content: result?.error || "未获取到探测结果",
				});
				return;
			}
			showSchemaDiscoverResult(record, result);
		} catch {
			// handled by global interceptor
		} finally {
			setSchemaDiscoveringId(null);
		}
	};

	const columns = useMemo(
			() => [
				{
					title: "名称",
					dataIndex: "name",
					key: "name",
					width: 180,
					fixed: "left" as const,
					sorter: (a: InfraDataSource, b: InfraDataSource) => (a.name || "").localeCompare(b.name || ""),
					render: (value: string, record: InfraDataSource) =>
						record.id ? (
							<a onClick={() => navigate(`/foundation/data-sources/${encodeURIComponent(String(record.id))}`)}>
								{value || "—"}
							</a>
						) : (
							value || "—"
						),
				},
				{
					title: "连接器",
					dataIndex: "connectorName",
					sorter: (a: InfraDataSource, b: InfraDataSource) => (a.connectorName || "").localeCompare(b.connectorName || ""),
					key: "connectorName",
					// connector 名称（如 "PostgreSQL JDBC Connector"）常常 25+ 字符，原 180 不够，调到 280
					width: 280,
					ellipsis: { showTitle: true },
					render: (value: string, record: InfraDataSource) => value || record.connectorKey || inferConnectorKey(record.type, record.props) || "-",
				},
				{ title: "类型", dataIndex: "type", key: "type", width: 120 },
			{
				title: "链路能力",
				key: "capability",
				width: 140,
				render: (_: any, record: InfraDataSource) => {
					const capability = resolveConnectorCapability(record);
					return <Tag color={capability.color} title={capability.description}>{capability.text}</Tag>;
				},
			},
			{
				title: "连接地址",
				dataIndex: "jdbcUrl",
				key: "jdbcUrl",
				width: 240,
				ellipsis: { showTitle: true },
				render: (value: string, record: InfraDataSource) =>
					isApiSourceType(record.type) ? readApiBaseUrl(record.props) || "-" : value || "-",
			},
			{ title: "用户名", dataIndex: "username", key: "username", width: 140 , sorter: (a: InfraDataSource, b: InfraDataSource) => (a.username || "").localeCompare(b.username || "") },
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
				sorter: (a: InfraDataSource, b: InfraDataSource) => {
					const ta = a.lastVerifiedAt ? new Date(a.lastVerifiedAt as any).getTime() : 0;
					const tb = b.lastVerifiedAt ? new Date(b.lastVerifiedAt as any).getTime() : 0;
					return ta - tb;
				},
				key: "lastVerifiedAt",
				width: 180,
				render: (value: string) => formatTime(value),
			},
			{
				title: "操作",
				key: "action",
				width: 240,
				fixed: "right" as const,
				render: (_: any, record: InfraDataSource) => {
					const adminManaged = isAdminManagedSource(record);
					const apiSource = isApiSourceType(record.type);
					// 高频按钮留在行内；低频/危险操作收进 ⋯ 下拉，把操作列从 540px 压缩到 240px
					const moreItems = [
						...(!apiSource
							? [
									{
										key: "schema-discover",
										icon: <SearchOutlined />,
										label: schemaDiscoveringId === record.id ? "探测中..." : "Schema 探测",
										disabled: schemaDiscoveringId === record.id,
										onClick: () => {
											void handleSchemaDiscover(record);
										},
									},
								]
							: []),
						...(record.id
							? [
									{
										key: "rollback",
										icon: <RollbackOutlined />,
										label: "全链路回退",
										danger: true,
										onClick: () => {
											setRollbackRequest({ level: 3, scope: "datasource", dataSourceId: record.id });
											setRollbackOpen(true);
										},
									},
								]
							: []),
					];
					return (
						<Space size={4} wrap={false}>
							<Button
								size="small"
								loading={testingId === record.id}
								onClick={() => handleTest(record)}
							>
								测试连接
							</Button>
							<Button
								size="small"
								disabled={adminManaged}
								onClick={() => navigate(`/foundation/data-sources/${encodeURIComponent(String(record.id))}`)}
							>
								编辑
							</Button>
							<Button
								size="small"
								danger
								disabled={adminManaged}
								onClick={() => handleDelete(record)}
							>
								删除
							</Button>
							{moreItems.length > 0 && (
								<Dropdown menu={{ items: moreItems }} trigger={["click"]} placement="bottomRight">
									<Button size="small" aria-label="更多操作" >更多操作</Button>
								</Dropdown>
							)}
						</Space>
					);
				},
			},
		],
		[schemaDiscoveringId, testingId]
	);

	return (
		<div className="space-y-4">
			<PageHeader
				title="数据源连接"
				actions={
				<Space>
					<Button onClick={loadList} disabled={loading}>
						刷新
					</Button>
					<Button onClick={() => navigate("/foundation/connectors")}>
						连接器目录
					</Button>
					<Button onClick={() => navigate("/foundation/jdbc-drivers")}>JDBC 驱动管理</Button>
					<Button onClick={() => navigate("/workbench?section=data-management")}>查看黄金链路</Button>
						<Button type="primary" onClick={() => openCreate()}>
						新建数据源
					</Button>
				</Space>
				}
			/>
			<Card title="数据源连接">
			{listError ? (
				<Alert
					type="error"
					showIcon
					className="mb-3"
					message="数据源列表加载失败"
					description={listError}
					action={
						<Button size="small" onClick={() => void loadList()}>
							重试
						</Button>
					}
				/>
			) : null}
			<CompactTable
				rowKey="id"
				columns={columns as any}
				dataSource={list}
				loading={loading}
				scroll={{ x: 1820 }}
				pagination={{ defaultPageSize: 10 }}
			/>

			<Modal
				title={`${schemaDiscoverSource?.name || "数据源"} Schema 探测`}
				open={schemaModalOpen}
				onCancel={() => setSchemaModalOpen(false)}
				width={920}
				footer={[
					<Button
						key="refresh"
						loading={Boolean(schemaDiscoverSource && schemaDiscoveringId === schemaDiscoverSource.id)}
						onClick={() => schemaDiscoverSource && handleSchemaDiscover(schemaDiscoverSource, true)}
					>
						重新探测
					</Button>,
					<Button key="close" onClick={() => setSchemaModalOpen(false)}>
						关闭
					</Button>,
					<Button
						key="precheck"
						disabled={!odsPreview}
						loading={odsPrechecking}
						onClick={() => void runOdsPrecheck(false)}
					>
						提交前预检
					</Button>,
					<Button
						key="apply"
						type="primary"
						disabled={!odsPreview}
						loading={odsApplying}
						onClick={handleOdsApply}
					>
						生成 ODS 映射与 dbt source
					</Button>,
					<Button
						key="task"
						type="primary"
						disabled={!odsPreview}
						loading={syncTaskCreating}
						onClick={handleCreateSyncTask}
					>
						生成同步任务
					</Button>,
				]}
			>
				<div className="space-y-4">
					<div className="flex flex-wrap items-center gap-2 text-xs text-slate-500">
						<span>
							{schemaDiscoverResult?.databaseProduct || "未知数据库"} · {schemaDiscoverResult?.tables?.length || 0} 张表 · {schemaDiscoverResult?.elapsedMs ?? 0} ms
						</span>
						{schemaDiscoverResult?.cached ? <Tag color="gold">缓存</Tag> : <Tag color="green">实时</Tag>}
					</div>
					{schemaDiscoverResult?.drift ? (
						<Alert
							type="warning"
							showIcon
							message={`Schema drift：新增 ${schemaDiscoverResult.drift.addedTables || 0} 表，删除 ${schemaDiscoverResult.drift.removedTables || 0} 表，变更 ${schemaDiscoverResult.drift.changedTables || 0} 表`}
							description={schemaDiscoverResult.drift.detailsJson ? (
								<details>
									<summary className="cursor-pointer">查看差异详情</summary>
									<pre className="mt-2 max-h-48 overflow-auto text-xs">{schemaDiscoverResult.drift.detailsJson}</pre>
								</details>
							) : undefined}
						/>
					) : null}
					{odsPrecheck ? (
						<Alert
							type={
								String(odsPrecheck.status).toUpperCase() === "FAIL"
									? "error"
									: String(odsPrecheck.status).toUpperCase() === "WARN"
										? "warning"
										: "success"
							}
							showIcon
							message={`提交前预检：${odsPrecheck.status}`}
							description={`规则 ${odsPrecheck.totalRules || 0} 条，通过 ${odsPrecheck.passedRules || 0}，警告 ${odsPrecheck.warningRules || 0}，失败 ${odsPrecheck.failedRules || 0}`}
						/>
					) : null}
					<div className="rounded border border-slate-200 p-3">
						<div className="mb-3 flex flex-wrap items-center gap-2">
							<Text strong>任务生成配置</Text>
							{["incremental", "timestamp_incremental"].includes(String(odsConfig.syncMode || "").toLowerCase()) && odsRequest ? (
								<Tag color={resolveCommonIncrementalCandidate(odsRequest.tables) ? "green" : "red"}>
									增量字段：{resolveCommonIncrementalCandidate(odsRequest.tables) || "未识别"}
								</Tag>
							) : null}
						</div>
						<div className="grid gap-3 md:grid-cols-4">
							<label className="space-y-1">
								<Text type="secondary">ODS Schema</Text>
								<Input
									value={odsConfig.odsSchema}
									onChange={(event) => updateOdsConfig({ odsSchema: event.target.value })}
								/>
							</label>
							<label className="space-y-1">
								<Text type="secondary">来源系统编码</Text>
								<Input
									value={odsConfig.systemCode}
									onChange={(event) => updateOdsConfig({ systemCode: event.target.value })}
								/>
							</label>
							<label className="space-y-1">
								<Text type="secondary">业务编码</Text>
								<Input
									value={odsConfig.bizCode}
									onChange={(event) => updateOdsConfig({ bizCode: event.target.value })}
								/>
							</label>
							<label className="space-y-1">
								<Text type="secondary">同步模式</Text>
								<Select
									className="w-full"
									value={odsConfig.syncMode}
									options={[
										{ label: "全量覆盖", value: "full_refresh" },
										{ label: "全量追加", value: "append" },
										{ label: "时间戳增量", value: "incremental" },
										{ label: "主键增量", value: "primary_key_incremental" },
									]}
									onChange={(value) => updateOdsConfig({ syncMode: value })}
								/>
							</label>
						</div>
					</div>
					<div className="max-h-[360px] overflow-auto">
						{(schemaDiscoverResult?.tables || []).slice(0, 20).map((table) => {
							const key = `${table.schema || ""}.${table.name}`;
							return (
								<div key={key} className="mb-3 rounded border border-slate-200 p-3">
									<div className="mb-2 flex flex-wrap items-center gap-2">
										<Text strong>
											{table.schema ? `${table.schema}.` : ""}
											{table.name}
										</Text>
										<Tag>{table.view ? "VIEW" : table.type || "TABLE"}</Tag>
										<Tag color="blue">{table.columns?.length || 0} 列</Tag>
										<Button
											size="small"
											loading={odsPreviewingKey === key}
											onClick={() => schemaDiscoverSource && handleOdsPreview(schemaDiscoverSource, [table])}
										>
											预览 ODS
										</Button>
									</div>
									{table.primaryKeys?.length ? (
										<div className="mb-1 text-xs">主键：{table.primaryKeys.join(", ")}</div>
									) : null}
									{table.incrementalCandidates?.length ? (
										<div className="mb-1 text-xs">增量候选：{table.incrementalCandidates.join(", ")}</div>
									) : null}
									<div className="text-xs text-slate-500">
										{(table.columns || [])
											.slice(0, 8)
											.map((column) => `${column.name}:${column.dataType || column.nativeType || "unknown"}`)
											.join(" · ") || "未读取字段"}
									</div>
									{renderOdsColumnEditor(table)}
								</div>
							);
						})}
						{(schemaDiscoverResult?.tables || []).length > 20 ? <Text type="secondary">仅展示前 20 张表。</Text> : null}
					</div>
					{schemaDiscoverSource && (schemaDiscoverResult?.tables || []).length > 1 ? (
						<Button
							loading={odsPreviewingKey === "__all__"}
							onClick={() => {
								const tables = (schemaDiscoverResult?.tables || []).filter((table) => !table.view).slice(0, 20);
								void handleOdsPreview(schemaDiscoverSource, tables, "__all__");
							}}
						>
							批量预览前 20 张表
						</Button>
					) : null}
					{odsPreview?.tables?.length ? (
						<div className="space-y-3">
							<Divider />
							<div className="flex flex-wrap items-center gap-2">
								<Text strong>ODS 生成预览</Text>
								<Tag color="green">{odsPreview.tables.length} 张表</Tag>
							</div>
							{odsPreview.warnings?.length ? (
								<Alert type="warning" showIcon message={odsPreview.warnings.join("；")} />
							) : null}
							<div className="space-y-3">{odsPreview.tables.map(renderOdsPlan)}</div>
						</div>
					) : null}
				</div>
			</Modal>

			<DataSourceFormModal
					open={formOpen}
					editing={null}
					initialConnectorKey={initialConnectorKey}
					onClose={closeCreate}
					onSaved={loadList}
				/>

			<RollbackImpactModal
				open={rollbackOpen}
				request={rollbackRequest}
				onClose={() => setRollbackOpen(false)}
				onSuccess={() => { loadList(); }}
			/>
			</Card>
		</div>
	);
}
