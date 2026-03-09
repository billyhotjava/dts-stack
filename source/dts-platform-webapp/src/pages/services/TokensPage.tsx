import { useEffect, useState } from "react";
import { toast } from "sonner";
import { Button, Card, Modal, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { PlusOutlined, DeleteOutlined } from "@ant-design/icons";
import { PageHeader } from "@/components/page-header";
import { createToken, deleteToken, listMyTokens } from "@/api/platformApi";

const { Text } = Typography;

type TokenInfo = {
	id: string;
	tokenHint?: string;
	expiresAt?: string;
	revoked?: boolean;
	createdAt?: string;
};

const formatDate = (value?: string) => {
	if (!value) return "-";
	const date = new Date(value);
	return Number.isNaN(date.valueOf()) ? value : date.toLocaleString();
};

export default function Page() {
	const [tokens, setTokens] = useState<TokenInfo[]>([]);
	const [loading, setLoading] = useState(false);
	const [tokenModal, setTokenModal] = useState<{ open: boolean; token?: string }>({ open: false });

	const loadTokens = async () => {
		setLoading(true);
		try {
			const list = await listMyTokens();
			setTokens(Array.isArray(list) ? (list as TokenInfo[]) : []);
		} catch (error: any) {
			toast.error(error?.message || "令牌加载失败");
		} finally {
			setLoading(false);
		}
	};

	useEffect(() => {
		void loadTokens();
	}, []);

	const handleCreate = async () => {
		try {
			const resp: any = await createToken();
			setTokenModal({ open: true, token: resp?.token });
			toast.success("令牌已创建");
			await loadTokens();
		} catch (error: any) {
			toast.error(error?.message || "创建失败");
		}
	};

	const handleDelete = async (id?: string) => {
		if (!id) return;
		try {
			await deleteToken(id);
			toast.success("令牌已吊销");
			await loadTokens();
		} catch (error: any) {
			toast.error(error?.message || "吊销失败");
		}
	};

	const columns: ColumnsType<TokenInfo> = [
		{ title: "令牌提示", dataIndex: "tokenHint", render: (v) => v || "-" },
		{ title: "创建时间", dataIndex: "createdAt", render: (v) => formatDate(v) },
		{ title: "过期时间", dataIndex: "expiresAt", render: (v) => formatDate(v) },
		{ title: "状态", dataIndex: "revoked", render: (v) => <Tag color={v ? "default" : "green"}>{v ? "已吊销" : "有效"}</Tag> },
		{
			title: "操作",
			width: 140,
			render: (_, record) => (
				<Button size="small" danger icon={<DeleteOutlined />} onClick={() => handleDelete(record.id)}>
					吊销
				</Button>
			),
		},
	];

	return (
		<div className="space-y-6">
			<PageHeader
				title="数据服务中心 / 共享交换"
				actions={
					<Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
						生成令牌
					</Button>
				}
			/>
			<Card>
				<Table rowKey={(record) => record.id} columns={columns} dataSource={tokens} loading={loading} />
			</Card>

			<Modal
				open={tokenModal.open}
				onCancel={() => setTokenModal({ open: false })}
				footer={null}
				title="新令牌"
				destroyOnClose
			>
				<Text>请妥善保存该令牌，关闭后无法再次查看：</Text>
				<pre className="mt-3 whitespace-pre-wrap rounded bg-muted p-3 text-xs">{tokenModal.token || "-"}</pre>
			</Modal>
		</div>
	);
}
