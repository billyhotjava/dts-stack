import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Alert, Button, Card, DatePicker, Form, Input, Modal, Select, Space, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { useNavigate, useSearchParams } from "react-router";
import { PageHeader } from "@/components/page-header";
import { JourneyContextBar, buildJourneyUrl } from "@/components/journey";
import opsService, { type OpsBackfill } from "@/api/services/opsService";
import { listAirflowJobs } from "@/api/platformApi";
import { CompactTable, RecordDetailDrawer, appendDetailAction } from "@/components/table";

const { RangePicker } = DatePicker;

type AirflowJob = { dagId: string; name?: string };

const formatDate = (value?: string) => {
	if (!value) return "-";
	const date = new Date(value);
	return Number.isNaN(date.valueOf()) ? value : date.toLocaleString();
};

export default function OpsBackfillPage() {
	const navigate = useNavigate();
	const [searchParams] = useSearchParams();
	const journeyRoute = (route: string) => buildJourneyUrl(route, searchParams);
	const [records, setRecords] = useState<OpsBackfill[]>([]);
	const [jobs, setJobs] = useState<AirflowJob[]>([]);
	const [loading, setLoading] = useState(false);
	const [modalOpen, setModalOpen] = useState(false);
	const [detailRow, setDetailRow] = useState<OpsBackfill | null>(null);
	const [form] = Form.useForm();

	const jobOptions = useMemo(
		() => jobs.map((job) => ({ label: job.name || job.dagId, value: job.dagId })),
		[jobs],
	);

	const loadBackfills = async () => {
		setLoading(true);
		try {
			const list = await opsService.backfills();
			setRecords(Array.isArray(list) ? (list as OpsBackfill[]) : []);
		} catch {
			// handled by global interceptor
		} finally {
			setLoading(false);
		}
	};

	const loadJobs = async () => {
		try {
			const list = await listAirflowJobs(200);
			setJobs(Array.isArray(list) ? (list as AirflowJob[]) : []);
		} catch {
			// handled by global interceptor
		}
	};

	useEffect(() => {
		void loadBackfills();
		void loadJobs();
	}, []);

	const openModal = () => {
		form.resetFields();
		setModalOpen(true);
	};

	const saveBackfill = async () => {
		try {
			const values = await form.validateFields();
			const [from, to] = values.range || [];
			await opsService.createBackfill({
				dagId: values.dagId,
				dateFrom: from ? dayjs(from).format("YYYY-MM-DD") : undefined,
				dateTo: to ? dayjs(to).format("YYYY-MM-DD") : undefined,
				note: values.note,
			});
			toast.success("补数任务已提交");
			setModalOpen(false);
			await loadBackfills();
		} catch (error: any) {
			if (error?.errorFields) return;
			// API errors handled by global interceptor
		}
	};

	const baseColumns: ColumnsType<OpsBackfill> = [
		{ title: "DAG", dataIndex: "dagId", render: (v) => v || "-" },
		{ title: "日期范围", dataIndex: "dateFrom", render: (_, record) => `${record.dateFrom || "-"} ~ ${record.dateTo || "-"}` },
		{ title: "状态", dataIndex: "status", render: (v) => <Tag>{v || "-"}</Tag> },
		{ title: "触发时间", dataIndex: "triggeredAt", render: (v) => formatDate(v) , sorter: (a, b) => { const ta = a.triggeredAt ? new Date(a.triggeredAt as any).getTime() : 0; const tb = b.triggeredAt ? new Date(b.triggeredAt as any).getTime() : 0; return ta - tb; } },
		{ title: "外部运行ID", dataIndex: "externalRunId", render: (v) => v || "-" },
		{ title: "备注", dataIndex: "message", render: (v) => v || "-" },
		{
			title: "操作",
			key: "actions",
			width: 260,
			fixed: "right",
			render: (_: unknown, record) => (
				<Space size="small" wrap>
					<Button
						type="link"
						size="small"
						onClick={() => navigate(journeyRoute(`/explore/etl/orchestration?dagId=${encodeURIComponent(record.dagId || "")}`))}
					>
						查看源任务
					</Button>
					<Button
						type="link"
						size="small"
						onClick={() => navigate(journeyRoute(`/ops/instances?runId=${encodeURIComponent(record.externalRunId || record.id)}`))}
					>
						查看实例
					</Button>
					<Button
						type="link"
						size="small"
						onClick={() => navigate(journeyRoute(`/ops/logs?entryKey=AIRFLOW_DAG&runId=${encodeURIComponent(record.externalRunId || record.id)}`))}
					>
						查看日志
					</Button>
				</Space>
			),
		},
	];

	const columns = useMemo(() => appendDetailAction(baseColumns, (row) => setDetailRow(row)), []);

	return (
		<div className="space-y-6 px-6 py-6">
			<JourneyContextBar stage="evidence" />
			<PageHeader
				title="补数管理"
				actions={
					<Space wrap>
						<Button onClick={() => navigate("/ops/instances")}>查看实例</Button>
						<Button onClick={() => navigate("/ops/logs")}>查看日志</Button>
						<Button type="primary" onClick={openModal}>
							新建补数
						</Button>
					</Space>
				}
			/>
			<Card
				extra={
					<Button type="primary" onClick={openModal}>
						新建补数
					</Button>
				}
			>
				<Alert
					className="mb-4"
					type="info"
					showIcon
					message="影响说明"
					description="补数会重新触发所选 DAG 的历史日期范围，请确认下游资产和报表可以接受重复刷新。"
				/>
				<CompactTable<OpsBackfill> rowKey={(record) => record.id} columns={columns} dataSource={records} loading={loading} />
			</Card>
			<RecordDetailDrawer<OpsBackfill>
				open={detailRow !== null}
				onClose={() => setDetailRow(null)}
				record={detailRow}
				columns={baseColumns}
				title="补数详情"
			/>

			<Modal
				open={modalOpen}
				title="新建补数任务"
				onCancel={() => setModalOpen(false)}
				onOk={saveBackfill}
				okText="提交"
				destroyOnClose
			>
				<Form form={form} layout="vertical">
					<Form.Item label="任务 DAG" name="dagId" rules={[{ required: true, message: "请选择任务" }]}
					>
						<Select options={jobOptions} showSearch optionFilterProp="label" />
					</Form.Item>
					<Form.Item label="日期范围" name="range" rules={[{ required: true, message: "请选择范围" }]}
					>
						<RangePicker />
					</Form.Item>
					<Form.Item label="备注" name="note">
						<Input.TextArea rows={3} />
					</Form.Item>
				</Form>
			</Modal>
		</div>
	);
}
