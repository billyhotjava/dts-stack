import { useEffect, useMemo, useState, type ReactNode } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button as AntButton, Divider, Form, Input, Space, Switch, Tabs, type FormInstance } from "antd";
import { Database, Plug, RefreshCw, Server, Shield, Workflow } from "lucide-react";
import { adminApi } from "@/admin/api/adminApi";
import {
	AdminFilterBar,
	AdminMetaPill,
	AdminPageHeader,
	AdminSectionCard,
	AdminSummaryCards,
} from "@/admin/components/console-page";
import { useRouter } from "@/routes/hooks";
import type { InfraServiceSettingsPayload, InfraServiceTestResult } from "@/types/infra";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { toast } from "sonner";

type ServiceKey = "addax" | "airflow" | "openmetadata" | "dbt" | "platform";

interface ServicePanelProps {
	service: ServiceKey;
	title: string;
	description?: string;
	restartHint?: string;
	capability: string;
	formContent: (form: FormInstance<Record<string, any>>) => ReactNode;
}

const SERVICE_LABELS: Record<ServiceKey, string> = {
	platform: "平台",
	addax: "Addax",
	airflow: "Airflow",
	openmetadata: "OpenMetadata",
	dbt: "DBT",
};

const SERVICE_CAPABILITIES: Record<ServiceKey, string> = {
	platform: "元数据联动",
	addax: "离线作业生成",
	airflow: "任务调度编排",
	openmetadata: "血缘与元数据采集",
	dbt: "建模与转换参数",
};

const QUICK_SWITCHES: Array<{ key: ServiceKey; label: string }> = [
	{ key: "platform", label: "平台联动" },
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

function ServicePanel({ service, title, description, restartHint, capability, formContent }: ServicePanelProps) {
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
			toast.success("配置已保存");
			if (restartHint) {
				toast.info(restartHint);
			}
			queryClient.invalidateQueries({ queryKey: ["admin", "infra-settings", service] });
		} catch (error: any) {
			if (error?.errorFields) return;
			toast.error(error?.message || "保存失败");
		}
	};

	const handleTest = async () => {
		try {
			const values = await form.validateFields();
			const payload = buildSettingsPayload(values);
			const result = await adminApi.testIntegrationSettings(service, payload);
			setTestResult(result || null);
			if (result?.success) {
				toast.success(result?.message || "连接成功");
			} else {
				toast.error(result?.message || "连接失败");
			}
		} catch (error: any) {
			if (error?.errorFields) return;
			setTestResult({ success: false, message: error?.message || "测试失败" });
			toast.error(error?.message || "测试失败");
		}
	};

	return (
		<div data-testid={`admin-infra-service-panel-${service}`}>
			<AdminSectionCard
				title={title}
				description={description}
				action={
					<div className="flex flex-wrap items-center gap-2">
						<Badge variant={enabled ? "success" : "outline"} className="rounded-full px-2.5 py-1">
							{enabled ? "已启用" : "未启用"}
						</Badge>
						<Badge variant="info" className="rounded-full px-2.5 py-1">
							{capability}
						</Badge>
					</div>
				}
				bodyClassName="space-y-5"
			>
				<div className="flex flex-wrap gap-2">
					<AdminMetaPill>服务标识：{SERVICE_LABELS[service]}</AdminMetaPill>
					<AdminMetaPill>配置即时保存</AdminMetaPill>
					{restartHint ? <AdminMetaPill>涉及服务重启提示</AdminMetaPill> : null}
				</div>

				<Form form={form} layout="vertical" requiredMark disabled={isFetching} className="max-w-4xl">
					{restartHint ? <Alert className="mb-4" type="warning" showIcon message={restartHint} /> : null}
					<div className="grid gap-x-5 xl:grid-cols-2">{formContent(form)}</div>
					<Space wrap className="pt-2">
						<AntButton data-testid={`admin-infra-service-save-${service}`} type="primary" onClick={handleSave}>
							保存配置
						</AntButton>
						<AntButton data-testid={`admin-infra-service-test-${service}`} onClick={handleTest}>
							测试连接
						</AntButton>
					</Space>
					{testResult ? (
						<div data-testid={`admin-infra-service-test-result-${service}`}>
							<Alert
								className="mt-4"
								type={testResult.success ? "success" : "error"}
								showIcon
								message={testResult.message || (testResult.success ? "测试成功" : "测试失败")}
								description={
									(testResult.status || testResult.body) ? (
										<div className="space-y-1">
											{testResult.status ? <div>状态码: {testResult.status}</div> : null}
											{testResult.body ? <pre className="whitespace-pre-wrap text-xs">{testResult.body}</pre> : null}
										</div>
									) : undefined
								}
							/>
						</div>
					) : null}
				</Form>
			</AdminSectionCard>
		</div>
	);
}

