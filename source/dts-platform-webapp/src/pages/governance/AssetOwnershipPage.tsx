import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Button, Card, Input, Modal, Select, Space, Tag } from "antd";
import { CompactTable, RecordDetailDrawer, appendDetailAction } from "@/components/table";
import { EditOutlined } from "@ant-design/icons";
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
	const [detailRow, setDetailRow] = useState<AssetOwnership | null>(null);

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
		} catch {
			/* global interceptor handles toast */
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
			toast.success("已更新");
			setEditModal({ open: false });
			loadData();
		} catch {
			/* global interceptor handles toast */
		}
	};

	const handleBatch = async () => {
		if (!selectedRowKeys.length || !editDept) return;
		try {
			await batchUpdateAssetOwnership({ ids: selectedRowKeys, ownerDeptCode: editDept });
			toast.success(`已更新 ${selectedRowKeys.length} 项`);
			setBatchModal(false);
			setSelectedRowKeys([]);
			loadData();
		} catch {
			/* global interceptor handles toast */
		}
	};

	const baseColumns: ColumnsType<AssetOwnership> = [
		{ title: "资产 ID", dataIndex: "assetId", key: "assetId", ellipsis: true,
			sorter: (a, b) => (a.assetId || "").localeCompare(b.assetId || "") },
		{ title: "类型", dataIndex: "assetType", key: "assetType", width: 120,
			render: (v: string) => <Tag>{v}</Tag> },
		{ title: "所有者部门", dataIndex: "ownerDeptCode", key: "ownerDeptCode", width: 150,
			render: (v: string) => {
				const dept = depts.find(d => d.value === v);
				return dept ? dept.label : v;
			}},
		{ title: "来源", dataIndex: "sourceId", key: "sourceId", width: 120 },
		{ title: "分配者", dataIndex: "assignedBy", key: "assignedBy", width: 120 },
		{ title: "操作", dataIndex: "actions", key: "action", width: 160, fixed: "right",
			render: (_: any, record: AssetOwnership) => (
				<Button type="link" size="small" icon={<EditOutlined />} onClick={() => {
					setEditDept(record.ownerDeptCode);
					setEditModal({ open: true, record });
				}}>编辑</Button>
			)},
	];

	const columns = useMemo(
		() => appendDetailAction(baseColumns, (row) => setDetailRow(row)),
		// eslint-disable-next-line react-hooks/exhaustive-deps
		[depts],
	);

	return (
		<div className="space-y-4">
			<Card title="资产所有权管理">
				<Space wrap className="mb-4">
					<Select allowClear placeholder="资产类型" options={ASSET_TYPE_OPTIONS}
						style={{ width: 140 }} value={assetType} onChange={setAssetType} />
					<Select allowClear showSearch placeholder="部门" options={depts}
						style={{ width: 180 }} value={ownerDeptCode} onChange={setOwnerDeptCode} />
					<Input.Search placeholder="搜索资产..." allowClear style={{ width: 220 }}
						onSearch={(v) => { setKeyword(v); setPage(0); }} />
					{selectedRowKeys.length > 0 && (
						<Button type="primary" onClick={() => { setEditDept(""); setBatchModal(true); }}>
							批量更新 ({selectedRowKeys.length})
						</Button>
					)}
				</Space>
				<CompactTable<AssetOwnership>
					rowKey="id" columns={columns} dataSource={items} loading={loading}
					rowSelection={{ selectedRowKeys, onChange: (keys) => setSelectedRowKeys(keys as number[]) }}
					pagination={{
						current: page + 1, pageSize: size, total,
						onChange: (p) => setPage(p - 1), showTotal: (t) => `共 ${t} 条`,
					}}
					size="small"
				/>
			</Card>

			<Modal title="编辑所有权" open={editModal.open}
				onCancel={() => setEditModal({ open: false })} onOk={handleEdit}>
				<div className="py-4">
					<div className="mb-2">资产: {editModal.record?.assetType}:{editModal.record?.assetId}</div>
					<Select showSearch placeholder="选择部门" options={depts}
						style={{ width: "100%" }} value={editDept} onChange={setEditDept} />
				</div>
			</Modal>

			<Modal title="批量更新所有权" open={batchModal}
				onCancel={() => setBatchModal(false)} onOk={handleBatch}>
				<div className="py-4">
					<div className="mb-2">已选择 {selectedRowKeys.length} 个资产</div>
					<Select showSearch placeholder="选择部门" options={depts}
						style={{ width: "100%" }} value={editDept} onChange={setEditDept} />
				</div>
			</Modal>
			<RecordDetailDrawer<AssetOwnership>
				open={detailRow !== null}
				onClose={() => setDetailRow(null)}
				record={detailRow}
				columns={baseColumns}
				title="所有权详情"
			/>
		</div>
	);
}
