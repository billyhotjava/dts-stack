import { Alert, Button, Space, Tag } from "antd";
import { useCallback, useEffect, useRef, useState } from "react";
import { listModelRegistrationTasks, type ModelRegistrationTask, retryModelRegistrationTask } from "@/api/modelIngestionTargetApi";
import { CompactTable } from "@/components/table";

export function ModelRegistrationInbox() {
	const [offset, setOffset] = useState(0);
	const [page, setPage] = useState<{ items: ModelRegistrationTask[]; nextOffset: number | null }>({ items: [], nextOffset: null });
	const [loading, setLoading] = useState(false);
	const [failure, setFailure] = useState("");
	const sequence = useRef(0);
	const load = useCallback(async () => {
		const request = ++sequence.current;
		setLoading(true); setFailure("");
		try { const result = await listModelRegistrationTasks(offset); if (request === sequence.current) setPage(result); }
		catch (error) { if (request === sequence.current) setFailure(error instanceof Error ? error.message : "登记任务读取失败"); }
		finally { if (request === sequence.current) setLoading(false); }
	}, [offset]);
	useEffect(() => { void load(); return () => { sequence.current++; }; }, [load]);
	const retry = async (task: ModelRegistrationTask) => {
		setLoading(true); setFailure("");
		try { await retryModelRegistrationTask(task); await load(); }
		catch (error) { setFailure(error instanceof Error ? error.message : "登记重试未能提交"); }
		finally { setLoading(false); }
	};
	return <section aria-label="模型产出登记待办" style={{ marginBottom: 16 }}>
		<h3>模型产出登记待办</h3>
		<p>构建已完成的产出在此独立登记。登记失败不影响构建结果，重试不会重新执行构建。</p>
		{failure ? <Alert type="error" showIcon message={failure} /> : null}
		<CompactTable<ModelRegistrationTask> rowKey="id" dataSource={page.items} loading={loading} pagination={false} scroll={{ x: 780 }}
			locale={{ emptyText: "当前页没有待处理登记任务" }} columns={[
				{ title: "模型与构建版本", key: "model", render: (_, row) => row.models.join("；") },
				{ title: "环境", dataIndex: "environment", render: (value: string) => ({ dev: "开发", test: "测试", prod: "生产" } as Record<string, string>)[value] || value },
				{ title: "登记状态", key: "state", render: (_, row) => <Tag color={row.state === "FAILED" || row.attempts >= 5 ? "red" : "blue"}>{row.attempts >= 5 ? "需人工重试" : row.state === "FAILED" ? "登记失败" : "排队中"}</Tag> },
				{ title: "最近结果", key: "result", render: (_, row) => `${row.errorMessage || "等待登记"}（已尝试 ${row.attempts} 次）` },
				{ title: "操作", key: "action", render: (_, row) => row.canRetry && (row.state === "FAILED" || row.attempts >= 5)
					? <Button disabled={loading} onClick={() => void retry(row)}>重试登记</Button> : "—" },
			]} />
		<Space style={{ marginTop: 8 }}>
			<Button disabled={loading} onClick={() => void load()}>刷新登记任务</Button>
			<Button disabled={loading || offset === 0} onClick={() => setOffset(Math.max(0, offset - 20))}>上一页</Button>
			<Button disabled={loading || page.nextOffset == null} onClick={() => setOffset(page.nextOffset!)}>下一页</Button>
		</Space>
	</section>;
}
