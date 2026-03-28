import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { Button, Card, Input, Modal, Select, Space, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { listAssetOwnership, updateAssetOwnership, batchUpdateAssetOwnership } from "@/api/platformApi";
import { getOrgTree } from "@/api/services/directoryService";

const ASSET_TYPE_OPTIONS = [
	{ label: "TABLE", value: "TABLE" },
	{ label: "CARD", value: "CARD" },
	{ label: "DASHBOARD", value: "DASHBOARD" },
	{ label: "SCREEN", value: "SCREEN" },
	{ label: "MODEL", value: "MODEL" },
];

type AssetOwnership = {
	id: number;
	assetType: string;
	assetId: string;
	ownerDeptCode: string;
	sourceId?: string;
	assignedBy?: string;
	createdDate?: string;
	lastModifiedDate?: string;
};

type OrgNode = { id: number; name: string; deptCode?: string; children?: OrgNode[] };

export default function AssetOwnershipPage() {
	const [items, setItems] = useState<AssetOwnership[]>([]);
	const [loading, setLoading] = useState(false);
	const [total, setTotal] = useState(0);
	const [page, setPage] = useState(0);
	const [size] = useState(20);
	const [assetType, setAssetType] = useState<string | undefined>();
	const [ownerDeptCode, setOwnerDeptCode] = useState<string | undefined>();
	const [keyword, setKeyword] = useState("");
	const [selectedRowKeys, setSelectedRowKeys] = useState<number[]>([]);
	const [depts, setDepts] = useState<{ label: string; value: string }[]>([]);
	const [editModal, setEditModal] = useState<{ open: boolean; record?: AssetOwnership }>({ open: false });
	const [batchModal, setBatchModal] = useState(false);
	const [editDept, setEditDept] = useState("");

	const loadDepts = useCallback(async () => {
		try {
			const tree = (await getOrgTree()) as OrgNode[];
			const flat: { label: string; value: string }[] = [];
			const walk = (nodes: OrgNode[]) => {
				for (const n of nodes) {
					if (n.deptCode) flat.push({ label: n.name, value: n.deptCode });
					if (n.children) walk(n.children);
				}
			};
			walk(Array.isArray(tree) ? tree : []);
			setDepts(flat);
		} catch { /* ignore */ }
	}, []);

	const loadData = useCallback(async () => {
		setLoading(true);
		try {
			const resp = await listAssetOwnership({
				assetType, ownerDeptCode, keyword: keyword || undefined, page, size,
			}) as any;
			const content = resp?.content ?? resp?.data ?? resp ?? [];
			setItems(Array.isArray(content) ? content : []);
			setTotal(resp?.totalElements ?? content.length ?? 0);
		} catch (err: any) {
			toast.error(err?.message || "Load failed");
		} finally {
			setLoading(false);
		}
	}, [assetType, ownerDeptCode, keyword, page, size]);

	useEffect(() => { loadDepts(); }, [loadDepts]);
	useEffect(() => { loadData(); }, [loadData]);

	const handleEdit = async () => {
		if (!editModal.record || !editDept) return;
		try {
			await updateAssetOwnership(editModal.record.id, { ownerDeptCode: editDept });
			toast.success("Updated");
			setEditModal({ open: false });
			loadData();
		} catch (err: any) {
			toast.error(err?.message || "Update failed");
		}
	};

	const handleBatch = async () => {
		if (!selectedRowKeys.length || !editDept) return;
		try {
			await batchUpdateAssetOwnership({ ids: selectedRowKeys, ownerDeptCode: editDept });
			toast.success(`Updated ${selectedRowKeys.length} items`);
			setBatchModal(false);
			setSelectedRowKeys([]);
			loadData();
		} catch (err: any) {
			toast.error(err?.message || "Batch update failed");
		}
	};

	const columns: ColumnsType<AssetOwnership> = [
		{ title: "Asset ID", dataIndex: "assetId", key: "assetId", ellipsis: true },
		{ title: "Type", dataIndex: "assetType", key: "assetType", width: 120,
			render: (v: string) => <Tag>{v}</Tag> },
		{ title: "Owner Dept", dataIndex: "ownerDeptCode", key: "ownerDeptCode", width: 150,
			render: (v: string) => {
				const dept = depts.find(d => d.value === v);
				return dept ? dept.label : v;
			}},
		{ title: "Source", dataIndex: "sourceId", key: "sourceId", width: 120 },
		{ title: "Assigned By", dataIndex: "assignedBy", key: "assignedBy", width: 120 },
		{ title: "Action", key: "action", width: 80,
			render: (_: any, record: AssetOwnership) => (
				<Button type="link" size="small" onClick={() => {
					setEditDept(record.ownerDeptCode);
					setEditModal({ open: true, record });
				}}>Edit</Button>
			)},
	];

	return (
		<div className="space-y-4">
			<Card title="Asset Ownership Management">
				<Space wrap className="mb-4">
					<Select allowClear placeholder="Asset Type" options={ASSET_TYPE_OPTIONS}
						style={{ width: 140 }} value={assetType} onChange={setAssetType} />
					<Select allowClear showSearch placeholder="Department" options={depts}
						style={{ width: 180 }} value={ownerDeptCode} onChange={setOwnerDeptCode} />
					<Input.Search placeholder="Search asset..." allowClear style={{ width: 220 }}
						onSearch={(v) => { setKeyword(v); setPage(0); }} />
					{selectedRowKeys.length > 0 && (
						<Button type="primary" onClick={() => { setEditDept(""); setBatchModal(true); }}>
							Batch Update ({selectedRowKeys.length})
						</Button>
					)}
				</Space>
				<Table<AssetOwnership>
					rowKey="id" columns={columns} dataSource={items} loading={loading}
					rowSelection={{ selectedRowKeys, onChange: (keys) => setSelectedRowKeys(keys as number[]) }}
					pagination={{
						current: page + 1, pageSize: size, total,
						onChange: (p) => setPage(p - 1), showTotal: (t) => `Total ${t}`,
					}}
					size="small"
				/>
			</Card>

			<Modal title="Edit Ownership" open={editModal.open}
				onCancel={() => setEditModal({ open: false })} onOk={handleEdit}>
				<div className="py-4">
					<div className="mb-2">Asset: {editModal.record?.assetType}:{editModal.record?.assetId}</div>
					<Select showSearch placeholder="Select department" options={depts}
						style={{ width: "100%" }} value={editDept} onChange={setEditDept} />
				</div>
			</Modal>

			<Modal title="Batch Update Ownership" open={batchModal}
				onCancel={() => setBatchModal(false)} onOk={handleBatch}>
				<div className="py-4">
					<div className="mb-2">Selected {selectedRowKeys.length} assets</div>
					<Select showSearch placeholder="Select department" options={depts}
						style={{ width: "100%" }} value={editDept} onChange={setEditDept} />
				</div>
			</Modal>
		</div>
	);
}
