import { useEffect, useState, type ReactNode } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button as AntButton, Card, Divider, Form, Input, Space, Switch, Tabs, type FormInstance } from "antd";
import { Database, RefreshCw, Workflow } from "lucide-react";
import { adminApi } from "@/admin/api/adminApi";
import { useRouter } from "@/routes/hooks";
import type { InfraServiceSettingsPayload, InfraServiceTestResult } from "@/types/infra";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { toast } from "sonner";

type ServiceKey = "addax" | "airflow" | "openmetadata" | "dbt" | "platform";

interface ServicePanelProps {
	service: ServiceKey;
	title: string;
	restartHint?: string;
	capability: string;
	formContent: (form: FormInstance<Record<string, any>>) => ReactNode;
}

const SERVICE_CAPABILITIES: Record<ServiceKey, string> = {
	platform: "\u5143\u6570\u636e\u8054\u52a8",
	addax: "\u79bb\u7ebf\u4f5c\u4e1a\u751f\u6210",
	airflow: "\u4efb\u52a1\u8c03\u5ea6\u7f16\u6392",
	openmetadata: "\u8840\u7f18\u4e0e\u5143\u6570\u636e\u91c7\u96c6",
	dbt: "\u5efa\u6a21\u4e0e\u8f6c\u6362\u53c2\u6570",
};

const QUICK_SWITCHES: Array<{ key: ServiceKey; label: string }> = [
	{ key: "platform", label: "\u5e73\u53f0\u8054\u52a8" },
	{ key: "addax", label: "Addax" },
	{ key: "airflow", label: "Airflow" },
	{ key: "openmetadata", label: "OpenMetadata" },
	{ key: "dbt", label: "DBT" },
];

const normalizeSettings = (data?: InfraServiceSettingsPayload | null) => {
	const settings = data?.settings || {};
	return { enabled: true, catalogSyncOnDataSource: true, ...settings };
};

const buildSettingsPayload = (values: Record<string, any>) => {
	return { ...values };
};

function ServicePanel({ service, title, restartHint, capability, formContent }: ServicePanelProps) {
	const queryClient = useQueryClient();
	const [form] = Form.useForm<Record<string, any>>();
	const [testResult, setTestResult] = useState<InfraServiceTestResult | null>(null);

	const { data, isFetching } = useQuery({
		queryKey: ["admin", "infra-settings", service],
		queryFn: () => adminApi.getIntegrationSettings(service),
	});

	useEffect(() => {
		form.setFieldsValue(normalizeSettings(data));
	}, [data, form]);

	const enabled = Boolean(form.getFieldValue("enabled"));

	const handleSave = async () => {
		try {
			const values = await form.validateFields();
			const payload = buildSettingsPayload(values);
			await adminApi.updateIntegrationSettings(service, payload);
			toast.success("\u914d\u7f6e\u5df2\u4fdd\u5b58");
			if (restartHint) {
				toast.info(restartHint);
			}
			queryClient.invalidateQueries({ queryKey: ["admin", "infra-settings", service] });
		} catch (error: any) {
			if (error?.errorFields) return;
			toast.error(error?.message || "\u4fdd\u5b58\u5931\u8d25");
		}
	};

	const handleTest = async () => {
		try {
			const values = await form.validateFields();
			const payload = buildSettingsPayload(values);
			const result = await adminApi.testIntegrationSettings(service, payload);
			setTestResult(result || null);
			if (result?.success) {
				toast.success(result?.message || "\u8fde\u63a5\u6210\u529f");
			} else {
				toast.error(result?.message || "\u8fde\u63a5\u5931\u8d25");
			}
		} catch (error: any) {
			if (error?.errorFields) return;
			setTestResult({ success: false, message: error?.message || "\u6d4b\u8bd5\u5931\u8d25" });
			toast.error(error?.message || "\u6d4b\u8bd5\u5931\u8d25");
		}
	};

	return (
		<div data-testid={`admin-infra-service-panel-${service}`}>
			<Card
				title={title}
				extra={
					<div className="flex flex-wrap items-center gap-2">
						<Badge variant={enabled ? "success" : "outline"} className="rounded-full px-2.5 py-1">
							{enabled ? "\u5df2\u542f\u7528" : "\u672a\u542f\u7528"}
						</Badge>
						<Badge variant="info" className="rounded-full px-2.5 py-1">
							{capability}
						</Badge>
					</div>
				}
			>
				<Form form={form} layout="vertical" requiredMark disabled={isFetching} className="max-w-4xl">
					{restartHint ? <Alert className="mb-4" type="warning" showIcon message={restartHint} /> : null}
					<div className="grid gap-x-5 xl:grid-cols-2">{formContent(form)}</div>
					<Space wrap className="pt-2">
						<AntButton data-testid={`admin-infra-service-save-${service}`} type="primary" onClick={handleSave}>
							\u4fdd\u5b58\u914d\u7f6e
						</AntButton>
						<AntButton data-testid={`admin-infra-service-test-${service}`} onClick={handleTest}>
							\u6d4b\u8bd5\u8fde\u63a5
						</AntButton>
					</Space>
					{testResult ? (
						<div data-testid={`admin-infra-service-test-result-${service}`}>
							<Alert
								className="mt-4"
								type={testResult.success ? "success" : "error"}
								showIcon
								message={testResult.message || (testResult.success ? "\u6d4b\u8bd5\u6210\u529f" : "\u6d4b\u8bd5\u5931\u8d25")}
								description={
									(testResult.status || testResult.body) ? (
										<div className="space-y-1">
											{testResult.status ? <div>\u72b6\u6001\u7801: {testResult.status}</div> : null}
											{testResult.body ? <pre className="whitespace-pre-wrap text-xs">{testResult.body}</pre> : null}
										</div>
									) : undefined
								}
							/>
						</div>
					) : null}
				</Form>
			</Card>
		</div>
	);
}

