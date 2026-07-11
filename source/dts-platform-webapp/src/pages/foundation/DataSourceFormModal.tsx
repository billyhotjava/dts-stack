import { useCallback, useEffect, useMemo, useState } from "react";
import {
	Alert,
	Button,
	Divider,
	Form,
	Input,
	InputNumber,
	Modal,
	Select,
	Space,
	Switch,
	Typography,
	message,
} from "antd";
import { PlusOutlined } from "@ant-design/icons";
import { Upload } from "@/components/upload";
import { getOrgTree, type OrgNode } from "@/api/services/directoryService";
import dataSourcesService, {
	type DataSourceUpsertPayload,
	type DataSourceUpdateImpact,
	type ExcelImportParseResponse,
	type ExcelImportPrepareResponse,
	type InfraDataSource,
} from "@/api/services/dataSourcesService";
import connectorsService, { type InfraConnector } from "@/api/services/connectorsService";
import dictionaryService, { type PlatformSystemType } from "@/api/services/dictionaryService";
import jdbcDriversService, { type InfraJdbcDriver } from "@/api/services/jdbcDriversService";
import {
	ingestionTaskAPI,
	type ApiConnectionTestResultDTO,
	type ApiAuthProviderDescriptorDTO,
	type ApiConnectorContractDTO,
} from "@/api/ingestion";
import { useRouter } from "@/routes/hooks";
import {
	TYPE_OPTIONS,
	asRecord,
	buildApiAuthFieldInput,
	cleanRecord,
	inferConnectorKey,
	isApiSourceType,
	isFileSource,
	isJdbcType,
	jsonObjectValidator,
	normalizeConnectorKey,
	omitReaderType,
	parseJson,
	parseObjectJson,
	readApiAuthConfig,
	readApiAuthProvider,
	readApiAuthSecretRefs,
	readApiBaseUrl,
	readApiConfigPart,
	stringifyJson,
} from "./dataSources/helpers";

type UploadRequestOption = Parameters<NonNullable<import("antd").UploadProps["customRequest"]>>[0];

const { Text } = Typography;

interface DataSourceFormModalProps {
	open: boolean;
	editing: InfraDataSource | null;
	initialConnectorKey?: string;
	onClose: () => void;
	onSaved: () => void;
}

const resolveDriverMatch = (record: InfraDataSource | null, driverList: InfraJdbcDriver[]) => {
	if (!record || !Array.isArray(driverList) || driverList.length === 0) return undefined;
	const props = record.props || {};
	const driverClass = String(props?.driverClass || "").trim().toLowerCase();
	const driverVersion = String(props?.driverVersion || "").trim().toLowerCase();
	if (!driverClass && !driverVersion) return undefined;
	return driverList.find((driver) => {
		const classMatch =
			driverClass && driver.driverClass && driver.driverClass.trim().toLowerCase() === driverClass;
		const versionMatch =
			driverVersion &&
			(driver.fileName?.trim().toLowerCase() === driverVersion ||
				driver.version?.trim().toLowerCase() === driverVersion);
		return classMatch || versionMatch;
	});
};

const resolveSystemTypeValue = (item: PlatformSystemType) =>
	normalizeConnectorKey(item.value || item.code || item.key || item.type);

const resolveSystemTypeLabel = (item: PlatformSystemType, value: string) =>
	String(item.label || item.displayName || item.name || value).trim();

const isSystemTypeEnabled = (item: PlatformSystemType) => {
	if (item.enabled === false) return false;
	const status = String(item.status || "").trim().toUpperCase();
	return !status || status === "ACTIVE" || status === "ENABLED" || status === "PUBLISHED";
};

