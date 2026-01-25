import { useMemo, useState } from "react";
import { Alert, Button, Card, Form, Input, Space, Switch, Typography } from "antd";
import { toast } from "sonner";
import { PageHeader } from "@/components/page-header";
import { createIngestionTask } from "@/api/platformApi";
import { useUserInfo } from "@/store/userStore";
import { useRouter } from "@/routes/hooks";

const { Text } = Typography;

const normalizeText = (value?: string) => String(value || "").trim();

const parseJson = (value?: string, label?: string) => {
	const text = normalizeText(value);
	if (!text) return undefined;
	try {
		return JSON.parse(text);
	} catch {
		throw new Error(`${label || "配置"} JSON 格式错误`);
	}
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

export default function TransformCreatePage() {
	const [saving, setSaving] = useState(false);
	const [form] = Form.useForm();
	const router = useRouter();
	const userInfo = useUserInfo() as any;
	const useDefaultDestination = Form.useWatch("useDefaultDestination", form);

	const initialValues = useMemo(
		() => ({
			useDefaultDestination: true,
			airflowEnabled: true,
			runNow: true,
		}),
		[]
	);

	const handleSubmit = async (values: any) => {
		try {
			setSaving(true);
			const readerConfig = parseJson(values.readerConfig, "Reader 配置");
			const writerConfig = values.useDefaultDestination
				? undefined
				: parseJson(values.writerConfig, "Writer 配置");
			const jobConfig = parseJson(values.jobConfig, "作业参数");
			const payload = {
				name: normalizeText(values.name),
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
					dagId: normalizeText(values.dagId) || undefined,
				},
				runNow: Boolean(values.runNow),
				jobConfig: jobConfig || undefined,
			};
			await createIngestionTask(payload);
			toast.success("入湖任务已提交");
			router.push("/explore/etl/transform/new");
		} catch (err: any) {
			toast.error(err?.message || "创建入湖任务失败");
		} finally {
			setSaving(false);
		}
	};

	return (
		<div className="flex flex-col gap-6">
			<PageHeader
				title="创建入湖任务"
				description="使用 Addax 生成作业配置，并由 Airflow 触发执行。"
				actions={
					<Space>
						<Button onClick={() => router.push("/explore/etl/transform/new")}>重置表单</Button>
						<Button type="primary" loading={saving} onClick={() => form.submit()}>
							提交任务
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
					<Form.Item
						name="name"
						label="任务名称"
						rules={[{ required: true, message: "请输入任务名称" }]}
					>
						<Input placeholder="例如：pg-lake-task1" />
					</Form.Item>
					<Form.Item
						name="readerType"
						label="Reader 类型"
						rules={[{ required: true, message: "请输入 Reader 类型" }]}
					>
						<Input placeholder="例如：postgresqlreader" />
					</Form.Item>
					<Form.Item
						name="readerConfig"
						label="Reader 配置 (JSON)"
						rules={[{ required: true, message: "请输入 Reader 配置" }, { validator: jsonValidator("Reader 配置") }]}
					>
						<Input.TextArea rows={6} placeholder='{"username":"xxx","password":"xxx","column":["*"]}' />
					</Form.Item>
					<Form.Item name="useDefaultDestination" label="使用平台默认数据湖" valuePropName="checked">
						<Switch />
					</Form.Item>
					{!useDefaultDestination && (
						<>
							<Form.Item
								name="writerType"
								label="Writer 类型"
								rules={[{ required: true, message: "请输入 Writer 类型" }]}
							>
								<Input placeholder="例如：postgresqlwriter" />
							</Form.Item>
							<Form.Item
								name="writerConfig"
								label="Writer 配置 (JSON)"
								rules={[{ required: true, message: "请输入 Writer 配置" }, { validator: jsonValidator("Writer 配置") }]}
							>
								<Input.TextArea rows={6} placeholder='{"username":"xxx","password":"xxx","column":["*"]}' />
							</Form.Item>
						</>
					)}
					<Form.Item name="jobConfig" label="作业参数 (JSON，可选)" rules={[{ validator: jsonValidator("作业参数") }]}
					>
						<Input.TextArea rows={4} placeholder='{"setting":{"speed":{"channel":3}}}' />
					</Form.Item>
					<Card type="inner" title="Airflow 触发">
						<Form.Item name="airflowEnabled" label="启用 Airflow" valuePropName="checked">
							<Switch />
						</Form.Item>
						<Form.Item name="dagId" label="DAG ID (可选)">
							<Input placeholder="默认读取系统设置中的 Addax DAG" />
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
