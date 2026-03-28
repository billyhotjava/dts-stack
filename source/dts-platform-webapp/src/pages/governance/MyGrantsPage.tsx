import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
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
		} catch (err: any) {
			toast.error(err?.message || "Load failed");
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
		{ title: "Asset", key: "asset", render: (_: any, r: AssetGrant) => `${r.assetType}:${r.assetId}` },
		{ title: "Permission", dataIndex: "permission", width: 100,
			render: (v: string) => <Tag color={v === "MANAGE" ? "red" : v === "EDIT" ? "orange" : "default"}>{v}</Tag> },
		{ title: "Valid To", dataIndex: "validTo", width: 160,
			render: (v?: string) => {
				if (!v) return <Tag color="green">Permanent</Tag>;
				const expiring = isExpiringSoon(v);
				return <Tag color={expiring ? "red" : "default"}>{new Date(v).toLocaleDateString()}</Tag>;
			}},
		{ title: "Granted By", dataIndex: "grantedBy", width: 120 },
		{ title: "Reason", dataIndex: "grantReason", ellipsis: true },
	];

	const grantedColumns: ColumnsType<AssetGrant> = [
		{ title: "Asset", key: "asset", render: (_: any, r: AssetGrant) => `${r.assetType}:${r.assetId}` },
		{ title: "Grantee", key: "grantee", render: (_: any, r: AssetGrant) => `${r.granteeType}:${r.granteeId}` },
		{ title: "Permission", dataIndex: "permission", width: 100,
			render: (v: string) => <Tag color={v === "MANAGE" ? "red" : v === "EDIT" ? "orange" : "default"}>{v}</Tag> },
		{ title: "Valid To", dataIndex: "validTo", width: 160,
			render: (v?: string) => v ? new Date(v).toLocaleDateString() : "Permanent" },
		{ title: "Reason", dataIndex: "grantReason", ellipsis: true },
	];

	return (
		<Card title="My Grants">
			<Tabs activeKey={activeTab} onChange={(key) => { setActiveTab(key); setPage(0); }}
				items={[
					{ key: "received", label: "Received" },
					{ key: "granted", label: "Granted by Me" },
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
