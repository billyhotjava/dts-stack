import { useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Divider, Form, Input, InputNumber, Select, Space, Switch, Tag } from "antd";
import { adminApi } from "@/admin/api/adminApi";
import type { HiveConnectionPersistRequest, HiveConnectionTestRequest, HiveConnectionTestResult, InceptorConfig } from "@/types/infra";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Button } from "@/ui/button";
import { Text, Title } from "@/ui/typography";
import { toast } from "sonner";

type FormValues = HiveConnectionPersistRequest & { jdbcPropertiesRaw?: string };

const AUTH_METHOD_OPTIONS = [
	{ value: "KEYTAB", label: "Kerberos Keytab" },
	{ value: "PASSWORD", label: "密码" },
];

const buildPropertiesRaw = (props?: Record<string, string>): string => {
	if (!props) return "";
	return Object.entries(props)
		.map(([key, value]) => `${key}=${value ?? ""}`)
		.join("\n");
};

const parseProperties = (raw?: string): Record<string, string> => {
	const output: Record<string, string> = {};
	if (!raw) return output;
	raw
		.split("\n")
		.map((line) => line.trim())
		.filter(Boolean)
		.forEach((line) => {
			const [key, ...rest] = line.split("=");
			const trimmedKey = key.trim();
			if (!trimmedKey) return;
			output[trimmedKey] = rest.join("=").trim();
		});
	return output;
};

const buildTestPayload = (values: FormValues): HiveConnectionTestRequest => ({
	jdbcUrl: values.jdbcUrl,
	loginPrincipal: values.loginPrincipal,
	authMethod: values.authMethod,
	krb5Conf: values.krb5Conf || undefined,
	keytabBase64: values.keytabBase64 || undefined,
	keytabFileName: values.keytabFileName || undefined,
	password: values.password || undefined,
	jdbcProperties: parseProperties(values.jdbcPropertiesRaw),
	proxyUser: values.proxyUser || undefined,
});

const buildPublishPayload = (values: FormValues, testResult: HiveConnectionTestResult | null): HiveConnectionPersistRequest => ({
	name: values.name,
	description: values.description || undefined,
	jdbcUrl: values.jdbcUrl,
	loginPrincipal: values.loginPrincipal,
	authMethod: values.authMethod,
	krb5Conf: values.krb5Conf || undefined,
	keytabBase64: values.keytabBase64 || undefined,
	keytabFileName: values.keytabFileName || undefined,
	password: values.password || undefined,
	jdbcProperties: parseProperties(values.jdbcPropertiesRaw),
	proxyUser: values.proxyUser || undefined,
	servicePrincipal: values.servicePrincipal,
	host: values.host,
	port: values.port,
	database: values.database,
	useHttpTransport: Boolean(values.useHttpTransport),
	httpPath: values.httpPath || undefined,
	useSsl: Boolean(values.useSsl),
	useCustomJdbc: Boolean(values.useCustomJdbc),
	customJdbcUrl: values.customJdbcUrl || undefined,
	lastTestElapsedMillis: testResult?.elapsedMillis ?? values.lastTestElapsedMillis,
	engineVersion: testResult?.engineVersion ?? values.engineVersion ?? null,
	driverVersion: testResult?.driverVersion ?? values.driverVersion ?? null,
});

