import { useCallback, useEffect, useState } from "react";
import { Card, Table, Tabs, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { listMyGrants, listGrantedByMe } from "@/api/platformApi";

type AssetGrant = {
	id: number;
	assetType: string;
	assetId: string;
	granteeType: string;
	granteeId: string;
	permission: string;
	validFrom?: string;
	validTo?: string;
	grantedBy: string;
	grantReason?: string;
	createdDate?: string;
};

export default function MyGrantsPage() {
	const [activeTab, setActiveTab] = useState("received");
	const [receivedGrants, setReceivedGrants] = useState<AssetGrant[]>([]);
	const [grantedGrants, setGrantedGrants] = useState<AssetGrant[]>([]);
	const [loading, setLoading] = useState(false);
	const [page, setPage] = useState(0);
	const [total, setTotal] = useState(0);

	const loadData = useCallback(async () => {
		setLoading(true);
		try {
			const api = activeTab === "received" ? listMyGrants : listGrantedByMe;
			const resp = await api({ page, size: 20 }) as any;
			const content = resp?.content ?? resp?.data ?? resp ?? [];
			const items = Array.isArray(content) ? content : [];
			if (activeTab === "received") {
				setReceivedGrants(items);
			} else {
				setGrantedGrants(items);
			}
			setTotal(resp?.totalElements ?? items.length);
		} catch {
			/* global interceptor handles toast */
		} finally {
			setLoading(false);
		}
	}, [activeTab, page]);

	useEffect(() => { loadData(); }, [loadData]);

	const isExpiringSoon = (validTo?: string) => {
		if (!validTo) return false;
		const diff = new Date(validTo).getTime() - Date.now();
		return diff > 0 && diff < 7 * 24 * 60 * 60 * 1000;
	};

	const receivedColumns: ColumnsType<AssetGrant> = [
		{ title: "资产", key: "asset", render: (_: any, r: AssetGrant) => `${r.assetType}:${r.assetId}` },
		{ title: "权限", dataIndex: "permission", width: 100,
			render: (v: string) => <Tag color={v === "MANAGE" ? "red" : v === "EDIT" ? "orange" : "default"}>{v}</Tag> },
		{ title: "有效期至", dataIndex: "validTo", width: 160,
			render: (v?: string) => {
				if (!v) return <Tag color="green">永久</Tag>;
				const expiring = isExpiringSoon(v);
				return <Tag color={expiring ? "red" : "default"}>{new Date(v).toLocaleDateString()}</Tag>;
			}},
		{ title: "授权者", dataIndex: "grantedBy", width: 120 },
		{ title: "原因", dataIndex: "grantReason", ellipsis: true },
	];

	const grantedColumns: ColumnsType<AssetGrant> = [
		{ title: "资产", key: "asset", render: (_: any, r: AssetGrant) => `${r.assetType}:${r.assetId}` },
		{ title: "被授权人", key: "grantee", render: (_: any, r: AssetGrant) => `${r.granteeType}:${r.granteeId}` },
		{ title: "权限", dataIndex: "permission", width: 100,
			render: (v: string) => <Tag color={v === "MANAGE" ? "red" : v === "EDIT" ? "orange" : "default"}>{v}</Tag> },
		{ title: "有效期至", dataIndex: "validTo", width: 160,
			render: (v?: string) => v ? new Date(v).toLocaleDateString() : "永久" },
		{ title: "原因", dataIndex: "grantReason", ellipsis: true },
	];

	return (
		<Card title="我的授权">
			<Tabs activeKey={activeTab} onChange={(key) => { setActiveTab(key); setPage(0); }}
				items={[
					{ key: "received", label: "已接收" },
					{ key: "granted", label: "我授予的" },
				]}
			/>
			<Table
				rowKey="id"
				columns={activeTab === "received" ? receivedColumns : grantedColumns}
				dataSource={activeTab === "received" ? receivedGrants : grantedGrants}
				loading={loading}
				size="small"
				pagination={{
					current: page + 1, pageSize: 20, total,
					onChange: (p) => setPage(p - 1),
				}}
			/>
		</Card>
	);
}