const showImpact = (impact: DataSourceUpdateImpact | null) => {
	if (!impact || !impact.connectionChanged) return;
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

const buildApiAuthRefName = (fieldName: string) => `${fieldName}Ref`;

export default function DataSourceFormModal({
	open,
	editing,
	initialConnectorKey,
	onClose,
	onSaved,
}: DataSourceFormModalProps) {
	const router = useRouter();
	const [form] = Form.useForm();
	const [saving, setSaving] = useState(false);
	const [connectors, setConnectors] = useState<InfraConnector[]>([]);
	const [connectorsLoading, setConnectorsLoading] = useState(false);
	const [systemTypes, setSystemTypes] = useState<PlatformSystemType[]>([]);
	const [systemTypesLoading, setSystemTypesLoading] = useState(false);
	const [systemTypesLoaded, setSystemTypesLoaded] = useState(false);
	const [systemTypesError, setSystemTypesError] = useState<string | null>(null);
	const [drivers, setDrivers] = useState<InfraJdbcDriver[]>([]);
	const [driversLoading, setDriversLoading] = useState(false);
	const [apiContract, setApiContract] = useState<ApiConnectorContractDTO | null>(null);
	const [apiContractLoading, setApiContractLoading] = useState(false);
	const [apiTesting, setApiTesting] = useState(false);
	const [apiTestResult, setApiTestResult] = useState<ApiConnectionTestResultDTO | null>(null);
	const [deptOptions, setDeptOptions] = useState<{ label: string; value: string }[]>([]);

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
	const [excelFillMerged, setExcelFillMerged] = useState(false);
	const [excelParseResult, setExcelParseResult] = useState<ExcelImportParseResponse | null>(null);

	const loadConnectors = useCallback(async () => {
		setConnectorsLoading(true);
		try {
			const data = await connectorsService.list();
			setConnectors(Array.isArray(data) ? data : []);
		} catch {
			setConnectors([]);
		} finally {
			setConnectorsLoading(false);
		}
	}, []);

	const loadSystemTypes = useCallback(async () => {
		setSystemTypesLoading(true);
		try {
			const data = await dictionaryService.listSystemTypes();
			setSystemTypes(Array.isArray(data) ? data.filter(isSystemTypeEnabled) : []);
			setSystemTypesError(null);
		} catch {
			setSystemTypes([]);
			setSystemTypesError("系统类型字典暂不可用，当前使用内置兜底选项。");
		} finally {
			setSystemTypesLoaded(true);
			setSystemTypesLoading(false);
		}
	}, []);

	const loadDrivers = useCallback(async () => {
		setDriversLoading(true);
		try {
			const data = await jdbcDriversService.list();
			setDrivers(Array.isArray(data) ? data : []);
		} catch {
			setDrivers([]);
		} finally {
			setDriversLoading(false);
		}
	}, []);

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
		} catch {
			/* ignore */
		}
	}, []);

	const loadApiContract = useCallback(async () => {
		if (apiContract || apiContractLoading) return;
		setApiContractLoading(true);
		try {
			const contract = await ingestionTaskAPI.getApiConnectorContract();
			if (contract?.authProviders?.length) {
				setApiContract(contract);
				return;
			}
			const authProviders = await ingestionTaskAPI.getApiAuthProviders();
			setApiContract({ ...(contract || {}), authProviders });
		} catch {
			setApiContract(null);
		} finally {
			setApiContractLoading(false);
		}
	}, [apiContract, apiContractLoading]);

	// 首次打开时加载字典；后续打开复用缓存
	useEffect(() => {
		if (!open) return;
		void loadConnectors();
		void loadSystemTypes();
		void loadDrivers();
		void loadDepts();
	}, [open, loadConnectors, loadSystemTypes, loadDrivers, loadDepts]);

	// 初始化表单：editing 变化或 open 上升沿都需要刷新
	useEffect(() => {
		if (!open) return;
		setExcelParseResult(null);
		setApiTestResult(null);
		if (!editing) {
			form.resetFields();
			if (initialConnectorKey) {
				form.setFieldsValue({ connectorKey: initialConnectorKey });
			}
			return;
		}
		const props = editing.props || {};
		const apiSource = isApiSourceType(editing.type);
		const existingApiRequestPolicy = apiSource ? readApiConfigPart(props, "requestPolicy") : undefined;
		form.setFieldsValue({
			name: editing.name,
			connectorKey: editing.connectorKey || inferConnectorKey(editing.type, props),
			type: editing.type,
			jdbcUrl: editing.jdbcUrl,
			username: editing.username,
			description: editing.description,
			ownerDept: editing.ownerDept,
			readerType: editing.props?.readerType || editing.props?.reader,
			driverClass: editing.props?.driverClass,
			driverVersion: editing.props?.driverVersion,
			propsJson: editing.props ? JSON.stringify(omitReaderType(editing.props), null, 2) : "",
			apiBaseUrl: apiSource ? readApiBaseUrl(props) : undefined,
			apiAuthProvider: apiSource ? readApiAuthProvider(props) : undefined,
			apiAuthConfig: apiSource ? readApiAuthConfig(props) : undefined,
			apiAuthSecrets: {},
			apiAuthSecretRefs: apiSource ? readApiAuthSecretRefs(props) : undefined,
			apiDefaultHeadersJson: apiSource ? stringifyJson(readApiConfigPart(props, "defaultHeaders")) : "",
			apiRequestPolicyJson: apiSource ? stringifyJson(readApiConfigPart(props, "requestPolicy")) : "",
			apiAllowHttp: apiSource ? existingApiRequestPolicy?.allowHttp === true : false,
			apiRateLimitJson: apiSource ? stringifyJson(readApiConfigPart(props, "rateLimit")) : "",
			apiTlsJson: apiSource ? stringifyJson(readApiConfigPart(props, "tls")) : "",
		});
		if (apiSource) void loadApiContract();
	}, [open, editing, form, loadApiContract, initialConnectorKey]);

	// drivers 列表加载完后回填 driverId（编辑态）
	useEffect(() => {
		if (!open || !editing || drivers.length === 0) return;
		const match = resolveDriverMatch(editing, drivers);
		if (match?.id) {
			form.setFieldsValue({ driverId: match.id });
		}
	}, [open, editing, drivers, form]);

	const connectorValue = Form.useWatch("connectorKey", form);
	const typeValue = Form.useWatch("type", form);
	const jdbcValue = Form.useWatch("jdbcUrl", form);
	const apiAuthProviderValue = Form.useWatch("apiAuthProvider", form);

	const apiSource = isApiSourceType(typeValue);
	const jdbcRequired = !apiSource && isJdbcType(typeValue, jdbcValue);
	const fileSource = isFileSource(typeValue);

	// API 源类型时按需加载契约
	useEffect(() => {
		if (open && apiSource) void loadApiContract();
	}, [open, apiSource, loadApiContract]);

	const driverOptions = useMemo(
		() =>
			drivers.map((driver) => ({
				value: driver.id,
				label: `${driver.fileName}${driver.version ? ` (${driver.version})` : ""}${driver.driverClass ? ` · ${driver.driverClass}` : ""}`,
			})),
		[drivers],
	);

	const connectorOptions = useMemo(() => {
		if (!connectors.length) {
			return TYPE_OPTIONS.map((option) => ({ ...option, value: inferConnectorKey(option.value) || option.value }));
		}
		return connectors.map((connector) => ({
			value: connector.connectorKey,
			label: `${connector.name}${connector.defaultEngine ? ` · ${connector.defaultEngine}` : ""}`,
		}));
	}, [connectors]);

	const systemTypeOptions = useMemo(() => {
		const seen = new Set<string>();
		const options = systemTypes
			.map((item) => {
				const value = resolveSystemTypeValue(item);
				if (!value || seen.has(value)) return null;
				seen.add(value);
				return {
					value,
					label: resolveSystemTypeLabel(item, value),
				};
			})
			.filter(Boolean) as Array<{ value: string; label: string }>;
		return options.length ? options : TYPE_OPTIONS;
	}, [systemTypes]);

	const systemTypesFallbackActive = systemTypesLoaded && systemTypeOptions === TYPE_OPTIONS;
	const systemTypesFallbackMessage = systemTypesError || "系统类型字典为空，当前使用内置兜底选项。";

	const selectedConnector = useMemo(
		() => connectors.find((item) => item.connectorKey === connectorValue),
		[connectorValue, connectors],
	);

	const apiAuthProviders = useMemo(
		() =>
			apiContract?.authProviders?.length
				? apiContract.authProviders
				: [{ id: "none", label: "无鉴权", description: "不向请求注入任何鉴权信息", fields: [] }],
		[apiContract],
	);

	const selectedApiAuthProvider = useMemo(
		() =>
			apiAuthProviders.find(
				(item) => String(item.id).toLowerCase() === String(apiAuthProviderValue || "none").toLowerCase(),
			),
		[apiAuthProviderValue, apiAuthProviders],
	);

	const handleTypeChange = useCallback(
		(type: string) => {
			if (isApiSourceType(type)) {
				form.setFieldsValue({
					driverId: undefined,
					driverClass: undefined,
					driverVersion: undefined,
					jdbcUrl: undefined,
					username: undefined,
					password: undefined,
					readerType: apiContract?.defaultReaderType || "httpreader",
					apiAuthProvider: form.getFieldValue("apiAuthProvider") || "none",
				});
				void loadApiContract();
				return;
			}
			if (!isJdbcType(type, form.getFieldValue("jdbcUrl"))) {
				form.setFieldsValue({ readerType: form.getFieldValue("readerType") || "" });
			}
		},
		[apiContract?.defaultReaderType, form, loadApiContract],
	);

	const applyConnectorDefaults = useCallback(
		(connectorKey?: string) => {
			const connector = connectors.find((item) => item.connectorKey === connectorKey);
			const fallback = TYPE_OPTIONS.find((option) => inferConnectorKey(option.value) === connectorKey);
			if (!connector && !fallback) return;
			const nextType = connector?.sourceType || fallback?.value || connectorKey;
			if (!nextType) return;
			form.setFieldsValue({
				type: nextType,
				readerType:
					connectorKey === "http-api"
						? apiContract?.defaultReaderType || "httpreader"
						: form.getFieldValue("readerType"),
			});
			handleTypeChange(nextType);
		},
		[apiContract?.defaultReaderType, connectors, form, handleTypeChange],
	);

	useEffect(() => {
		if (!open || editing || !initialConnectorKey) return;
		form.setFieldsValue({ connectorKey: initialConnectorKey });
		applyConnectorDefaults(initialConnectorKey);
	}, [open, editing, initialConnectorKey, form, applyConnectorDefaults]);

	const handleDriverSelect = (id?: string) => {
		if (!id) return;
		const driver = drivers.find((item) => item.id === id);
		if (!driver) return;
		const next: Record<string, string> = {};
		if (driver.driverClass) next.driverClass = driver.driverClass;
		const versionHint = driver.fileName || driver.version;
		if (versionHint) next.driverVersion = versionHint;
		form.setFieldsValue(next);
	};

	const buildApiSecrets = (values: Record<string, any>, descriptor?: ApiAuthProviderDescriptorDTO) => {
		const secrets: Record<string, any> = {};
		const secretValues = asRecord(values.apiAuthSecrets) || {};
		(descriptor?.fields || []).forEach((field) => {
			if (!field.sensitive || String(field.type || "").toLowerCase() === "secretref") return;
			const value = secretValues[field.name];
			if (typeof value === "string" ? value.trim() : value != null) {
				secrets[field.name] = typeof value === "string" ? value.trim() : value;
			}
		});
		return Object.keys(secrets).length ? secrets : undefined;
	};

	const buildApiProps = (
		values: Record<string, any>,
		baseProps: Record<string, any> | undefined,
		descriptor?: ApiAuthProviderDescriptorDTO,
	) => {
		const baseUrl = String(values.apiBaseUrl || "").trim();
		const authProvider = String(values.apiAuthProvider || "none").trim() || "none";
		const defaultHeaders = parseObjectJson(values.apiDefaultHeadersJson, "默认请求头");
		const requestPolicy = parseObjectJson(values.apiRequestPolicyJson, "请求策略");
		const resolvedRequestPolicy = {
			...(requestPolicy || {}),
			allowHttp: typeof values.apiAllowHttp === "boolean" ? values.apiAllowHttp : requestPolicy?.allowHttp === true,
		};
		const rateLimit = parseObjectJson(values.apiRateLimitJson, "限流策略");
		const tls = parseObjectJson(values.apiTlsJson, "TLS 策略");
		const rawAuthConfig = cleanRecord(values.apiAuthConfig);
		const configFieldNames = new Set(
			(descriptor?.fields || [])
				.filter((field) => !field.sensitive || String(field.type || "").toLowerCase() === "secretref")
				.map((field) => field.name),
		);
		const authConfig = cleanRecord(
			configFieldNames.size
				? Object.fromEntries(Object.entries(rawAuthConfig || {}).filter(([key]) => configFieldNames.has(key)))
				: rawAuthConfig,
		);
		const secretFieldNames = new Set(
			(descriptor?.fields || [])
				.filter((field) => field.sensitive && String(field.type || "").toLowerCase() !== "secretref")
				.map((field) => field.name),
		);
		const existingSecretRefs =
			cleanRecord(
				Object.fromEntries(
					Object.entries(cleanRecord(values.apiAuthSecretRefs) || {}).filter(
						([key]) => !secretFieldNames.size || secretFieldNames.has(key),
					),
				),
			) || {};
		const nextSecretRefs: Record<string, string> = {};
		(descriptor?.fields || []).forEach((field) => {
			if (!field.sensitive || String(field.type || "").toLowerCase() === "secretref") return;
			const value = asRecord(values.apiAuthSecrets)?.[field.name];
			if (typeof value === "string" ? value.trim() : value != null) {
				nextSecretRefs[field.name] = field.name;
			}
		});
		const secretRefs = cleanRecord({ ...existingSecretRefs, ...nextSecretRefs });
		const authRefConfig: Record<string, string> = {};
		(descriptor?.fields || []).forEach((field) => {
			if (!field.sensitive || String(field.type || "").toLowerCase() === "secretref") return;
			const secretRef = secretRefs?.[field.name];
			if (!secretRef) return;
			authRefConfig[buildApiAuthRefName(field.name)] = secretRef;
		});
		const auth =
			authProvider === "none"
				? { provider: "none" }
				: cleanRecord({
						provider: authProvider,
						...(authConfig || {}),
						...authRefConfig,
						...(secretRefs ? { secretRefs } : {}),
					}) || { provider: authProvider };
		const apiNode = {
			baseUrl,
			...(defaultHeaders ? { defaultHeaders } : {}),
			requestPolicy: resolvedRequestPolicy,
			...(rateLimit ? { rateLimit } : {}),
			...(tls ? { tls } : {}),
			auth,
		};
		return {
			...(baseProps || {}),
			baseUrl,
			authProvider,
			auth,
			api: apiNode,
			readerConfig: {
				...(asRecord(baseProps?.readerConfig) || {}),
				...apiNode,
			},
			connectorType: "api",
			readerType: apiContract?.defaultReaderType || "httpreader",
			sourceCategory: "api",
			contractVersion: apiContract?.contractVersion || baseProps?.contractVersion || "1.0.0",
		};
	};

	const handleApiConnectionTest = async () => {
		try {
			const values = await form.validateFields([
				"type",
				"apiBaseUrl",
				"apiAuthProvider",
				"apiAuthConfig",
				"apiAuthSecrets",
				"apiDefaultHeadersJson",
				"apiRequestPolicyJson",
				"apiAllowHttp",
				"apiRateLimitJson",
				"apiTlsJson",
				"propsJson",
			]);
			if (!isApiSourceType(values.type)) {
				message.warning("请选择 API 数据源类型后再测试");
				return;
			}
			setApiTesting(true);
			setApiTestResult(null);
			const selectedApiDescriptor = (apiContract?.authProviders || []).find(
				(item) => String(item.id).toLowerCase() === String(values.apiAuthProvider || "none").toLowerCase(),
			);
			if (selectedApiDescriptor?.enabled === false) {
				message.warning("该鉴权方式即将支持，当前运行时暂未开放");
				return;
			}
			const result = editing?.id
				? await ingestionTaskAPI.testApiConnection({ dataSourceId: editing.id })
				: await ingestionTaskAPI.testApiConnection({
						sourceConfig: buildApiProps(
							values,
							values.propsJson ? asRecord(parseJson(values.propsJson)) : undefined,
							selectedApiDescriptor,
						),
						secrets: buildApiSecrets(values, selectedApiDescriptor),
					});
			setApiTestResult(result);
			if (result?.connected) {
				message.success("API 连接成功");
			} else {
				message.warning(result?.message || result?.advice || "API 连接失败");
			}
		} catch (err: any) {
			message.error(err?.message || "API 连接测试失败");
		} finally {
			setApiTesting(false);
		}
	};

	const handleSave = async () => {
		try {
			const values = await form.validateFields();
			setSaving(true);
			const apiSourceFlag = isApiSourceType(values.type);
			const jdbc = !apiSourceFlag && isJdbcType(values.type, values.jdbcUrl);
			let props = values.propsJson ? parseJson(values.propsJson) : undefined;
			const selectedApiDescriptor = (apiContract?.authProviders || []).find(
				(item) => String(item.id).toLowerCase() === String(values.apiAuthProvider || "none").toLowerCase(),
			);
			if (apiSourceFlag && selectedApiDescriptor?.enabled === false) {
				message.warning("该鉴权方式即将支持，当前运行时暂未开放");
				return;
			}
			if (apiSourceFlag) {
				props = buildApiProps(values, asRecord(props), selectedApiDescriptor);
			} else if (values.readerType) {
				props = { ...(props || {}), readerType: values.readerType };
			}
			const selectedDriver = drivers.find((item) => item.id === values.driverId);
			const resolvedDriverClass = String(values.driverClass || selectedDriver?.driverClass || "").trim();
			const resolvedDriverVersion = String(
				values.driverVersion || selectedDriver?.fileName || selectedDriver?.version || "",
			).trim();
			if (resolvedDriverClass) props = { ...(props || {}), driverClass: resolvedDriverClass };
			if (resolvedDriverVersion) props = { ...(props || {}), driverVersion: resolvedDriverVersion };
			const apiSecrets = apiSourceFlag ? buildApiSecrets(values, selectedApiDescriptor) : undefined;
			const payload: DataSourceUpsertPayload = {
				name: String(values.name).trim(),
				type: String(values.type).trim(),
				connectorKey: normalizeConnectorKey(values.connectorKey) || inferConnectorKey(values.type, props),
				jdbcUrl: jdbc ? String(values.jdbcUrl || "").trim() || undefined : undefined,
				username: jdbc ? String(values.username || "").trim() || undefined : undefined,
				description: String(values.description || "").trim() || undefined,
				ownerDept: String(values.ownerDept || "").trim() || undefined,
				props: props && Object.keys(props).length ? props : undefined,
				secrets: apiSourceFlag ? apiSecrets : values.password ? { password: values.password } : undefined,
			};
			if (editing) {
				const impact = await dataSourcesService.updateWithImpact(editing.id, payload);
				message.success("数据源已更新");
				showImpact(impact || null);
			} else {
				await dataSourcesService.create(payload);
				message.success("数据源已创建");
			}
			onSaved();
			onClose();
		} catch (error: any) {
			if (error?.errorFields) return; // validation error already surfaced by antd
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

	return (
		<>
			<Modal
				title={editing ? "编辑数据源" : "新增数据源"}
				open={open}
				onCancel={onClose}
				onOk={handleSave}
				okText="保存"
				confirmLoading={saving}
				destroyOnClose
			>
				<Form layout="vertical" form={form} preserve={false}>
					<Form.Item name="name" label="名称" rules={[{ required: true, message: "请输入名称" }]}>
						<Input placeholder="例如：ERP 数据库" />
					</Form.Item>
					<Form.Item name="connectorKey" label="连接器" rules={[{ required: true, message: "请选择连接器" }]}>
						<Select
							options={connectorOptions}
							placeholder={connectorsLoading ? "连接器加载中..." : "请选择连接器"}
							loading={connectorsLoading}
							showSearch
							optionFilterProp="label"
							onChange={(value) => applyConnectorDefaults(value)}
						/>
					</Form.Item>
					{selectedConnector?.description ? (
						<Text type="secondary" className="block -mt-2 mb-3">
							{selectedConnector.description}
						</Text>
					) : null}
					{systemTypesFallbackActive ? (
						<Alert
							type="warning"
							showIcon
							className="mb-4"
							message="系统类型字典未接通"
							description={
								<Space direction="vertical" size={4}>
									<Text>{systemTypesFallbackMessage}</Text>
									<Space size={8} wrap>
										<Button type="link" className="h-auto p-0" onClick={() => router.push("/governance/standards/reference")}>
											去参考码维护
										</Button>
										<Button type="link" className="h-auto p-0" onClick={() => router.push("/foundation/connectors")}>
											查看连接器目录
										</Button>
									</Space>
								</Space>
							}
						/>
					) : null}
					<Form.Item name="type" label="源类型" rules={[{ required: true, message: "请选择源类型" }]}>
						<Select
							options={systemTypeOptions}
							placeholder={systemTypesLoading ? "系统类型加载中..." : "由连接器自动填充"}
							loading={systemTypesLoading}
							disabled={Boolean(connectorValue)}
							onChange={(value) => handleTypeChange(value)}
						/>
					</Form.Item>
					{apiSource && (
						<>
							<Alert
								type="info"
								showIcon
								className="mb-4"
								message="API 数据源配置"
								description="鉴权机制仍可扩展；敏感值只会写入 secrets，props 中保存运行时可读取的 provider 与 Ref 字段，连接测试会复用 API 入湖运行时。"
							/>
							<Form.Item
								name="apiBaseUrl"
								label="API Base URL"
								rules={[
									{ required: true, message: "请输入 API Base URL" },
									{
										validator: async (_: any, value: string) => {
											if (!value) return Promise.resolve();
											try {
												const url = new URL(String(value).trim());
												if (url.protocol !== "http:" && url.protocol !== "https:") {
													return Promise.reject(new Error("仅支持 http/https"));
												}
												return Promise.resolve();
											} catch {
												return Promise.reject(new Error("URL 格式不合法"));
											}
										},
									},
								]}
							>
								<Input placeholder="https://api.example.com" />
							</Form.Item>
							<Form.Item
								name="apiAllowHttp"
								label="允许明文 HTTP"
								valuePropName="checked"
								initialValue={false}
								extra="仅建议测试环境开启；编辑已有数据源请先保存，再测试连接；生产环境请优先使用 HTTPS。"
							>
								<Switch checkedChildren="允许" unCheckedChildren="禁止" />
							</Form.Item>
							<div className="grid gap-4 md:grid-cols-2">
								<Form.Item
									name="readerType"
									label="Reader 类型"
									initialValue="httpreader"
									rules={[{ required: true, message: "请输入 Reader 类型" }]}
								>
									<Input disabled placeholder="httpreader" />
								</Form.Item>
								<Form.Item name="apiAuthProvider" label="鉴权方式" initialValue="none">
									<Select
										loading={apiContractLoading}
										options={apiAuthProviders.map((item) => ({
											label: item.enabled === false ? `${item.label || item.id}（即将支持）` : item.label || item.id,
											value: item.id,
											disabled: item.enabled === false,
										}))}
										placeholder="选择鉴权方式"
									/>
								</Form.Item>
							</div>
							{selectedApiAuthProvider?.description ? (
								<Text type="secondary" className="block -mt-2 mb-3">
									{selectedApiAuthProvider.description}
								</Text>
							) : null}
							{selectedApiAuthProvider?.fields?.length ? (
								<div className="grid gap-4 md:grid-cols-2">
									{selectedApiAuthProvider.fields.map((field) => {
										const secretField =
											Boolean(field.sensitive) && String(field.type || "").toLowerCase() !== "secretref";
										return (
											<Form.Item
												key={field.name}
												name={secretField ? ["apiAuthSecrets", field.name] : ["apiAuthConfig", field.name]}
												label={field.label || field.name}
												extra={
													secretField && editing
														? "留空保持已有密钥不变"
														: field.description || undefined
												}
												rules={[
													{
														required: Boolean(field.required) && (!secretField || !editing),
														message: `请输入${field.label || field.name}`,
													},
												]}
											>
												{buildApiAuthFieldInput(field)}
											</Form.Item>
										);
									})}
								</div>
							) : null}
							<Form.Item
								name="apiDefaultHeadersJson"
								label="默认请求头 JSON（可选）"
								rules={[{ validator: jsonObjectValidator("默认请求头") }]}
							>
								<Input.TextArea rows={3} placeholder='{"Accept":"application/json"}' />
							</Form.Item>
							<div className="grid gap-4 md:grid-cols-3">
								<Form.Item
									name="apiRequestPolicyJson"
									label="请求策略 JSON"
									rules={[{ validator: jsonObjectValidator("请求策略") }]}
								>
									<Input.TextArea rows={3} placeholder='{"connectTimeoutMillis":5000,"readTimeoutMillis":30000}' />
								</Form.Item>
								<Form.Item
									name="apiRateLimitJson"
									label="限流策略 JSON"
									rules={[{ validator: jsonObjectValidator("限流策略") }]}
								>
									<Input.TextArea rows={3} placeholder='{"requestsPerSecond":5,"maxConcurrency":2}' />
								</Form.Item>
								<Form.Item
									name="apiTlsJson"
									label="TLS 策略 JSON"
									rules={[{ validator: jsonObjectValidator("TLS 策略") }]}
								>
									<Input.TextArea rows={3} placeholder='{"verifyTls":true}' />
								</Form.Item>
							</div>
							<Form.Item label="连接测试">
								<Space direction="vertical" style={{ width: "100%" }}>
									<Button onClick={handleApiConnectionTest} loading={apiTesting}>
										{editing?.id ? "测试已保存连接" : "测试当前配置"}
									</Button>
									{apiTestResult ? (
										<Alert
											type={apiTestResult.connected ? "success" : "warning"}
											showIcon
											message={apiTestResult.connected ? "连接成功" : "连接失败"}
											description={
												apiTestResult.connected
													? `HTTP ${apiTestResult.httpStatus ?? "-"} · 样本 ${apiTestResult.sampleCount ?? 0} 条 · ${apiTestResult.elapsedMs ?? 0} ms`
													: apiTestResult.advice || apiTestResult.message || apiTestResult.failureCategory || "请检查 API 配置"
											}
										/>
									) : null}
									{!editing?.id ? <Text type="secondary">保存前可用当前表单配置测试 API 连接。</Text> : null}
								</Space>
							</Form.Item>
						</>
					)}
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
					{!apiSource && (
						<>
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
						</>
					)}
					{!jdbcRequired && !apiSource && (
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
					<Form.Item name="ownerDept" label="归属部门">
						<Select
							showSearch
							allowClear
							placeholder="选择归属部门（可选）"
							options={deptOptions}
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
					{editing?.lastError && <Text type="danger">最近错误：{editing.lastError}</Text>}
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
					<Upload
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
					</Upload>

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
		</>
	);
}
