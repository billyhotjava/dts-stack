import { useEffect, useState } from "react";
import { toast } from "sonner";
import { Alert, Button, Card, Modal, Space, Tag, Typography } from "antd";
import { CompactTable } from "@/components/table";
import type { ColumnsType } from "antd/es/table";
import { } from "@ant-design/icons";
import { PageHeader } from "@/components/page-header";
import { createToken, deleteToken, listMyTokens } from "@/api/platformApi";

const { Text } = Typography;

type TokenInfo = {
	id: string;
	tokenHint?: string;
	scope?: string;
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
		} catch {
			// global interceptor handles the error toast
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
		} catch {
			// global interceptor handles the error toast
		}
	};

	const handleDelete = async (id?: string) => {
		if (!id) return;
		try {
			await deleteToken(id);
			toast.success("令牌已撤销");
			await loadTokens();
		} catch {
			// global interceptor handles the error toast
		}
	};

	const copyToken = async () => {
		if (!tokenModal.token) return;
		try {
			await navigator.clipboard.writeText(tokenModal.token);
			toast.success("令牌已复制");
		} catch {
			toast.error("复制失败，请手动复制令牌");
		}
	};

	const columns: ColumnsType<TokenInfo> = [
		{ title: "令牌提示", dataIndex: "tokenHint", render: (v) => v || "-" },
		{ title: "作用域", dataIndex: "scope", width: 140, render: (v) => v || "当前用户" },
		{ title: "创建时间", dataIndex: "createdAt", render: (v) => formatDate(v) , sorter: (a, b) => { const ta = a.createdAt ? new Date(a.createdAt as any).getTime() : 0; const tb = b.createdAt ? new Date(b.createdAt as any).getTime() : 0; return ta - tb; } },
		{ title: "有效期", dataIndex: "expiresAt", render: (v) => formatDate(v) , sorter: (a, b) => { const ta = a.expiresAt ? new Date(a.expiresAt as any).getTime() : 0; const tb = b.expiresAt ? new Date(b.expiresAt as any).getTime() : 0; return ta - tb; } },
		{ title: "状态", dataIndex: "revoked", render: (v) => <Tag color={v ? "default" : "green"}>{v ? "已撤销" : "有效"}</Tag> },
		{
			title: "操作",
			width: 180,
			render: (_, record) => (
				<Space>
					<Button size="small" disabled title="审计流水接口尚未接入，先通过令牌创建时间和状态追溯">
						查看审计
					</Button>
					<Button size="small" danger onClick={() => handleDelete(record.id)}>
						撤销
					</Button>
				</Space>
			),
		},
	];

	return (
		<div className="space-y-6">
			<PageHeader
				title="数据服务中心 / 共享交换"
				actions={
					<Button type="primary" onClick={handleCreate}>
						生成令牌
					</Button>
				}
			/>
			<Card>
				<Alert
					className="mb-4"
					type="warning"
					showIcon
					message="令牌按作用域和有效期进行安全共享；新令牌只展示一次，请生成后立即复制并妥善保存。"
				/>
				<CompactTable rowKey={(record) => record.id} columns={columns} dataSource={tokens} loading={loading} />
			</Card>

			<Modal
				open={tokenModal.open}
				onCancel={() => setTokenModal({ open: false })}
				footer={null}
				title="新令牌"
				destroyOnClose
			>
				<Text>该令牌只展示一次，关闭后无法再次查看：</Text>
				<pre className="mt-3 whitespace-pre-wrap rounded bg-muted p-3 text-xs">{tokenModal.token || "-"}</pre>
				<Button className="mt-3" type="primary" onClick={copyToken}>
					复制令牌
				</Button>
			</Modal>
		</div>
	);
}
