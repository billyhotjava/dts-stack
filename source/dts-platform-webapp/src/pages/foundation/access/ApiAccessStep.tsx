import { Alert, Button, Form, Input, InputNumber, Select, Space, Typography } from "antd";
import type { FormInstance } from "antd/es/form";
import type { ApiConnectionTestResultDTO } from "@/api/ingestion";
import type { InfraDataSource } from "@/api/services/dataSourcesService";
import type { AccessPlanFormValues } from "./accessPlan.types";

type Props = {
	form: FormInstance<AccessPlanFormValues>;
	phase?: "source" | "resource";
	dataSources: InfraDataSource[];
	preview: ApiConnectionTestResultDTO | null;
	previewing: boolean;
	onPreview: () => void;
	onPreviewInputChange: () => void;
};

export function ApiAccessStep({
	form: _form,
	phase = "resource",
	dataSources,
	preview,
	previewing,
	onPreview,
	onPreviewInputChange,
}: Props) {
	if (phase === "source") {
		return (
			<div className="space-y-5">
				<div>
					<Typography.Title level={4}>选择 API 连接</Typography.Title>
					<Typography.Text type="secondary">认证信息由平台连接管理托管，任务只引用连接。</Typography.Text>
				</div>
				<div className="grid gap-4 md:grid-cols-2">
					<Form.Item name="name" label="任务名称" rules={[{ required: true, message: "请输入任务名称" }]}>
						<Input placeholder="例如：CRM 订单 API" />
					</Form.Item>
					<Form.Item
						name="sourceDataSourceId"
						label="API 连接"
						rules={[{ required: true, message: "请选择 API 连接" }]}
					>
						<Select
							showSearch
							optionFilterProp="label"
							onChange={onPreviewInputChange}
							options={dataSources.map((item) => ({ label: `${item.name} · ${item.type}`, value: item.id }))}
						/>
					</Form.Item>
				</div>
				<Form.Item name="description" label="用途说明">
					<Input.TextArea rows={2} />
				</Form.Item>
			</div>
		);
	}

	return (
		<div className="space-y-5">
			<div>
				<Typography.Title level={4}>定义 API 资源</Typography.Title>
				<Typography.Text type="secondary">预览只请求小样本，用于验证鉴权和记录路径，不做全量扫描。</Typography.Text>
			</div>
			<div className="grid gap-4 md:grid-cols-3">
				<Form.Item name="sourceSystem" label="来源系统标识">
					<Input placeholder="CRM" />
				</Form.Item>
				<Form.Item name="apiResourceId" label="资源标识">
					<Input placeholder="orders" />
				</Form.Item>
				<Form.Item name="apiResourceDisplayName" label="显示名称">
					<Input placeholder="订单" />
				</Form.Item>
			</div>
			<div className="grid gap-4 md:grid-cols-[140px_1fr]">
				<Form.Item name="apiMethod" label="请求方法" rules={[{ required: true }]}>
					<Select
						onChange={onPreviewInputChange}
						options={["GET", "POST", "PUT", "PATCH"].map((value) => ({ label: value, value }))}
					/>
				</Form.Item>
				<Form.Item name="apiResourcePath" label="资源路径" rules={[{ required: true, message: "请输入资源路径" }]}>
					<Input placeholder="/v1/orders" onChange={onPreviewInputChange} />
				</Form.Item>
			</div>
			<Form.Item name="apiRecordPath" label="记录路径">
				<Input placeholder="例如：data.items；响应本身为数组时留空" onChange={onPreviewInputChange} />
			</Form.Item>
			<div>
				<Typography.Text strong>分页参数</Typography.Text>
				<div className="mt-3 grid gap-4 md:grid-cols-3">
					<Form.Item name="apiPageParam" label="页码参数">
						<Input placeholder="page" onChange={onPreviewInputChange} />
					</Form.Item>
					<Form.Item name="apiSizeParam" label="每页数量参数">
						<Input placeholder="pageSize" onChange={onPreviewInputChange} />
					</Form.Item>
					<Form.Item name="apiPageSize" label="每页数量">
						<InputNumber min={1} max={10000} className="w-full" onChange={onPreviewInputChange} />
					</Form.Item>
				</div>
			</div>
			<div>
				<Typography.Text strong>增量游标</Typography.Text>
				<div className="mt-3 grid gap-4 md:grid-cols-2">
					<Form.Item name="apiCursorField" label="响应游标字段">
						<Input placeholder="updatedAt" onChange={onPreviewInputChange} />
					</Form.Item>
					<Form.Item name="apiCursorParam" label="请求参数名">
						<Input placeholder="updatedAfter" onChange={onPreviewInputChange} />
					</Form.Item>
				</div>
			</div>
			<Space wrap>
				<Button type="primary" ghost loading={previewing} onClick={onPreview}>
					测试并预览小样本
				</Button>
				<Typography.Text type="secondary">最多返回服务端限定的少量样本。</Typography.Text>
			</Space>
			{preview ? (
				<Alert
					showIcon
					type={preview.connected ? "success" : "warning"}
					message={preview.connected ? `连接成功 · 样本 ${preview.sampleCount || 0} 条` : "连接未通过"}
					description={preview.connected ? "已完成受限小样本验证。" : "请检查托管连接与资源定义后重试。"}
				/>
			) : null}
		</div>
	);
}
