import { Alert, Modal, Space, Tag, Typography } from "antd";
import { CompactTable } from "@/components/table";
import type { SqlModelBatchDeleteDetail } from "../sqlModelBatchDeleteResult.helpers";

const { Text } = Typography;

type BatchDeleteResultModalProps = {
	open: boolean;
	onClose: () => void;
	result: SqlModelBatchDeleteDetail | null;
	title?: string;
};

export default function BatchDeleteResultModal({ open, onClose, result, title = "删除结果" }: BatchDeleteResultModalProps) {
	return (
		<Modal
			open={open}
			title={title}
			width={980}
			onCancel={onClose}
			onOk={onClose}
			okText="知道了"
			cancelButtonProps={{ style: { display: "none" } }}
		>
			<Space direction="vertical" size={16} style={{ width: "100%" }}>
				<Alert
					type={result?.failed || result?.skipped ? "warning" : "success"}
					showIcon
					message={`本次请求 ${result?.requested || 0} 个模型，成功 ${result?.deleted || 0} 个，失败 ${result?.failed || 0} 个，跳过 ${result?.skipped || 0} 个`}
				/>
				<CompactTable
					size="small"
					rowKey={(record) => record.modelId}
					pagination={{ pageSize: 8, hideOnSinglePage: true }}
					dataSource={result?.rows || []}
					columns={[
						{
							title: "模型",
							dataIndex: "name",
							render: (_, record) => (
								<div>
									<div className="font-medium">{record.name}</div>
									<div className="text-xs text-muted-foreground">{record.modelPath}</div>
								</div>
							),
						},
						{
							title: "项目空间",
							dataIndex: "planName",
							width: 140,
						},
						{
							title: "分层",
							dataIndex: "layer",
							width: 100,
							render: (value) => <Tag>{value}</Tag>,
						},
						{
							title: "结果",
							dataIndex: "status",
							width: 120,
							render: (value) =>
								value === "success" ? (
									<Tag color="green">处理成功</Tag>
								) : value === "skipped" ? (
									<Tag color="gold">已跳过</Tag>
								) : (
									<Tag color="red">处理失败</Tag>
								),
						},
						{
							title: "说明",
							dataIndex: "message",
							render: (value, record) => (
								<Text type={record.status === "failed" && value ? "danger" : "secondary"}>
									{value || "处理成功"}
								</Text>
							),
						},
					]}
				/>
			</Space>
		</Modal>
	);
}
