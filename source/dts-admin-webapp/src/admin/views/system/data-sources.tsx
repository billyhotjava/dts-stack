import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useMemo, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import type { HiveConnectionPersistRequest, HiveConnectionTestResult } from "#/infra";
import {
	type ConnectionTestLog,
	createInfraDataSource,
	deleteInfraDataSource,
	fetchInfraFeatures,
	type InfraDataSource,
	type InfraFeatureFlags,
	listConnectionTestLogs,
	listInfraDataSources,
	publishInceptorDataSource,
	refreshInceptorRegistry,
	testHiveConnection,
	type ModuleStatus,
	updateInfraDataSource,
	type UpsertInfraDataSourcePayload,
} from "@/api/services/infraService";
import { Icon } from "@/components/icon";
import { Alert, AlertDescription, AlertTitle } from "@/ui/alert";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/ui/card";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/ui/collapsible";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/ui/form";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Switch } from "@/ui/switch";
import { Textarea } from "@/ui/textarea";
import { cn } from "@/utils";
import { GenericDataSourceDialog } from "./GenericDataSourceDialog";

const formSchema = z
	.object({
		name: z.string().min(1, "请输入数据源名称"),
		description: z.string().optional(),
		host: z.string().min(1, "请输入 Hive 主机地址"),
		port: z.coerce.number().int().min(1).max(65535),
		database: z.string().min(1, "请输入默认数据库"),
		servicePrincipal: z.string().min(1, "请输入服务主体 principal"),
		loginPrincipal: z.string().min(1, "请输入登录主体"),
		authMethod: z.enum(["KEYTAB", "PASSWORD"]),
		keytabBase64: z.string().optional(),
		keytabFileName: z.string().optional(),
		password: z.string().optional(),
		krb5Conf: z.string().optional(),
		proxyUser: z.string().optional(),
		testQuery: z.string().optional(),
		extraParams: z.string().optional(),
		useHttpTransport: z.boolean(),
		httpPath: z.string().optional(),
		useSsl: z.boolean(),
		useCustomJdbc: z.boolean(),
		customJdbcUrl: z.string().optional(),
	})
	.superRefine((values, ctx) => {
		if (values.authMethod === "KEYTAB" && !values.keytabBase64) {
			ctx.addIssue({
				code: z.ZodIssueCode.custom,
				path: ["keytabBase64"],
				message: "请上传 Keytab 文件",
			});
		}
		if (values.authMethod === "PASSWORD" && !values.password) {
			ctx.addIssue({
				code: z.ZodIssueCode.custom,
				path: ["password"],
				message: "请输入 Kerberos 密码",
			});
		}
		if (!values.krb5Conf?.trim()) {
			ctx.addIssue({
				code: z.ZodIssueCode.custom,
				path: ["krb5Conf"],
				message: "请上传 krb5.conf 文件",
			});
		}
		if (values.useHttpTransport && !values.httpPath?.trim()) {
			ctx.addIssue({
				code: z.ZodIssueCode.custom,
				path: ["httpPath"],
				message: "HTTP 模式需要指定 httpPath",
			});
		}
		if (values.useCustomJdbc && !values.customJdbcUrl?.trim()) {
			ctx.addIssue({
				code: z.ZodIssueCode.custom,
				path: ["customJdbcUrl"],
				message: "请输入自定义 JDBC 连接串",
			});
		}
	});

type FormValues = z.infer<typeof formSchema>;

const defaultValues: FormValues = {
	name: "生产 Hive",
	description: "TDS Inceptor 集群",
	host: "Hive_Host/<ip>",
	port: 10000,
	database: "default",
	servicePrincipal: "hive/<hive_host or ip>@TDH",
	loginPrincipal: "hive@TDH",
	authMethod: "KEYTAB",
	keytabBase64: "",
	keytabFileName: undefined,
	password: "",
	krb5Conf: "",
	proxyUser: "",
	testQuery: "SELECT 1",
	extraParams: "",
	useHttpTransport: false,
	httpPath: "",
	useSsl: false,
	useCustomJdbc: false,
	customJdbcUrl: "",
};