export default function DataLakeConfigView() {
	const queryClient = useQueryClient();
	const [form] = Form.useForm<FormValues>();
	const [testResult, setTestResult] = useState<HiveConnectionTestResult | null>(null);
	const [testing, setTesting] = useState(false);
	const [saving, setSaving] = useState(false);
	const [refreshing, setRefreshing] = useState(false);

	const { data: config } = useQuery({
		queryKey: ["admin", "inceptor-config"],
		queryFn: adminApi.getInceptorConfig,
	});

	const { data: flags } = useQuery({
		queryKey: ["admin", "inceptor-flags"],
		queryFn: adminApi.getInceptorFlags,
	});

	const authMethod = Form.useWatch("authMethod", form);
	const useHttpTransport = Form.useWatch("useHttpTransport", form);
	const useCustomJdbc = Form.useWatch("useCustomJdbc", form);

	const statusTag = useMemo(() => {
		if (!flags?.inceptorStatus) return <Tag color="default">未配置</Tag>;
		if (flags.inceptorStatus === "ACTIVE") return <Tag color="green">已启用</Tag>;
		if (flags.inceptorStatus === "NOT_CONFIGURED") return <Tag color="default">未配置</Tag>;
		return <Tag color="orange">{flags.inceptorStatus}</Tag>;
	}, [flags?.inceptorStatus]);

	useEffect(() => {
		const current = config as InceptorConfig | null | undefined;
		if (!current) {
			form.resetFields();
			return;
		}
		form.setFieldsValue({
			name: current.name || "默认数据湖",
			description: current.description || "",
			jdbcUrl: current.jdbcUrl || current.customJdbcUrl || "",
			loginPrincipal: current.loginPrincipal || "",
			authMethod: (current.authMethod as FormValues["authMethod"]) || "KEYTAB",
			krb5Conf: current.krb5Conf || "",
			keytabBase64: current.keytabBase64 || "",
			keytabFileName: current.keytabFileName || "",
			password: current.password || "",
			jdbcPropertiesRaw: buildPropertiesRaw(current.jdbcProperties),
			proxyUser: current.proxyUser || "",
			servicePrincipal: current.servicePrincipal || "",
			host: current.host || "",
			port: current.port ?? 10000,
			database: current.database || "",
			useHttpTransport: Boolean(current.useHttpTransport),
			httpPath: current.httpPath || "",
			useSsl: Boolean(current.useSsl),
			useCustomJdbc: Boolean(current.useCustomJdbc),
			customJdbcUrl: current.customJdbcUrl || "",
			lastTestElapsedMillis: current.lastTestElapsedMillis,
			engineVersion: current.engineVersion ?? undefined,
			driverVersion: current.driverVersion ?? undefined,
		});
	}, [config, form]);

	const handleTest = async () => {
		try {
			setTesting(true);
			const values = await form.validateFields(["jdbcUrl", "loginPrincipal", "authMethod"]);
			const payload = buildTestPayload(values);
			const result = await adminApi.testInceptorConnection(payload, (config as InceptorConfig | null | undefined)?.id);
			setTestResult(result);
			if (result.success) {
				toast.success("连接成功");
			} else {
				toast.error(result.message || "连接失败");
			}
			queryClient.invalidateQueries({ queryKey: ["admin", "inceptor-flags"] });
		} catch (error: any) {
			if (error?.errorFields?.length) {
				return;
			}
			toast.error(error?.message || "测试失败");
		} finally {
			setTesting(false);
		}
	};

	const handlePublish = async () => {
		try {
			setSaving(true);
			const values = await form.validateFields();
			const payload = buildPublishPayload(values, testResult);
			await adminApi.publishInceptorConfig(payload);
			toast.success("已保存并发布");
			queryClient.invalidateQueries({ queryKey: ["admin", "inceptor-config"] });
			queryClient.invalidateQueries({ queryKey: ["admin", "inceptor-flags"] });
		} catch (error: any) {
			if (error?.errorFields?.length) {
				return;
			}
			toast.error(error?.message || "保存失败");
		} finally {
			setSaving(false);
		}
	};

	const handleRefresh = async () => {
		try {
			setRefreshing(true);
			await adminApi.refreshInceptorRegistry();
			toast.success("已触发刷新");
			queryClient.invalidateQueries({ queryKey: ["admin", "inceptor-flags"] });
		} catch (error: any) {
			toast.error(error?.message || "刷新失败");
		} finally {
			setRefreshing(false);
		}
	};

	return (
		<div className="space-y-6">
			<div className="flex flex-wrap items-center justify-between gap-3">
				<div>
					<Title as="h4">数据湖配置</Title>
					<Text variant="body3" className="text-muted-foreground">
						配置 Inceptor 数据湖连接，发布后平台将自动使用该连接完成默认数据湖访问。
					</Text>
				</div>
				<Space>
					<Button variant="outline" onClick={handleRefresh} disabled={refreshing}>
						刷新注册
					</Button>
					<Button variant="secondary" onClick={handleTest} disabled={testing}>
						测试连接
					</Button>
					<Button onClick={handlePublish} disabled={saving}>
						保存并发布
					</Button>
				</Space>
			</div>

			<Card>
				<CardHeader>
					<CardTitle>当前状态</CardTitle>
				</CardHeader>
				<CardContent className="space-y-4">
					<div className="flex flex-wrap items-center gap-3">
						{statusTag}
						<Text variant="body3" className="text-muted-foreground">
							{flags?.dataSourceName || config?.name || "未配置数据湖连接"}
						</Text>
					</div>
					<div className="grid gap-3 md:grid-cols-3">
						<div>
							<Text variant="body3" className="text-muted-foreground">
								最近验证
							</Text>
							<Text variant="body2">{flags?.lastVerifiedAt || config?.lastVerifiedAt || "--"}</Text>
						</div>
						<div>
							<Text variant="body3" className="text-muted-foreground">
								心跳状态
							</Text>
							<Text variant="body2">{flags?.heartbeatStatus || config?.heartbeatStatus || "--"}</Text>
						</div>
						<div>
							<Text variant="body3" className="text-muted-foreground">
								最后同步
							</Text>
							<Text variant="body2">{flags?.integrationStatus?.lastSyncAt || "--"}</Text>
						</div>
					</div>
					{config?.lastError ? (
						<Alert type="error" message="最近一次错误" description={config.lastError} showIcon />
					) : null}
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle>连接参数</CardTitle>
				</CardHeader>
				<CardContent>
					<Form<FormValues> layout="vertical" form={form}>
						<div className="grid gap-4 md:grid-cols-2">
							<Form.Item name="name" label="连接名称" rules={[{ required: true, message: "请填写连接名称" }]}>
								<Input placeholder="如：Inceptor-生产集群" />
							</Form.Item>
							<Form.Item name="description" label="描述">
								<Input placeholder="可选描述" />
							</Form.Item>
							<Form.Item name="jdbcUrl" label="JDBC URL" rules={[{ required: true, message: "请填写 JDBC URL" }]}>
								<Input placeholder="jdbc:hive2://host:10000/default" />
							</Form.Item>
							<Form.Item name="authMethod" label="认证方式" rules={[{ required: true, message: "请选择认证方式" }]}>
								<Select options={AUTH_METHOD_OPTIONS} />
							</Form.Item>
						</div>

						<Divider />

						<div className="grid gap-4 md:grid-cols-3">
							<Form.Item name="host" label="主机" rules={[{ required: true, message: "请填写主机" }]}>
								<Input placeholder="如：inceptor-prod" />
							</Form.Item>
							<Form.Item name="port" label="端口" rules={[{ required: true, message: "请填写端口" }]}>
								<InputNumber min={1} max={65535} className="w-full" />
							</Form.Item>
							<Form.Item name="database" label="默认数据库" rules={[{ required: true, message: "请填写数据库" }]}>
								<Input placeholder="default" />
							</Form.Item>
							<Form.Item
								name="loginPrincipal"
								label="登录主体"
								rules={[{ required: true, message: "请填写登录主体" }]}
							>
								<Input placeholder="如：hive/_HOST@REALM" />
							</Form.Item>
							<Form.Item
								name="servicePrincipal"
								label="服务主体"
								rules={[{ required: true, message: "请填写服务主体" }]}
							>
								<Input placeholder="如：hive/_HOST@REALM" />
							</Form.Item>
							<Form.Item name="proxyUser" label="代理用户">
								<Input placeholder="可选" />
							</Form.Item>
						</div>

						<Divider />

						<div className="grid gap-4 md:grid-cols-3">
							<Form.Item name="useSsl" label="启用 SSL" valuePropName="checked">
								<Switch />
							</Form.Item>
							<Form.Item name="useHttpTransport" label="HTTP 传输" valuePropName="checked">
								<Switch />
							</Form.Item>
							<Form.Item name="useCustomJdbc" label="自定义 JDBC" valuePropName="checked">
								<Switch />
							</Form.Item>
						</div>

						{useHttpTransport ? (
							<Form.Item name="httpPath" label="HTTP Path">
								<Input placeholder="如：/gateway/default/hive" />
							</Form.Item>
						) : null}

						{useCustomJdbc ? (
							<Form.Item name="customJdbcUrl" label="自定义 JDBC URL">
								<Input placeholder="jdbc:inceptor2://host:port/default" />
							</Form.Item>
						) : null}

						<Divider />

						<div className="grid gap-4 md:grid-cols-2">
							<Form.Item name="krb5Conf" label="krb5.conf">
								<Input.TextArea rows={4} placeholder="可选，填写 Kerberos 配置内容" />
							</Form.Item>
							{authMethod === "KEYTAB" ? (
								<>
									<Form.Item name="keytabFileName" label="Keytab 文件名">
										<Input placeholder="user.keytab" />
									</Form.Item>
									<Form.Item name="keytabBase64" label="Keytab Base64">
										<Input.TextArea rows={4} placeholder="粘贴 base64 内容" />
									</Form.Item>
								</>
							) : (
								<Form.Item name="password" label="密码">
									<Input.Password placeholder="请输入密码" />
								</Form.Item>
							)}
						</div>

						<Divider />

						<Form.Item name="jdbcPropertiesRaw" label="JDBC 扩展参数（每行 key=value）">
							<Input.TextArea rows={4} placeholder="transportMode=http" />
						</Form.Item>
					</Form>
				</CardContent>
			</Card>

			{testResult ? (
				<Card>
					<CardHeader>
						<CardTitle>最新测试结果</CardTitle>
					</CardHeader>
					<CardContent>
						<Alert
							type={testResult.success ? "success" : "error"}
							message={testResult.message || (testResult.success ? "连接成功" : "连接失败")}
							description={`耗时 ${testResult.elapsedMillis ?? "--"} ms`}
							showIcon
						/>
					</CardContent>
				</Card>
			) : null}
		</div>
	);
}