export default function InfraSettingsView() {
	const { push } = useRouter();
	const [activeService, setActiveService] = useState<ServiceKey>("platform");

	const items = [
		{
			key: "platform",
			label: "\u5e73\u53f0",
			children: (
				<ServicePanel
					service="platform"
					title="\u5e73\u53f0\u4fa7\u8054\u52a8"
					restartHint="\u8be5\u914d\u7f6e\u901a\u5e38\u53ef\u70ed\u751f\u6548\uff1b\u5982\u65e0\u6548\u8bf7\u91cd\u542f dts-platform\u3002"
					capability={SERVICE_CAPABILITIES.platform}
					formContent={() => (
						<>
							<Form.Item
								label="\u6570\u636e\u6e90\u81ea\u52a8\u540c\u6b65\u5b57\u6bb5"
								name="catalogSyncOnDataSource"
								valuePropName="checked"
								className="xl:col-span-2"
							>
								<Switch />
							</Form.Item>
							<div className="xl:col-span-2 rounded-2xl border border-border/70 bg-muted/40 p-4 text-sm leading-6 text-muted-foreground">
								\u5f00\u542f\u540e\uff0c\u65b0\u589e\u6216\u66f4\u65b0\u6570\u636e\u6e90\u4f1a\u81ea\u52a8\u540c\u6b65\u5143\u6570\u636e\u3002JDBC \u7c7b\u6570\u636e\u6e90\u8d70\u5143\u6570\u636e\u6293\u53d6\uff0c\u6587\u4ef6\u7c7b\u6570\u636e\u6e90\u8d70\u5b57\u6bb5\u89e3\u6790\uff0c\u5e76\u81ea\u52a8\u751f\u6210 ODS \u8349\u7a3f\u3002
							</div>
						</>
					)}
				/>
			),
		},
		{
			key: "addax",
			label: "Addax",
			children: (
				<ServicePanel
					service="addax"
					title="Addax \u4f5c\u4e1a"
					restartHint="\u4fdd\u5b58\u540e\u5efa\u8bae\u91cd\u5efa\u5165\u6e56\u4efb\u52a1\uff1b\u955c\u50cf\u53d8\u66f4\u9700\u91cd\u542f\u8c03\u5ea6\u76f8\u5173\u670d\u52a1\u3002"
					capability={SERVICE_CAPABILITIES.addax}
					formContent={() => (
						<>
							<Form.Item label="\u542f\u7528" name="enabled" valuePropName="checked">
								<Switch />
							</Form.Item>
							<Form.Item
								label="\u4f5c\u4e1a\u76ee\u5f55"
								name="jobDir"
								rules={[{ required: true, message: "\u8bf7\u8f93\u5165\u4f5c\u4e1a\u76ee\u5f55" }]}
							>
								<Input placeholder="/opt/airflow/dags" />
							</Form.Item>
							<Form.Item
								label="\u955c\u50cf"
								name="image"
								rules={[{ required: true, message: "\u8bf7\u8f93\u5165\u955c\u50cf" }]}
								className="xl:col-span-2"
							>
								<Input placeholder="quay.io/wgzhao/addax:6.0.8" />
							</Form.Item>
							<div className="xl:col-span-2 rounded-2xl border border-border/70 bg-muted/40 p-4 text-sm leading-6 text-muted-foreground">
								Addax \u4f5c\u4e1a\u76ee\u5f55\u9700\u4e0e Airflow DAG \u76ee\u5f55\u4e00\u81f4\u3002\u5199\u5165\u5668\u901a\u7528\u6a21\u677f\u8bf7\u5728\u6570\u636e\u6e56\u7ba1\u7406\u4e2d\u7ef4\u62a4\uff0c\u4efb\u52a1\u4fa7\u53ea\u586b\u5199\u8868\u540d\u4e0e\u5dee\u5f02\u5316\u53c2\u6570\u3002
							</div>
						</>
					)}
				/>
			),
		},
		{
			key: "airflow",
			label: "Airflow",
			children: (
				<ServicePanel
					service="airflow"
					title="Airflow \u8c03\u5ea6"
					restartHint="\u4fdd\u5b58\u540e\u5efa\u8bae\u91cd\u542f dts-ingestion \u4e0e dts-platform\uff0c\u4f7f\u8fde\u63a5\u53c2\u6570\u4e00\u81f4\u3002"
					capability={SERVICE_CAPABILITIES.airflow}
					formContent={() => (
						<>
							<Form.Item label="\u542f\u7528" name="enabled" valuePropName="checked">
								<Switch />
							</Form.Item>
							<Form.Item
								label="Airflow \u5730\u5740"
								name="baseUrl"
								rules={[{ required: true, message: "\u8bf7\u8f93\u5165 Airflow \u5730\u5740" }]}
							>
								<Input placeholder="http://dts-airflow-web:8080" />
							</Form.Item>
							<Form.Item label="API Path" name="apiPath">
								<Input placeholder="/api/v1" />
							</Form.Item>
							<Form.Item
								label="DAG \u76ee\u5f55"
								name="dagsDir"
								rules={[{ required: true, message: "\u8bf7\u8f93\u5165 DAG \u76ee\u5f55" }]}
							>
								<Input placeholder="/opt/airflow/dags" />
							</Form.Item>
							<Form.Item
								label="\u7528\u6237\u540d"
								name="username"
								rules={[{ required: true, message: "\u8bf7\u8f93\u5165\u7528\u6237\u540d" }]}
							>
								<Input placeholder="airflow" />
							</Form.Item>
							<Form.Item
								label="\u5bc6\u7801"
								name="password"
								rules={[{ required: true, message: "\u8bf7\u8f93\u5165\u5bc6\u7801" }]}
							>
								<Input.Password placeholder="******" />
							</Form.Item>
							<Form.Item label="\u9ed8\u8ba4 DAG ID" name="dagId" className="xl:col-span-2">
								<Input placeholder="dbt_load" />
							</Form.Item>
						</>
					)}
				/>
			),
		},
		{
			key: "openmetadata",
			label: "OpenMetadata",
			children: (
				<ServicePanel
					service="openmetadata"
					title="OpenMetadata \u5143\u6570\u636e"
					restartHint="\u4fdd\u5b58\u540e\u5efa\u8bae\u91cd\u542f dts-ingestion\uff0c\u786e\u4fdd\u91c7\u96c6\u5668\u8bfb\u53d6\u6700\u65b0\u914d\u7f6e\u3002"
					capability={SERVICE_CAPABILITIES.openmetadata}
					formContent={() => (
						<>
							<Form.Item label="\u542f\u7528" name="enabled" valuePropName="checked">
								<Switch />
							</Form.Item>
							<Form.Item
								label="OpenMetadata \u5730\u5740"
								name="baseUrl"
								rules={[{ required: true, message: "\u8bf7\u8f93\u5165 OpenMetadata \u5730\u5740" }]}
							>
								<Input placeholder="http://openmetadata:8585" />
							</Form.Item>
							<Form.Item label="API Path" name="apiPath">
								<Input placeholder="/api/v1" />
							</Form.Item>
							<Form.Item
								label="Token"
								name="authToken"
								rules={[{ required: true, message: "\u8bf7\u8f93\u5165 Token" }]}
							>
								<Input.Password placeholder="Bearer ..." />
							</Form.Item>
							<Form.Item label="\u8868\u5b57\u6bb5" name="tableFields" className="xl:col-span-2">
								<Input placeholder="columns,owner,tags,domain" />
							</Form.Item>
							<Divider orientation="left" className="xl:col-span-2">
								\u670d\u52a1\u6807\u8bc6
							</Divider>
							<Form.Item
								label="\u6e90\u670d\u52a1\u540d\u79f0"
								name="sourceServiceName"
								rules={[{ required: true, message: "\u8bf7\u8f93\u5165\u6e90\u670d\u52a1\u540d\u79f0" }]}
							>
								<Input placeholder="source_service" />
							</Form.Item>
							<Form.Item
								label="\u6e90\u670d\u52a1\u7c7b\u578b"
								name="sourceServiceType"
								rules={[{ required: true, message: "\u8bf7\u8f93\u5165\u6e90\u670d\u52a1\u7c7b\u578b" }]}
							>
								<Input placeholder="Postgres" />
							</Form.Item>
							<Form.Item
								label="\u76ee\u6807\u670d\u52a1\u540d\u79f0"
								name="destinationServiceName"
								rules={[{ required: true, message: "\u8bf7\u8f93\u5165\u76ee\u6807\u670d\u52a1\u540d\u79f0" }]}
							>
								<Input placeholder="destination_service" />
							</Form.Item>
							<Form.Item
								label="\u76ee\u6807\u670d\u52a1\u7c7b\u578b"
								name="destinationServiceType"
								rules={[{ required: true, message: "\u8bf7\u8f93\u5165\u76ee\u6807\u670d\u52a1\u7c7b\u578b" }]}
							>
								<Input placeholder="Postgres" />
							</Form.Item>
							<Form.Item
								label="\u6e90\u6570\u636e\u5e93"
								name="sourceDatabase"
								rules={[{ required: true, message: "\u8bf7\u8f93\u5165\u6e90\u6570\u636e\u5e93" }]}
							>
								<Input placeholder="source_db" />
							</Form.Item>
							<Form.Item
								label="\u6e90 Schema"
								name="sourceSchema"
								rules={[{ required: true, message: "\u8bf7\u8f93\u5165\u6e90 Schema" }]}
							>
								<Input placeholder="public" />
							</Form.Item>
							<Form.Item
								label="\u76ee\u6807\u6570\u636e\u5e93"
								name="destinationDatabase"
								rules={[{ required: true, message: "\u8bf7\u8f93\u5165\u76ee\u6807\u6570\u636e\u5e93" }]}
							>
								<Input placeholder="ods" />
							</Form.Item>
							<Form.Item
								label="\u76ee\u6807 Schema"
								name="destinationSchema"
								rules={[{ required: true, message: "\u8bf7\u8f93\u5165\u76ee\u6807 Schema" }]}
							>
								<Input placeholder="public" />
							</Form.Item>
							<Divider orientation="left" className="xl:col-span-2">
								\u91c7\u96c6\u8bbe\u7f6e
							</Divider>
							<Form.Item label="\u542f\u7528\u91c7\u96c6" name="ingestionEnabled" valuePropName="checked">
								<Switch />
							</Form.Item>
							<Form.Item label="\u91c7\u96c6\u4efb\u52a1\u524d\u7f00" name="ingestionPrefix">
								<Input placeholder="dts_ingest" />
							</Form.Item>
							<Form.Item label="\u91c7\u96c6\u8c03\u5ea6" name="ingestionSchedule" className="xl:col-span-2">
								<Input placeholder="0 * * * *" />
							</Form.Item>
						</>
					)}
				/>
			),
		},
		{
			key: "dbt",
			label: "DBT",
			children: (
				<ServicePanel
					service="dbt"
					title="DBT \u4efb\u52a1"
					restartHint="\u542f\u7528\u524d\u8bf7\u540c\u6b65\u5b8c\u6210 dts-ingestion \u4e0e dts-platform \u7684 DBT \u63a5\u5165\u8054\u8c03\u3002"
					capability={SERVICE_CAPABILITIES.dbt}
					formContent={() => (
						<>
							<div className="xl:col-span-2">
								<Alert
									type="info"
									showIcon
									message="\u5f53\u524d\u57fa\u7ebf\u5148\u7ba1\u7406\u8fde\u63a5\u53c2\u6570"
									description="\u4efb\u52a1\u53d1\u5e03\u548c\u7f16\u6392\u4ecd\u8d70\u5e73\u53f0\u4fa7\u94fe\u8def\uff0c\u5f85\u73b0\u573a\u786e\u8ba4\u63a5\u5165\u987a\u5e8f\u540e\u518d\u5f00\u653e\u81ea\u52a8\u6267\u884c\u5165\u53e3\u3002"
								/>
							</div>
							<Form.Item label="\u542f\u7528" name="enabled" valuePropName="checked">
								<Switch />
							</Form.Item>
							<Form.Item label="DBT \u5730\u5740" name="baseUrl">
								<Input placeholder="http://dbt-service:8080" />
							</Form.Item>
							<Form.Item label="API Path" name="apiPath">
								<Input placeholder="/api/v1" />
							</Form.Item>
							<Form.Item label="\u7528\u6237\u540d" name="username">
								<Input />
							</Form.Item>
							<Form.Item label="\u5bc6\u7801" name="password">
								<Input.Password />
							</Form.Item>
							<Form.Item label="Token" name="token">
								<Input.Password />
							</Form.Item>
						</>
					)}
				/>
			),
		},
	];

	return (
		<div className="space-y-4" data-testid="admin-infra-settings-page">
			<Card
				title={"\u96c6\u6210\u8bbe\u7f6e"}
				extra={
					<>
						<Button variant="outline" onClick={() => push("/admin/data-lake")}>
							<Database className="h-4 w-4" />
							\u6570\u636e\u6e56\u914d\u7f6e
						</Button>
						<Button variant="outline" onClick={() => push("/admin/ops")}>
							<RefreshCw className="h-4 w-4" />
							\u8fd0\u7ef4\u914d\u7f6e
						</Button>
					</>
				}
			>
				<div className="flex flex-wrap items-center gap-2 mb-4">
					{QUICK_SWITCHES.map((item) => (
						<Button
							key={item.key}
							data-testid={`admin-infra-switch-${item.key}`}
							variant={activeService === item.key ? "contrast" : "outline"}
							size="sm"
							onClick={() => setActiveService(item.key)}
						>
							{item.label}
						</Button>
					))}
				</div>

				<div data-testid="admin-infra-settings-tabs">
					<Tabs activeKey={activeService} onChange={(key) => setActiveService(key as ServiceKey)} items={items} />
				</div>
			</Card>

			<div className="grid gap-4 xl:grid-cols-[1.2fr_0.8fr]">
				<Card title={"\u8054\u52a8\u5efa\u8bae"}>
					<div className="grid gap-3">
						{[
							"Addax \u4f5c\u4e1a\u76ee\u5f55\u4e0e Airflow DAG \u76ee\u5f55\u5fc5\u987b\u4fdd\u6301\u4e00\u81f4\u3002",
							"OpenMetadata \u7684\u6e90/\u76ee\u6807\u670d\u52a1\u547d\u540d\u5e94\u4e0e\u5e73\u53f0\u771f\u5b9e\u8d44\u4ea7\u547d\u540d\u4fdd\u6301\u540c\u53e3\u5f84\u3002",
							"DBT \u82e5\u53ea\u4fdd\u5b58\u8fde\u63a5\u53c2\u6570\uff0c\u5148\u4e0d\u8981\u5728\u4e1a\u52a1\u9875\u66b4\u9732\u81ea\u52a8\u6267\u884c\u5165\u53e3\u3002",
						].map((item) => (
							<div key={item} className="rounded-[22px] border border-border/70 bg-muted/35 px-4 py-3 text-sm text-muted-foreground">
								{item}
							</div>
						))}
					</div>
				</Card>

				<Card title={"\u5173\u8054\u5165\u53e3"}>
					<div className="grid gap-3">
						{[
							{ title: "\u6570\u636e\u6e56\u914d\u7f6e", path: "/admin/data-lake", icon: <Database className="h-4 w-4" /> },
							{ title: "\u8fd0\u7ef4\u914d\u7f6e", path: "/admin/ops", icon: <Workflow className="h-4 w-4" /> },
							{ title: "\u5de5\u4f5c\u6d41\u914d\u7f6e", path: "/admin/workflows", icon: <Workflow className="h-4 w-4" /> },
						].map((item) => (
							<button
								key={item.path}
								type="button"
								onClick={() => push(item.path)}
								className="flex items-center gap-2 rounded-[22px] border border-border/70 bg-muted/35 px-4 py-4 text-left text-sm font-semibold text-foreground transition hover:bg-muted/55"
							>
								{item.icon}
								{item.title}
							</button>
						))}
					</div>
				</Card>
			</div>
		</div>
	);
}