export default function AdminDataSourcesView() {
	const form = useForm<FormValues>({
		resolver: zodResolver(formSchema),
		defaultValues,
	});

	const authMethod = useWatch({ control: form.control, name: "authMethod" });
	const useHttpTransport = useWatch({ control: form.control, name: "useHttpTransport" });
	const useCustomJdbc = useWatch({ control: form.control, name: "useCustomJdbc" });
	const watched = useWatch({ control: form.control });

	const jdbcPreview = useMemo(() => buildJdbcUrl(watched), [watched]);

	const [features, setFeatures] = useState<InfraFeatureFlags | null>(null);
	const [sources, setSources] = useState<InfraDataSource[]>([]);
	const [logs, setLogs] = useState<ConnectionTestLog[]>([]);
	const [loadingFeatures, setLoadingFeatures] = useState(false);
	const [loadingSources, setLoadingSources] = useState(false);
	const [loadingLogs, setLoadingLogs] = useState(false);
	const [testing, setTesting] = useState(false);
	const [testResult, setTestResult] = useState<HiveConnectionTestResult | null>(null);
	const [connectionAdvancedOpen, setConnectionAdvancedOpen] = useState(false);
	const [kerberosAdvancedOpen, setKerberosAdvancedOpen] = useState(false);
	const [refreshing, setRefreshing] = useState(false);
	const [deletingId, setDeletingId] = useState<string | null>(null);
	const [featurePolls, setFeaturePolls] = useState(0);
	const [genericDialogOpen, setGenericDialogOpen] = useState(false);
	const [genericDialogMode, setGenericDialogMode] = useState<"create" | "edit">("create");
	const [genericEditing, setGenericEditing] = useState<InfraDataSource | null>(null);
	const [savingGeneric, setSavingGeneric] = useState(false);

	const resolveModuleLabel = (module: string) => {
		switch (module) {
			case "catalog":
				return "数据资产";
			case "governance":
				return "数据治理";
			case "development":
				return "数据开发";
			default:
				return module;
		}
	};

	const loadFeatures = async () => {
		setLoadingFeatures(true);
		try {
			const data = await fetchInfraFeatures();
			setFeatures(data);
		} finally {
			setLoadingFeatures(false);
		}
	};

	const hasInceptor = features?.hasActiveInceptor ?? false;
	const inceptorStatusLabel = hasInceptor ? "Inceptor 数据源已就绪" : "Inceptor 数据源未配置";
	const activeInceptor = useMemo(
		() => sources.find((item) => (item.type || "").toUpperCase() === "INCEPTOR"),
		[sources],
	);
	const lastVerifiedDisplay = features?.lastVerifiedAt ? new Date(features.lastVerifiedAt).toLocaleString() : undefined;
	const lastUpdatedDisplay = features?.lastUpdatedAt ? new Date(features.lastUpdatedAt).toLocaleString() : undefined;
	let moduleStatuses: ModuleStatus[] = features?.moduleStatuses ?? [];
	const integrationStatus = features?.integrationStatus;
	const lastSyncedDisplay = integrationStatus?.lastSyncAt
		? new Date(integrationStatus.lastSyncAt).toLocaleString()
		: undefined;
	const integrationActions = integrationStatus?.actions?.length ? integrationStatus.actions.join("，") : undefined;

	// Derive a transient SYNCING state for the catalog module
	const datasetCount = integrationStatus?.catalogDatasetCount ?? 0;
	const showSyncing =
		(features?.hasActiveInceptor ?? false) && datasetCount === 0 && (features?.syncInProgress || featurePolls > 0);
	if (showSyncing && moduleStatuses.length > 0) {
		moduleStatuses = moduleStatuses.map((m) =>
			m.module === "catalog" ? { ...m, status: "SYNCING", message: m.message || "首次加载，正在同步 Hive 元数据…" } : m,
		);
	}

	const loadSources = async () => {
		setLoadingSources(true);
		try {
			const data = await listInfraDataSources();
			const sorted = [...data].sort((a, b) => {
				const typeA = (a.type || "").toUpperCase();
				const typeB = (b.type || "").toUpperCase();
				if (typeA === "INCEPTOR" && typeB !== "INCEPTOR") return -1;
				if (typeA !== "INCEPTOR" && typeB === "INCEPTOR") return 1;
				const typeCompare = typeA.localeCompare(typeB);
				if (typeCompare !== 0) return typeCompare;
				const tsA = a.lastVerifiedAt ? Date.parse(a.lastVerifiedAt) : 0;
				const tsB = b.lastVerifiedAt ? Date.parse(b.lastVerifiedAt) : 0;
				if (tsA !== tsB) return tsB - tsA;
				return (a.name || "").localeCompare(b.name || "");
			});
			setSources(sorted);
		} finally {
			setLoadingSources(false);
		}
	};

	const loadLogs = async () => {
		setLoadingLogs(true);
		try {
			const data = await listConnectionTestLogs();
			setLogs(data);
		} finally {
			setLoadingLogs(false);
		}
	};

	const handleRegistryRefresh = async () => {
		setRefreshing(true);
		try {
			const data = await refreshInceptorRegistry();
			setFeatures(data);
			await loadSources();
			toast.success("Inceptor 状态已重新同步");
		} catch (error: any) {
			toast.error(error?.message || "刷新失败");
		} finally {
			setRefreshing(false);
		}
	};

	const handleDeleteSource = async (item: InfraDataSource) => {
		if (!item?.id) return;
		const confirmed = window.confirm(`确定删除数据源 “${item.name}” 吗？该操作不可撤销。`);
		if (!confirmed) {
			return;
		}
		setDeletingId(item.id);
		try {
			await deleteInfraDataSource(item.id);
			await Promise.all([loadSources(), loadFeatures(), loadLogs()]);
			toast.success("数据源已删除");
		} catch (error: any) {
			toast.error(error?.message || "删除失败");
		} finally {
			setDeletingId(null);
		}
	};

	const openGenericEdit = (item: InfraDataSource) => {
		setGenericDialogMode("edit");
		setGenericEditing(item);
		setGenericDialogOpen(true);
	};

	const closeGenericDialog = () => {
		if (savingGeneric) {
			return;
		}
		setGenericDialogOpen(false);
		setGenericEditing(null);
	};

	const handleGenericSubmit = async (payload: UpsertInfraDataSourcePayload) => {
		if (savingGeneric) return;
		const normalizedType = payload.type.trim().toUpperCase();
		if (normalizedType === "INCEPTOR") {
			toast.error("Inceptor 数据源需通过上方流程配置");
			return;
		}
		const requestBody: UpsertInfraDataSourcePayload = {
			...payload,
			type: normalizedType,
			props: payload.props && Object.keys(payload.props).length ? payload.props : undefined,
			secrets: payload.secrets && Object.keys(payload.secrets).length ? payload.secrets : undefined,
		};
		setSavingGeneric(true);
		try {
			if (genericDialogMode === "create") {
				await createInfraDataSource(requestBody);
				toast.success("数据源已创建");
			} else if (genericDialogMode === "edit" && genericEditing?.id) {
				await updateInfraDataSource(genericEditing.id, requestBody);
				toast.success("数据源已更新");
			}
			await Promise.all([loadSources(), loadFeatures()]);
			setGenericDialogOpen(false);
			setGenericEditing(null);
		} catch (error: any) {
			const fallback = genericDialogMode === "create" ? "创建失败" : "更新失败";
			toast.error(error?.message || fallback);
		} finally {
			setSavingGeneric(false);
		}
	};

	useEffect(() => {
		void loadFeatures();
		void loadSources();
		void loadLogs();
	}, []);

	// Auto-refresh features shortly after mount when active Inceptor exists but catalog is still empty.
	useEffect(() => {
		const active = features?.hasActiveInceptor ?? false;
		const datasetCount = features?.integrationStatus?.catalogDatasetCount ?? 0;
		if (active && datasetCount === 0 && featurePolls < 5) {
			const timer = setTimeout(async () => {
				await loadFeatures();
				setFeaturePolls((n) => n + 1);
			}, 3000);
			return () => clearTimeout(timer);
		}
		return;
	}, [features, featurePolls]);

	const handleKeytabUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
		const file = event.target.files?.[0];
		if (!file) return;
		if (file.name.includes("机密")) {
			toast.error("本模块是非密模块，请勿处理机密数据！");
			form.setError("keytabBase64", { message: "文件名包含“机密”，已拒绝上传" });
			try {
				event.target.value = "";
			} catch {
				// ignore
			}
			return;
		}
		if (file.size > 2 * 1024 * 1024) {
			form.setError("keytabBase64", { message: "Keytab 文件过大（>2MB）" });
			return;
		}
		const buffer = await file.arrayBuffer();
		const bytes = new Uint8Array(buffer);
		let binary = "";
		for (let i = 0; i < bytes.byteLength; i += 1) {
			binary += String.fromCharCode(bytes[i]);
		}
		const base64 = btoa(binary);
		form.clearErrors("keytabBase64");
		form.setValue("keytabBase64", base64, { shouldDirty: true });
		form.setValue("keytabFileName", file.name, { shouldDirty: true });
	};

	const [krb5FileName, setKrb5FileName] = useState<string | undefined>(undefined);

	const handleKrb5Upload = async (event: React.ChangeEvent<HTMLInputElement>) => {
		const file = event.target.files?.[0];
		if (!file) return;
		if (file.name.includes("机密")) {
			toast.error("本模块是非密模块，请勿处理机密数据！");
			form.setError("krb5Conf", { message: "文件名包含“机密”，已拒绝上传" });
			try {
				event.target.value = "";
			} catch {
				// ignore
			}
			return;
		}
		if (file.size > 256 * 1024) {
			form.setError("krb5Conf", { message: "krb5.conf 文件过大（>256KB）" });
			return;
		}
		const content = await file.text();
		form.clearErrors("krb5Conf");
		form.setValue("krb5Conf", content, { shouldDirty: true });
		setKrb5FileName(file.name);
	};

	const resetKrb5 = () => {
		form.setValue("krb5Conf", "", { shouldDirty: true });
		setKrb5FileName(undefined);
	};

	const resetKeytab = () => {
		form.setValue("keytabBase64", "", { shouldDirty: true });
		form.setValue("keytabFileName", undefined, { shouldDirty: true });
	};

	const onSubmit = form.handleSubmit(async (values) => {
		const jdbcUrl = values.useCustomJdbc && values.customJdbcUrl?.trim() ? values.customJdbcUrl.trim() : jdbcPreview;
		if (!jdbcUrl) {
			toast.error("无法生成 JDBC 连接串，请检查基础配置");
			return;
		}
		const rawPropEntries = parseKeyValueEntries(values.extraParams);
		// 仅允许 Apache Hive 官方属性透传到 Properties。其余（尤其 Simba/自定义）一律丢弃。
		const allowedPropKeys = new Set<string>([
			"sasl.qop",
			// 其他官方属性如需放开可在此追加
		]);
		const jdbcPropsEntries = rawPropEntries.filter(([key]) => allowedPropKeys.has(key));
		const jdbcProps: Record<string, string> = Object.fromEntries(jdbcPropsEntries);
		// ssl 通过 URL 参数处理（见 buildJdbcUrl），不重复放入属性

		const payload = {
			jdbcUrl,
			loginPrincipal: values.loginPrincipal.trim(),
			krb5Conf: values.krb5Conf?.trim() || undefined,
			authMethod: values.authMethod,
			keytabBase64: values.authMethod === "KEYTAB" ? values.keytabBase64 || undefined : undefined,
			keytabFileName: values.authMethod === "KEYTAB" ? values.keytabFileName || undefined : undefined,
			password: values.authMethod === "PASSWORD" ? values.password : undefined,
			proxyUser: values.proxyUser?.trim() || undefined,
			testQuery: values.testQuery?.trim() || undefined,
			jdbcProperties: Object.keys(jdbcProps).length ? jdbcProps : undefined,
			remarks: values.description?.trim() || undefined,
		} satisfies Parameters<typeof testHiveConnection>[0];

		setTesting(true);
		setTestResult(null);
		let lastResult: HiveConnectionTestResult | null = null;
		try {
			const result = await testHiveConnection(payload);
			lastResult = result;
			setTestResult(result);
			await loadLogs();
			if (!result.success) {
				toast.error(result.message || "连接失败");
				return;
			}
			const persistPayload: HiveConnectionPersistRequest = {
				...payload,
				name: values.name.trim(),
				description: values.description?.trim() || undefined,
				servicePrincipal: values.servicePrincipal.trim(),
				host: values.host.trim(),
				port: values.port,
				database: values.database.trim(),
				useHttpTransport: values.useHttpTransport,
				httpPath: values.useHttpTransport ? values.httpPath?.trim() || undefined : undefined,
				useSsl: values.useSsl,
				useCustomJdbc: values.useCustomJdbc,
				customJdbcUrl: values.useCustomJdbc ? values.customJdbcUrl?.trim() || undefined : undefined,
				lastTestElapsedMillis: result.elapsedMillis,
				engineVersion: result.engineVersion ?? undefined,
				driverVersion: result.driverVersion ?? undefined,
			};
			await publishInceptorDataSource(persistPayload);
			await Promise.all([loadSources(), loadFeatures()]);
			toast.success(`连接成功并已保存，用时 ${result.elapsedMillis} ms`);
		} catch (error: any) {
			const message = error?.message || "保存数据源失败";
			if (!lastResult || !lastResult.success) {
				setTestResult({ success: false, message, elapsedMillis: 0, warnings: [] });
			}
			toast.error(message);
		} finally {
			setTesting(false);
		}
	});

	const handleReset = () => {
		form.reset(defaultValues);
		setTestResult(null);
	};

	return (
		<div className="space-y-4">
			<div className="flex items-center justify-center gap-2 rounded-md border border-dashed border-red-200 bg-red-50 px-4 py-3 text-center text-sm font-medium text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200">
				<Icon icon="mdi:star" className="h-5 w-5 text-red-500" />
				<span className="text-center">非密模块禁止处理涉密数据</span>
			</div>
			<Form {...form}>
				<form onSubmit={onSubmit} className="space-y-4">
					<Card>
						<CardHeader>
							<CardTitle className="text-base">必填信息</CardTitle>
							<CardDescription>先完成核心字段，确保能够与 Hive/Inceptor 建立 Kerberos 连接。</CardDescription>
						</CardHeader>
						<CardContent className="space-y-4">
							<div className="grid gap-4 md:grid-cols-2">
								<FormField
									control={form.control}
									name="name"
									render={({ field }) => (
										<FormItem>
											<FormLabel>数据源名称</FormLabel>
											<FormControl>
												<Input {...field} placeholder="如：生产 Hive 集群" />
											</FormControl>
											<FormMessage />
										</FormItem>
									)}
								/>
								<FormField
									control={form.control}
									name="host"
									render={({ field }) => (
										<FormItem>
											<FormLabel>主机 / 网关</FormLabel>
											<FormControl>
												<Input {...field} placeholder="如：inceptor-gw.example.com" />
											</FormControl>
											<FormMessage />
										</FormItem>
									)}
								/>
								<FormField
									control={form.control}
									name="port"
									render={({ field }) => (
										<FormItem>
											<FormLabel>端口</FormLabel>
											<FormControl>
												<Input type="number" {...field} />
											</FormControl>
											<FormMessage />
										</FormItem>
									)}
								/>
								<FormField
									control={form.control}
									name="database"
									render={({ field }) => (
										<FormItem>
											<FormLabel>默认数据库</FormLabel>
											<FormControl>
												<Input {...field} placeholder="default" />
											</FormControl>
											<FormMessage />
										</FormItem>
									)}
								/>
							</div>

							<div className="grid gap-4 md:grid-cols-2">
								<FormField
									control={form.control}
									name="servicePrincipal"
									render={({ field }) => (
										<FormItem>
											<FormLabel>服务 Principal</FormLabel>
											<FormControl>
												<Input {...field} placeholder="hive/_HOST@EXAMPLE.COM" />
											</FormControl>
											<FormMessage />
										</FormItem>
									)}
								/>
							</div>

							<FormField
								control={form.control}
								name="krb5Conf"
								render={() => (
									<FormItem>
										<FormLabel>上传 krb5.conf</FormLabel>
										<FormControl>
											<Input type="file" accept=".conf,.ini,.cfg,.txt" onChange={handleKrb5Upload} />
										</FormControl>
										<FormMessage />
										{krb5FileName ? (
											<div className="flex items-center gap-2 pt-1 text-xs text-muted-foreground">
												<Icon icon="solar:check-circle-bold" className="h-4 w-4 text-emerald-500" />
												<span>已选择：{krb5FileName}</span>
												<Button type="button" variant="ghost" size="sm" onClick={resetKrb5}>
													清除
												</Button>
											</div>
										) : (
											<p className="text-xs text-muted-foreground">请选择集群下发的 krb5.conf 配置文件。</p>
										)}
									</FormItem>
								)}
							/>

							<div className="grid gap-4 md:grid-cols-2">
								<FormField
									control={form.control}
									name="loginPrincipal"
									render={({ field }) => (
										<FormItem>
											<FormLabel>登录 Principal</FormLabel>
											<FormControl>
												<Input {...field} placeholder="hive@TDH" />
											</FormControl>
											<FormMessage />
										</FormItem>
									)}
								/>
								<div className="space-y-2">
									<FormLabel>认证方式</FormLabel>
									<div className="flex flex-wrap items-center gap-2">
										<Select
											value={authMethod}
											onValueChange={(value) =>
												form.setValue("authMethod", value as FormValues["authMethod"], { shouldDirty: true })
											}
										>
											<SelectTrigger className="w-48">
												<SelectValue />
											</SelectTrigger>
											<SelectContent>
												<SelectItem value="KEYTAB">Keytab</SelectItem>
												<SelectItem value="PASSWORD">密码</SelectItem>
											</SelectContent>
										</Select>
										<Badge variant="outline">推荐：Keytab</Badge>
									</div>
								</div>
							</div>

							{authMethod === "KEYTAB" ? (
								<FormField
									control={form.control}
									name="keytabBase64"
									render={() => (
										<FormItem>
											<FormLabel>上传 Keytab</FormLabel>
											<FormControl>
												<Input type="file" accept=".keytab" onChange={handleKeytabUpload} />
											</FormControl>
											<FormMessage />
											{watched.keytabFileName ? (
												<div className="flex items-center gap-2 pt-1 text-xs text-muted-foreground">
													<Icon icon="solar:check-circle-bold" className="h-4 w-4 text-emerald-500" />
													<span>已选择：{watched.keytabFileName}</span>
													<Button type="button" variant="ghost" size="sm" onClick={resetKeytab}>
														清除
													</Button>
												</div>
											) : (
												<p className="text-xs text-muted-foreground">请选择平台运维下发的客户端 keytab。</p>
											)}
										</FormItem>
									)}
								/>
							) : (
								<FormField
									control={form.control}
									name="password"
									render={({ field }) => (
										<FormItem>
											<FormLabel>Kerberos 密码</FormLabel>
											<FormControl>
												<Input type="password" {...field} />
											</FormControl>
											<FormMessage />
										</FormItem>
									)}
								/>
							)}
						</CardContent>
					</Card>

					<Card>
						<Collapsible open={connectionAdvancedOpen} onOpenChange={setConnectionAdvancedOpen}>
							<CardHeader className="flex flex-row items-start justify-between space-y-0">
								<div>
									<CardTitle className="text-base">高级连接选项</CardTitle>
									<CardDescription>可选：描述信息、HTTP/SSL、附加 JDBC 参数、手动连接串。</CardDescription>
								</div>
								<CollapsibleTrigger asChild>
									<Button type="button" variant="ghost" size="sm" className="transition-none">
										<Icon
											icon="solar:alt-arrow-down-bold"
											className={cn("h-4 w-4 transition-transform", connectionAdvancedOpen ? "rotate-180" : "")}
										/>
									</Button>
								</CollapsibleTrigger>
							</CardHeader>
							<CollapsibleContent>
								<CardContent className="space-y-4">
									<FormField
										control={form.control}
										name="description"
										render={({ field }) => (
											<FormItem>
												<FormLabel>描述</FormLabel>
												<FormControl>
													<Input {...field} placeholder="可选：说明用途" />
												</FormControl>
											</FormItem>
										)}
									/>

									<div className="grid gap-4 md:grid-cols-2">
										<FormField
											control={form.control}
											name="useHttpTransport"
											render={({ field }) => (
												<FormItem className="flex flex-col space-y-2">
													<FormLabel>传输模式</FormLabel>
													<div className="flex items-center gap-2">
														<Switch checked={field.value} onCheckedChange={field.onChange} />
														<span className="text-sm text-muted-foreground">HTTP 模式（默认 Binary）</span>
													</div>
												</FormItem>
											)}
										/>
										{useHttpTransport ? (
											<FormField
												control={form.control}
												name="httpPath"
												render={({ field }) => (
													<FormItem>
														<FormLabel>HTTP Path</FormLabel>
														<FormControl>
															<Input {...field} placeholder="cliservice" />
														</FormControl>
														<FormMessage />
													</FormItem>
												)}
											/>
										) : (
											<div className="h-full rounded-md border border-dashed border-muted-foreground/30 px-3 py-2 text-sm text-muted-foreground">
												默认采用二进制端口 10000 直连，可切换至 HTTP 以适配代理网关。
											</div>
										)}
									</div>

									<FormField
										control={form.control}
										name="useSsl"
										render={({ field }) => (
											<FormItem className="flex items-center justify-between rounded-md border border-border px-4 py-3">
												<div>
													<FormLabel className="flex items-center gap-2">
														<Icon icon="solar:shield-check-bold" className="h-4 w-4" />
														启用 TLS/SSL
													</FormLabel>
													<p className="text-xs text-muted-foreground">
														勾选后将自动追加 ssl=true，可在附加参数中补充 truststore 设置。
													</p>
												</div>
												<FormControl>
													<Switch checked={field.value} onCheckedChange={field.onChange} />
												</FormControl>
											</FormItem>
										)}
									/>

									<FormField
										control={form.control}
										name="extraParams"
										render={({ field }) => (
											<FormItem>
												<FormLabel>附加 JDBC 参数</FormLabel>
												<FormControl>
													<Textarea
														rows={4}
														{...field}
														placeholder={`每行一个 k=v，如：\ntransportMode=http\nhttpPath=cliservice`}
													/>
												</FormControl>
												<FormMessage />
												<p className="text-xs text-muted-foreground">
													支持添加标准 Hive 参数（如 sasl.qop、transportMode、httpPath、ssl），# 开头的行将忽略。
												</p>
											</FormItem>
										)}
									/>

									<div className="rounded-md border border-dashed border-muted-foreground/30 bg-muted/20 p-4">
										<div className="flex items-center justify-between gap-2">
											<div>
												<Label className="text-sm font-medium">自动生成的 JDBC 连接串</Label>
												<p className="text-xs text-muted-foreground">根据上方参数实时生成，可切换为手动编辑。</p>
											</div>
											<div className="flex items-center gap-2 text-sm text-muted-foreground">
												<span>手动编辑</span>
												<Switch
													checked={useCustomJdbc}
													onCheckedChange={(checked) => form.setValue("useCustomJdbc", checked)}
												/>
											</div>
										</div>
										<Textarea
											className="mt-2 h-24 font-mono text-xs"
											value={useCustomJdbc ? watched.customJdbcUrl : jdbcPreview}
											onChange={(event) => form.setValue("customJdbcUrl", event.target.value, { shouldDirty: true })}
											readOnly={!useCustomJdbc}
										/>
									</div>
								</CardContent>
							</CollapsibleContent>
						</Collapsible>
					</Card>

					<Card>
						<Collapsible open={kerberosAdvancedOpen} onOpenChange={setKerberosAdvancedOpen}>
							<CardHeader className="flex flex-row items-start justify-between space-y-0">
								<div>
									<CardTitle className="text-base">高级 Kerberos 设置</CardTitle>
									<CardDescription>可选：代理用户。</CardDescription>
								</div>
								<CollapsibleTrigger asChild>
									<Button type="button" variant="ghost" size="sm" className="transition-none">
										<Icon
											icon="solar:alt-arrow-down-bold"
											className={cn("h-4 w-4 transition-transform", kerberosAdvancedOpen ? "rotate-180" : "")}
										/>
									</Button>
								</CollapsibleTrigger>
							</CardHeader>
							<CollapsibleContent>
								<CardContent className="space-y-4">
									<div className="grid gap-4 md:grid-cols-2">
										<FormField
											control={form.control}
											name="proxyUser"
											render={({ field }) => (
												<FormItem>
													<FormLabel>代理用户（可选）</FormLabel>
													<FormControl>
														<Input {...field} placeholder="如需代理执行，可填写具体用户名" />
													</FormControl>
												</FormItem>
											)}
										/>
									</div>

									{/* krb5.conf 由前面的文件上传提供，这里移除文本框 */}
								</CardContent>
							</CollapsibleContent>
						</Collapsible>
					</Card>

					<Card>
						<CardHeader>
							<CardTitle className="text-base">测试与提示</CardTitle>
							<CardDescription>配置完成后可立即测试 Kerberos 链路与 Hive 可达性。</CardDescription>
						</CardHeader>
						<CardContent className="space-y-4">
							<FormField
								control={form.control}
								name="testQuery"
								render={({ field }) => (
									<FormItem>
										<FormLabel>验证 SQL</FormLabel>
										<FormControl>
											<Input {...field} placeholder="SELECT 1" readOnly />
										</FormControl>
									</FormItem>
								)}
							/>

							{activeInceptor && (
								<Alert>
									<AlertTitle>当前已绑定 Inceptor</AlertTitle>
									<AlertDescription className="text-xs text-muted-foreground">
										平台仅维护一个 Inceptor
										数据库连接。再次“测试并覆盖”会复用并更新现有配置，如需全新连接请先删除列表中的数据源。
									</AlertDescription>
								</Alert>
							)}

							<div className="rounded-md border border-dashed border-muted-foreground/40 bg-muted/30 p-4 text-sm text-muted-foreground">
								<ul className="list-disc space-y-2 pl-5">
									<li>启动测试会在后端临时写入 keytab/krb5 文件并登录，再执行验证 SQL。</li>
									<li>若后端返回 "无法生成 JDBC 连接串"，请检查主机、principal 与 HTTP 模式配置。</li>
									<li>建议在生产环境使用最小权限技术账号，并关闭 Ticket 自动续期。</li>
								</ul>
							</div>

							<div className="flex flex-wrap items-center gap-2">
								<Button type="submit" disabled={testing}>
									<Icon
										icon={testing ? "eos-icons:three-dots-loading" : "solar:wifi-router-bold"}
										className="mr-1 h-4 w-4"
									/>
									{testing ? "执行中..." : activeInceptor ? "测试并覆盖" : "测试并发布"}
								</Button>
								<Button type="button" variant="outline" onClick={handleReset} disabled={testing}>
									<Icon icon="solar:refresh-bold" className="mr-1 h-4 w-4" /> 重置示例
								</Button>
							</div>

							{testResult && (
								<Alert variant={testResult.success ? "default" : "destructive"}>
									<AlertTitle>{testResult.success ? "连接成功" : "连接失败"}</AlertTitle>
									<AlertDescription className="space-y-1 text-sm">
										<p>{testResult.message}</p>
										<div className="text-xs text-muted-foreground">
											<span>耗时：{testResult.elapsedMillis} ms</span>
											{testResult.engineVersion && <span className="ml-3">引擎版本：{testResult.engineVersion}</span>}
											{testResult.driverVersion && <span className="ml-3">驱动：{testResult.driverVersion}</span>}
										</div>
										{testResult.warnings?.length ? (
											<ul className="list-disc space-y-1 pl-5 text-xs text-muted-foreground">
												{testResult.warnings.map((warn, index) => (
													<li key={index}>{warn}</li>
												))}
											</ul>
										) : null}
									</AlertDescription>
								</Alert>
							)}
						</CardContent>
					</Card>
				</form>
			</Form>

			<Card>
				<CardHeader className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
					<div>
						<CardTitle className="text-base">基础能力开关</CardTitle>
						<CardDescription>多源支持等基础能力状态来自后端配置。</CardDescription>
					</div>
					<div className="flex w-full flex-col gap-2 md:w-auto md:flex-row">
						<Button
							type="button"
							variant="outline"
							size="sm"
							onClick={handleRegistryRefresh}
							disabled={loadingFeatures || refreshing}
						>
							<Icon icon="solar:refresh-bold" className={cn("mr-1 h-4 w-4", refreshing ? "animate-spin" : "")} />
							{refreshing ? "同步中…" : "重新同步"}
						</Button>
					</div>
				</CardHeader>
				<CardContent className="space-y-3 text-sm">
					<div className="flex flex-wrap items-center gap-2">
						<Badge variant={features?.multiSourceEnabled ? "default" : "secondary"}>
							多数据源 {features?.multiSourceEnabled ? "已启用" : "未启用"}
						</Badge>
						<Badge variant={hasInceptor ? "default" : "secondary"}>{inceptorStatusLabel}</Badge>
					</div>
					<div className="space-y-1 text-xs text-muted-foreground">
						<p>
							默认 JDBC：
							{features?.defaultJdbcUrl ? (
								<span className="font-mono text-[11px]">{features.defaultJdbcUrl}</span>
							) : (
								<span>未配置</span>
							)}
						</p>
						{features?.loginPrincipal && <p>登录主体：{features.loginPrincipal}</p>}
						{features?.dataSourceName && <p>数据源名称：{features.dataSourceName}</p>}
						{features?.description && <p>描述：{features.description}</p>}
						{features?.authMethod && <p>认证方式：{features.authMethod}</p>}
						{features?.engineVersion && <p>引擎版本：{features.engineVersion}</p>}
						{features?.driverVersion && <p>驱动版本：{features.driverVersion}</p>}
						{features?.lastTestElapsedMillis && <p>最近连通耗时：{features.lastTestElapsedMillis} ms</p>}
						{features?.database && <p>默认库：{features.database}</p>}
						{features?.proxyUser?.trim() && <p>Proxy User：{features.proxyUser}</p>}
						{lastVerifiedDisplay && <p>最近校验：{lastVerifiedDisplay}</p>}
						{lastUpdatedDisplay && <p>最近更新：{lastUpdatedDisplay}</p>}
						{lastSyncedDisplay && <p>最近联动：{lastSyncedDisplay}</p>}
						{integrationActions && <p>联动动作：{integrationActions}</p>}
					</div>
					{moduleStatuses.length > 0 && (
						<div className="rounded-md border border-dashed bg-muted/20 p-3">
							<div className="mb-2 text-xs font-medium text-muted-foreground">模块联动状态</div>
							<div className="space-y-2">
								{moduleStatuses.map((item) => (
									<div key={item.module} className="flex items-start justify-between gap-3 text-xs">
										<div className="space-y-1">
											<div className="font-medium text-foreground">{resolveModuleLabel(item.module)}</div>
											<div className="text-muted-foreground">{item.message}</div>
											{item.updatedAt && (
												<div className="text-[10px] text-muted-foreground">
													{new Date(item.updatedAt).toLocaleString()}
												</div>
											)}
										</div>
										<Badge
											variant={
												item.status === "READY" ? "default" : item.status === "SYNCING" ? "secondary" : "destructive"
											}
										>
											{item.status}
										</Badge>
									</div>
								))}
							</div>
						</div>
					)}
					<p className="text-xs text-muted-foreground">
						如需支持多个外部 Hive 集群，请联系运维开启多源能力并补充密钥。
					</p>
				</CardContent>
			</Card>

			<div className="grid gap-4 lg:grid-cols-2">
				<Card>
					<CardHeader className="flex flex-row flex-wrap items-center justify-between gap-2">
						<div>
							<CardTitle className="text-base">已登记数据源</CardTitle>
							<CardDescription>展示后端注册的数据源摘要（不包含敏感信息）。</CardDescription>
						</div>
						<div className="flex items-center gap-2">
							<Button type="button" variant="ghost" size="sm" onClick={loadSources} disabled={loadingSources}>
								<Icon icon="solar:refresh-bold" className="mr-1 h-4 w-4" /> 刷新
							</Button>
						</div>
					</CardHeader>
					<CardContent className="space-y-3">
						<div className="max-h-[280px] overflow-y-auto rounded-md border">
							<table className="w-full table-fixed border-collapse text-sm">
								<thead className="bg-muted/40 text-left text-xs text-muted-foreground">
									<tr>
										<th className="px-3 py-2 font-medium">名称</th>
										<th className="px-3 py-2 font-medium">状态</th>
										<th className="px-3 py-2 font-medium">类型</th>
										<th className="px-3 py-2 font-medium">连接串</th>
										<th className="px-3 py-2 font-medium">密钥</th>
										<th className="px-3 py-2 font-medium text-right">操作</th>
									</tr>
								</thead>
								<tbody>
									{sources.map((item) => (
										<tr key={item.id} className="border-b last:border-b-0">
											<td className="px-3 py-2 font-medium">{item.name}</td>
											<td className="px-3 py-2">
												<div className="space-y-1">
													<Badge variant={item.status === "ACTIVE" ? "default" : "secondary"}>
														{item.status ?? "未知"}
													</Badge>
													<div className="text-[10px] text-muted-foreground">
														{item.lastVerifiedAt ? new Date(item.lastVerifiedAt).toLocaleString() : "-"}
													</div>
												</div>
											</td>
											<td className="px-3 py-2 text-xs text-muted-foreground">{item.type}</td>
											<td className="px-3 py-2 text-xs font-mono text-muted-foreground break-all">{item.jdbcUrl}</td>
											<td className="px-3 py-2">
												<Badge variant="outline">{item.hasSecrets ? "已加密" : "未配置"}</Badge>
											</td>
											<td className="px-3 py-2 text-right">
												<div className="flex flex-wrap items-center justify-end gap-1">
													{(item.type || "").toUpperCase() !== "INCEPTOR" && (
														<Button
															type="button"
															variant="ghost"
															size="sm"
															onClick={() => openGenericEdit(item)}
															disabled={refreshing || loadingSources}
														>
															<Icon icon="solar:pen-bold" className="mr-1 h-4 w-4" />
															编辑
														</Button>
													)}
													<Button
														type="button"
														variant="ghost"
														size="sm"
														onClick={() => handleDeleteSource(item)}
														disabled={deletingId === item.id || refreshing}
													>
														<Icon icon="solar:trash-bin-trash-bold" className="mr-1 h-4 w-4" />
														{deletingId === item.id ? "删除中" : "删除"}
													</Button>
												</div>
											</td>
										</tr>
									))}
									{!sources.length && (
										<tr>
											<td colSpan={6} className="px-3 py-6 text-center text-xs text-muted-foreground">
												{loadingSources ? "加载中…" : "暂无已登记数据源"}
											</td>
										</tr>
									)}
								</tbody>
							</table>
						</div>
						<p className="text-xs text-muted-foreground">提示：编辑时需重新填写密码或密钥，留空将清除既有敏感配置。</p>
					</CardContent>
				</Card>

				<Card>
					<CardHeader className="flex flex-row items-center justify-between">
						<div>
							<CardTitle className="text-base">最近连接测试</CardTitle>
							<CardDescription>展示最近 20 次后端记录的 Hive 连接自测结果。</CardDescription>
						</div>
						<Button type="button" variant="ghost" size="sm" onClick={loadLogs} disabled={loadingLogs}>
							<Icon icon="solar:refresh-bold" className="mr-1 h-4 w-4" /> 刷新
						</Button>
					</CardHeader>
					<CardContent className="max-h-[280px] overflow-y-auto">
						<table className="w-full table-fixed border-collapse text-sm">
							<thead className="bg-muted/40 text-left text-xs text-muted-foreground">
								<tr>
									<th className="px-3 py-2 font-medium">时间</th>
									<th className="px-3 py-2 font-medium">结果</th>
									<th className="px-3 py-2 font-medium">耗时</th>
									<th className="px-3 py-2 font-medium">备注</th>
								</tr>
							</thead>
							<tbody>
								{logs.map((log) => (
									<tr key={log.id} className="border-b last:border-b-0">
										<td className="px-3 py-2 text-xs text-muted-foreground">
											{log.createdAt ? new Date(log.createdAt).toLocaleString() : "-"}
										</td>
										<td className="px-3 py-2">
											<Badge variant={log.result === "SUCCESS" ? "default" : "destructive"}>{log.result}</Badge>
										</td>
										<td className="px-3 py-2 text-xs text-muted-foreground">{log.elapsedMs ?? "-"} ms</td>
										<td className="px-3 py-2 text-xs text-muted-foreground">{log.message || "-"}</td>
									</tr>
								))}
								{!logs.length && (
									<tr>
										<td colSpan={4} className="px-3 py-6 text-center text-xs text-muted-foreground">
											{loadingLogs ? "加载中…" : "暂无测试记录"}
										</td>
									</tr>
								)}
							</tbody>
						</table>
					</CardContent>
				</Card>
			</div>
			<GenericDataSourceDialog
				open={genericDialogOpen}
				mode={genericDialogMode}
				submitting={savingGeneric}
				source={genericEditing ?? undefined}
				onClose={closeGenericDialog}
				onSubmit={handleGenericSubmit}
			/>
		</div>
	);
}

