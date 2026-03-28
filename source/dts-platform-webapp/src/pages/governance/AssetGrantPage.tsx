import { useCallback, useState } from "react";
import { toast } from "sonner";
import { Button, Card, DatePicker, Form, Input, Modal, Radio, Select, Space, Table, Tag, Popconfirm } from "antd";
import type { ColumnsType } from "antd/es/table";
import { listAssetGrants, createAssetGrant, deleteAssetGrant } from "@/api/platformApi";

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

const PERMISSION_OPTIONS = [
	{ label: "READ", value: "READ" },
	{ label: "EDIT", value: "EDIT" },
	{ label: "MANAGE", value: "MANAGE" },
];

const GRANTEE_TYPE_OPTIONS = [
	{ label: "User", value: "USER" },
	{ label: "Role", value: "ROLE" },
	{ label: "Dept", value: "DEPT" },
];

const ASSET_TYPE_OPTIONS = [
	{ label: "TABLE", value: "TABLE" },
	{ label: "CARD", value: "CARD" },
	{ label: "DASHBOARD", value: "DASHBOARD" },
	{ label: "SCREEN", value: "SCREEN" },
	{ label: "MODEL", value: "MODEL" },
];

export default function AssetGrantPage() {
	const [grants, setGrants] = useState<AssetGrant[]>([]);
	const [loading, setLoading] = useState(false);
	const [searchType, setSearchType] = useState("TABLE");
	const [searchId, setSearchId] = useState("");
	const [grantModal, setGrantModal] = useState(false);
	const [form] = Form.useForm();

	const loadGrants = useCallback(async () => {
		if (!searchId) return;
		setLoading(true);
		try {
			const resp = await listAssetGrants({ assetType: searchType, assetId: searchId }) as AssetGrant[];
			setGrants(Array.isArray(resp) ? resp : []);
		} catch (err: any) {
			toast.error(err?.message || "Load failed");
		} finally {
			setLoading(false);
		}
	}, [searchType, searchId]);

	const handleCreate = async () => {
		try {
			const values = await form.validateFields();
			await createAssetGrant({
				assetType: searchType,
				assetId: searchId,
				granteeType: values.granteeType,
				granteeId: values.granteeId,
				permission: values.permission,
				validFrom: values.validRange?.[0]?.toISOString(),
				validTo: values.validRange?.[1]?.toISOString(),
				grantReason: values.grantReason,
			});
			toast.success("Grant created");
			setGrantModal(false);
			form.resetFields();
			loadGrants();
		} catch (err: any) {
			if (err?.errorFields) return;
			toast.error(err?.message || "Create failed");
		}
	};

	const handleRevoke = async (id: number) => {
		try {
			await deleteAssetGrant(id);
			toast.success("Grant revoked");
			loadGrants();
		} catch (err: any) {
			toast.error(err?.message || "Revoke failed");
		}
	};

	const columns: ColumnsType<AssetGrant> = [
		{ title: "Grantee Type", dataIndex: "granteeType", key: "granteeType", width: 100,
			render: (v: string) => <Tag color={v === "USER" ? "blue" : v === "ROLE" ? "green" : "orange"}>{v}</Tag> },
		{ title: "Grantee", dataIndex: "granteeId", key: "granteeId" },
		{ title: "Permission", dataIndex: "permission", key: "permission", width: 100,
			render: (v: string) => <Tag color={v === "MANAGE" ? "red" : v === "EDIT" ? "orange" : "default"}>{v}</Tag> },
		{ title: "Valid To", dataIndex: "validTo", key: "validTo", width: 180,
			render: (v?: string) => v ? new Date(v).toLocaleDateString() : "Permanent" },
		{ title: "Granted By", dataIndex: "grantedBy", key: "grantedBy", width: 120 },
		{ title: "Reason", dataIndex: "grantReason", key: "grantReason", ellipsis: true },
		{ title: "Action", key: "action", width: 80,
			render: (_: any, record: AssetGrant) => (
				<Popconfirm title="Revoke this grant?" onConfirm={() => handleRevoke(record.id)}>
					<Button type="link" size="small" danger>Revoke</Button>
				</Popconfirm>
			)},
	];

	return (
		<div className="space-y-4">
			<Card title="Asset Grant Management">
				<Space wrap className="mb-4">
					<Select options={ASSET_TYPE_OPTIONS} value={searchType} onChange={setSearchType}
						style={{ width: 140 }} />
					<Input.Search placeholder="Enter asset ID" style={{ width: 220 }}
						onSearch={(v) => { setSearchId(v); }} />
					{searchId && (
						<Button type="primary" onClick={() => { form.resetFields(); setGrantModal(true); }}>
							+ New Grant
						</Button>
					)}
				</Space>

				{searchId && (
					<Table<AssetGrant>
						rowKey="id" columns={columns} dataSource={grants}
						loading={loading} size="small" pagination={false}
					/>
				)}
			</Card>

			<Modal title="New Grant" open={grantModal} onCancel={() => setGrantModal(false)} onOk={handleCreate}
				destroyOnClose>
				<Form form={form} layout="vertical" className="pt-4">
					<div className="mb-2 text-gray-500">Asset: {searchType}:{searchId}</div>
					<Form.Item name="granteeType" label="Grantee Type" rules={[{ required: true }]} initialValue="USER">
						<Radio.Group options={GRANTEE_TYPE_OPTIONS} />
					</Form.Item>
					<Form.Item name="granteeId" label="Grantee" rules={[{ required: true, message: "Required" }]}>
						<Input placeholder="Username / Role name / Dept code" />
					</Form.Item>
					<Form.Item name="permission" label="Permission" rules={[{ required: true }]} initialValue="READ">
						<Radio.Group options={PERMISSION_OPTIONS} />
					</Form.Item>
					<Form.Item name="validRange" label="Valid Period">
						<DatePicker.RangePicker style={{ width: "100%" }} />
					</Form.Item>
					<Form.Item name="grantReason" label="Reason / OA Reference">
						<Input.TextArea rows={2} placeholder="OA approval number or reason" />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
