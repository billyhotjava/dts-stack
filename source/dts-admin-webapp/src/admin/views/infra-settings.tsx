import { useEffect, type ReactNode } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button as AntButton, Divider, Form, Input, Space, Switch, Tabs } from "antd";
import type { FormInstance } from "antd";
import { adminApi } from "@/admin/api/adminApi";
import type { InfraServiceSettingsPayload } from "@/types/infra";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Text } from "@/ui/typography";
import { toast } from "sonner";

type ServiceKey = "addax" | "airflow" | "openmetadata" | "dbt" | "platform";

interface ServicePanelProps {
	service: ServiceKey;
	title: string;
	description?: string;
	formContent: (form: FormInstance<Record<string, any>>) => ReactNode;
}

const normalizeSettings = (data?: InfraServiceSettingsPayload | null) => {
	const settings = data?.settings || {};
	return { enabled: true, catalogSyncOnDataSource: true, ...settings };
};

const buildSettingsPayload = (
	values: Record<string, any>,
) => {
	return { ...values };
};

function ServicePanel({ service, title, description, formContent }: ServicePanelProps) {
	const queryClient = useQueryClient();
	const [form] = Form.useForm<Record<string, any>>();

	const { data, isFetching } = useQuery({
		queryKey: ["admin", "infra-settings", service],
		queryFn: () => adminApi.getIntegrationSettings(service),
	});

	useEffect(() => {
		form.setFieldsValue(normalizeSettings(data));
	}, [data, form]);

	const handleSave = async () => {
		try {
			const values = await form.validateFields();
			const payload = buildSettingsPayload(values);
			await adminApi.updateIntegrationSettings(service, payload);
			toast.success("配置已保存");
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
			if (result?.success) {
				toast.success(result?.message || "连接成功");
			} else {
				toast.error(result?.message || "连接失败");
			}
		} catch (error: any) {
			if (error?.errorFields) return;
			toast.error(error?.message || "测试失败");
		}
	};

	return (
		<Card>
			<CardHeader>
				<CardTitle>{title}</CardTitle>
				{description ? <Text variant="body3" className="text-muted-foreground">{description}</Text> : null}
			</CardHeader>
			<CardContent>
				<Form form={form} layout="vertical" requiredMark disabled={isFetching} className="max-w-3xl">
					{formContent(form)}
					<Space wrap>
						<AntButton type="primary" onClick={handleSave}>
							保存配置
						</AntButton>
						<AntButton onClick={handleTest}>测试连接</AntButton>
					</Space>
				</Form>
			</CardContent>
		</Card>
	);
}

export default function InfraSettingsView() {
	const items = [
		{
			key: "platform",
			label: "平台",
			children: (
				<ServicePanel
					service="platform"
					title="平台侧联动"
					description="控制数据源创建时是否同步字段到元数据目录。"
					formContent={() => (
						<>
							<Form.Item
								label="数据源自动同步字段"
								name="catalogSyncOnDataSource"
								valuePropName="checked"
							>
								<Switch />
							</Form.Item>
							<Text variant="body3" className="text-muted-foreground">
								开启后，新增/更新非 JDBC 数据源会自动将字段同步到元数据目录（ODS 层，草稿状态）。
							</Text>
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
					description="用于生成 Addax 作业文件并配合 Airflow 执行。"
					formContent={(form) => (
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
							>
								<Input placeholder="quay.io/wgzhao/addax:6.0.8" />
							</Form.Item>
							<Text variant="body3" className="text-muted-foreground">
								Addax 作业目录需与 Airflow DAG 目录保持一致。
							</Text>
							<Text variant="body3" className="text-muted-foreground">
								写入器通用模板请在数据湖管理（默认数据湖）中维护，入湖任务仅填写表名等覆盖参数。
							</Text>
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
					description="用于触发 DAG 或对接调度任务。"
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
							<Form.Item label="默认 DAG ID" name="dagId">
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
					description="用于血缘与元数据采集。"
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
							<Form.Item label="表字段" name="tableFields">
								<Input placeholder="columns,owner,tags,domain" />
							</Form.Item>
							<Divider orientation="left">服务标识</Divider>
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
							<Divider orientation="left">采集设置</Divider>
							<Form.Item label="启用采集" name="ingestionEnabled" valuePropName="checked">
								<Switch />
							</Form.Item>
							<Form.Item label="采集任务前缀" name="ingestionPrefix">
								<Input placeholder="dts_ingest" />
							</Form.Item>
							<Form.Item label="采集调度" name="ingestionSchedule">
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
					description="预留接入配置，当前未启用。"
					formContent={() => (
						<>
							<Alert type="info" message="DBT 接入能力预留中，当前仅保存配置。" showIcon />
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
		<div className="space-y-6">
			<Tabs items={items} />
		</div>
	);
}