const parseKeyValueEntries = (raw?: string) => {
	if (!raw) return [] as [string, string][];
	const entries: [string, string][] = [];
	raw.split(/\r?\n/).forEach((line) => {
		const trimmed = line.trim();
		if (!trimmed || trimmed.startsWith("#")) {
			return;
		}
		const eqIndex = trimmed.indexOf("=");
		if (eqIndex <= 0) return;
		const key = trimmed.slice(0, eqIndex).trim();
		const value = trimmed.slice(eqIndex + 1).trim();
		if (key) {
			entries.push([key, value]);
		}
	});
	return entries;
};

const buildJdbcUrl = (values: Partial<FormValues>) => {
	const host = values.host?.trim();
	const principal = values.servicePrincipal?.trim();
	if (!host || !principal) {
		return "";
	}
	const port = values.port ?? 10000;
	const database = values.database?.trim() || "default";
	const params: string[] = [`principal=${principal}`];
	if (values.useHttpTransport) {
		params.push("transportMode=http");
		if (values.httpPath?.trim()) {
			params.push(`httpPath=${values.httpPath.trim()}`);
		}
	}
	if (values.useSsl) {
		params.push("ssl=true");
	}
	const extraEntries = parseKeyValueEntries(values.extraParams);
	const allowedUrlKeys = new Set([
		"transportMode", // Apache Hive HTTP mode
		"httpPath", // Apache Hive HTTP path
		"ssl", // Apache Hive TLS
		"sasl.qop", // SASL QOP (optional)
		"principal", // will be present already; duplicate filtered below
	]);
	extraEntries.forEach(([key, value]) => {
		if (key === "ssl" && values.useSsl) return; // avoid duplicate
		if (key === "principal") return; // already included
		if (!allowedUrlKeys.has(key)) return; // drop Simba/自定义键
		params.push(`${key}=${value}`);
	});
	return `jdbc:hive2://${host}:${port}/${database};${params.join(";")}`;
};