export default function InfraSettingsView() {
	const { push } = useRouter();
	const [activeService, setActiveService] = useState<ServiceKey>("platform");

	const summaryItems = useMemo(
		() => [
			{
				label: "集成面板",
				value: String(QUICK_SWITCHES.length),
				note: "平台、调度、采集与建模统一维护",
				icon: <Server className="h-5 w-5" />,
			},
			{
				label: "调度链路",
				value: "3",
				note: "Addax、Airflow、DBT",
				icon: <Workflow className="h-5 w-5" />,
				tone: "info" as const,
			},
			{
				label: "元数据联动",
				value: "2",
				note: "平台同步与 OpenMetadata 采集",
				icon: <Database className="h-5 w-5" />,
				tone: "success" as const,
			},
			{
				label: "变更提示",
				value: "4",
				note: "涉及重启的链路需提前安排窗口",
				icon: <Shield className="h-5 w-5" />,
				tone: "warning" as const,
			},
		],
		[],
	);

	const items = [
		{
			key: "platform",
			label: "平台",
			children: (
				<ServicePanel
					service="platform"
					title="平台侧联动"
					description="控制数据源创建时是否自动同步字段到元数据目录，优先保证资产接入后的目录完整性。"
					restartHint="该配置通常可热生效；如无效请重启 dts-platform。"
					capability={SERVICE_CAPABILITIES.platform}
					formContent={() => (
						<>
							<Form.Item
								label="数据源自动同步字段"
								name="catalogSyncOnDataSource"
								valuePropName="checked"
								className="xl:col-span-2"
							>
								<Switch />
							</Form.Item>
							<div className="xl:col-span-2 rounded-2xl border border-border/70 bg-muted/40 p-4 text-sm leading-6 text-muted-foreground">
								开启后，新增或更新数据源会自动同步元数据。JDBC 类数据源走元数据抓取，文件类数据源走字段解析，并自动生成 ODS 草稿。
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
					title="Addax 作业"
					description="维护作业目录与镜像，确保入湖任务与 Airflow DAG 路径保持一致。"
					restartHint="保存后建议重建入湖任务；镜像变更需重启调度相关服务。"
					capability={SERVICE_CAPABILITIES.addax}
					formContent={() => (
						<>
							<Form.Item label="启用" name="enabled" valuePropName="checked">
								<Switch />
							</Form.Item>
							<Form.Item
								label="作业目录"
								name="jobDir"
								rules={[{ required: true, message: "请输入作业目录" }]}
							>
								<Input placeholder="/opt/airflow/dags" />
							</Form.Item>
							<Form.Item
								label="镜像"
								name="image"
								rules={[{ required: true, message: "请输入镜像" }]}
								className="xl:col-span-2"
							>
								<Input placeholder="quay.io/wgzhao/addax:6.0.8" />
							</Form.Item>
							<div className="xl:col-span-2 rounded-2xl border border-border/70 bg-muted/40 p-4 text-sm leading-6 text-muted-foreground">
								Addax 作业目录需与 Airflow DAG 目录一致。写入器通用模板请在数据湖管理中维护，任务侧只填写表名与差异化参数。
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
					title="Airflow 调度"
					description="统一维护 DAG 地址、认证信息与默认调度入口。"
					restartHint="保存后建议重启 dts-ingestion 与 dts-platform，使连接参数一致。"
					capability={SERVICE_CAPABILITIES.airflow}
					formContent={() => (
						<>
							<Form.Item label="启用" name="enabled" valuePropName="checked">
								<Switch />
							</Form.Item>
							<Form.Item
								label="Airflow 地址"
								name="baseUrl"
								rules={[{ required: true, message: "请输入 Airflow 地址" }]}
							>
								<Input placeholder="http://dts-airflow-web:8080" />
							</Form.Item>
							<Form.Item label="API Path" name="apiPath">
								<Input placeholder="/api/v1" />
							</Form.Item>
							<Form.Item
								label="DAG 目录"
								name="dagsDir"
								rules={[{ required: true, message: "请输入 DAG 目录" }]}
							>
								<Input placeholder="/opt/airflow/dags" />
							</Form.Item>
							<Form.Item
								label="用户名"
								name="username"
								rules={[{ required: true, message: "请输入用户名" }]}
							>
								<Input placeholder="airflow" />
							</Form.Item>
							<Form.Item
								label="密码"
								name="password"
								rules={[{ required: true, message: "请输入密码" }]}
							>
								<Input.Password placeholder="******" />
							</Form.Item>
							<Form.Item label="默认 DAG ID" name="dagId" className="xl:col-span-2">
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
					title="OpenMetadata 元数据"
					description="维护源/目标服务标识和采集计划，用于血缘与元数据同步。"
					restartHint="保存后建议重启 dts-ingestion，确保采集器读取最新配置。"
					capability={SERVICE_CAPABILITIES.openmetadata}
					formContent={() => (
						<>
							<Form.Item label="启用" name="enabled" valuePropName="checked">
								<Switch />
							</Form.Item>
							<Form.Item
								label="OpenMetadata 地址"
								name="baseUrl"
								rules={[{ required: true, message: "请输入 OpenMetadata 地址" }]}
							>
								<Input placeholder="http://openmetadata:8585" />
							</Form.Item>
							<Form.Item label="API Path" name="apiPath">
								<Input placeholder="/api/v1" />
							</Form.Item>
							<Form.Item
								label="Token"
								name="authToken"
								rules={[{ required: true, message: "请输入 Token" }]}
							>
								<Input.Password placeholder="Bearer ..." />
							</Form.Item>
							<Form.Item label="表字段" name="tableFields" className="xl:col-span-2">
								<Input placeholder="columns,owner,tags,domain" />
							</Form.Item>
							<Divider orientation="left" className="xl:col-span-2">
								服务标识
							</Divider>
							<Form.Item
								label="源服务名称"
								name="sourceServiceName"
								rules={[{ required: true, message: "请输入源服务名称" }]}
							>
								<Input placeholder="source_service" />
							</Form.Item>
							<Form.Item
								label="源服务类型"
								name="sourceServiceType"
								rules={[{ required: true, message: "请输入源服务类型" }]}
							>
								<Input placeholder="Postgres" />
							</Form.Item>
							<Form.Item
								label="目标服务名称"
								name="destinationServiceName"
								rules={[{ required: true, message: "请输入目标服务名称" }]}
							>
								<Input placeholder="destination_service" />
							</Form.Item>
							<Form.Item
								label="目标服务类型"
								name="destinationServiceType"
								rules={[{ required: true, message: "请输入目标服务类型" }]}
							>
								<Input placeholder="Postgres" />
							</Form.Item>
							<Form.Item
								label="源数据库"
								name="sourceDatabase"
								rules={[{ required: true, message: "请输入源数据库" }]}
							>
								<Input placeholder="source_db" />
							</Form.Item>
							<Form.Item
								label="源 Schema"
								name="sourceSchema"
								rules={[{ required: true, message: "请输入源 Schema" }]}
							>
								<Input placeholder="public" />
							</Form.Item>
							<Form.Item
								label="目标数据库"
								name="destinationDatabase"
								rules={[{ required: true, message: "请输入目标数据库" }]}
							>
								<Input placeholder="ods" />
							</Form.Item>
							<Form.Item
								label="目标 Schema"
								name="destinationSchema"
								rules={[{ required: true, message: "请输入目标 Schema" }]}
							>
								<Input placeholder="public" />
							</Form.Item>
							<Divider orientation="left" className="xl:col-span-2">
								采集设置
							</Divider>
							<Form.Item label="启用采集" name="ingestionEnabled" valuePropName="checked">
								<Switch />
							</Form.Item>
							<Form.Item label="采集任务前缀" name="ingestionPrefix">
								<Input placeholder="dts_ingest" />
							</Form.Item>
							<Form.Item label="采集调度" name="ingestionSchedule" className="xl:col-span-2">
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
					title="DBT 任务"
					description="登记 DBT 服务地址、鉴权与后续编排接入参数，先保证连接口径统一。"
					restartHint="启用前请同步完成 dts-ingestion 与 dts-platform 的 DBT 接入联调。"
					capability={SERVICE_CAPABILITIES.dbt}
					formContent={() => (
						<>
							<div className="xl:col-span-2">
								<Alert
									type="info"
									showIcon
									message="当前基线先管理连接参数"
									description="任务发布和编排仍走平台侧链路，待现场确认接入顺序后再开放自动执行入口。"
								/>
							</div>
							<Form.Item label="启用" name="enabled" valuePropName="checked">
								<Switch />
							</Form.Item>
							<Form.Item label="DBT 地址" name="baseUrl">
								<Input placeholder="http://dbt-service:8080" />
							</Form.Item>
							<Form.Item label="API Path" name="apiPath">
								<Input placeholder="/api/v1" />
							</Form.Item>
							<Form.Item label="用户名" name="username">
								<Input />
							</Form.Item>
							<Form.Item label="密码" name="password">
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
		<div className="space-y-6" data-testid="admin-infra-settings-page">
			<AdminPageHeader
				title="集成设置"
				description="把平台联动、调度编排、元数据采集和 DBT 接入参数收敛到同一控制面，避免现场在多个页面来回切换。"
				eyebrow="System Integrations"
				actions={
					<>
						<Button variant="outline" onClick={() => push("/admin/data-lake")}>
							<Database className="h-4 w-4" />
							数据湖配置
						</Button>
						<Button variant="outline" onClick={() => push("/admin/ops")}>
							<RefreshCw className="h-4 w-4" />
							运维配置
						</Button>
					</>
				}
				meta={
					<>
						<AdminMetaPill>统一维护 5 条集成链路</AdminMetaPill>
						<AdminMetaPill>现场优先关注 Addax / Airflow / OpenMetadata</AdminMetaPill>
						<AdminMetaPill>DBT 先收口连接参数</AdminMetaPill>
					</>
				}
			/>

			<AdminSummaryCards items={summaryItems} />

			<AdminFilterBar>
				<div>
					<div className="text-sm font-semibold text-foreground">按服务切换配置面板</div>
					<div className="mt-1 text-sm text-muted-foreground">
						保持“集成服务一页一职责”的结构，不再把不同链路的配置混在一个长表单里。
					</div>
				</div>
				<div className="flex flex-wrap items-center gap-2">
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
			</AdminFilterBar>

			<AdminSectionCard
				title="集成服务配置"
				description="每个服务单独维护连接与运行参数，保存与联测动作就地完成。"
				action={
					<div className="flex flex-wrap items-center gap-2">
						<Badge variant="outline" className="rounded-full px-2.5 py-1">
							当前面板：{SERVICE_LABELS[activeService]}
						</Badge>
						<Badge variant="info" className="rounded-full px-2.5 py-1">
							{SERVICE_CAPABILITIES[activeService]}
						</Badge>
					</div>
				}
				bodyClassName="pt-0"
			>
				<div data-testid="admin-infra-settings-tabs">
					<Tabs activeKey={activeService} onChange={(key) => setActiveService(key as ServiceKey)} items={items} />
				</div>
			</AdminSectionCard>

			<div className="grid gap-6 xl:grid-cols-[1.2fr_0.8fr]">
				<AdminSectionCard
					title="联动建议"
					description="现场切换或新接服务时，优先检查下面三条主链路。"
				>
					<div className="grid gap-3">
						{[
							"Addax 作业目录与 Airflow DAG 目录必须保持一致。",
							"OpenMetadata 的源/目标服务命名应与平台真实资产命名保持同口径。",
							"DBT 若只保存连接参数，先不要在业务页暴露自动执行入口。",
						].map((item) => (
							<div key={item} className="rounded-[22px] border border-border/70 bg-muted/35 px-4 py-3 text-sm text-muted-foreground">
								{item}
							</div>
						))}
					</div>
				</AdminSectionCard>

				<AdminSectionCard
					title="关联入口"
					description="集成配置之外，常用的上下游入口保持就近跳转。"
				>
					<div className="grid gap-3">
						{[
							{ title: "数据湖配置", description: "维护 JDBC 驱动、默认数据湖和写入模板。", path: "/admin/data-lake", icon: <Database className="h-4 w-4" /> },
							{ title: "运维配置", description: "查看运行期参数、功能开关和重启影响面。", path: "/admin/ops", icon: <Shield className="h-4 w-4" /> },
							{ title: "工作流配置", description: "审批模板与链路策略在这里统一维护。", path: "/admin/workflows", icon: <Workflow className="h-4 w-4" /> },
							{ title: "门户菜单", description: "对外入口和链接暴露由菜单治理页面负责。", path: "/admin/portal-menus", icon: <Plug className="h-4 w-4" /> },
						].map((item) => (
							<button
								key={item.path}
								type="button"
								onClick={() => push(item.path)}
								className="flex items-center justify-between rounded-[22px] border border-border/70 bg-muted/35 px-4 py-4 text-left transition hover:bg-muted/55"
							>
								<div className="space-y-1">
									<div className="flex items-center gap-2 text-sm font-semibold text-foreground">
										{item.icon}
										{item.title}
									</div>
									<div className="text-sm text-muted-foreground">{item.description}</div>
								</div>
								<Server className="h-4 w-4 text-muted-foreground" />
							</button>
						))}
					</div>
				</AdminSectionCard>
			</div>
		</div>
	);
}
