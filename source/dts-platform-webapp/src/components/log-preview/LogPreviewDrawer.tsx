import { useEffect, useRef, useState } from "react";
import { Button, Drawer, Space, Spin, Typography } from "antd";
import { useNavigate } from "react-router";
import { getDbtRunLog, getAirflowTaskLog } from "@/api/platformApi";
import { useLogPreview } from "./LogPreviewContext";
import type { LogPreviewParams } from "./LogPreviewContext";

const { Text } = Typography;

function buildLogCenterUrl(params: LogPreviewParams | null): string {
	if (!params) return "/ops/logs";
	const q = new URLSearchParams();
	q.set("entryKey", params.entryKey);
	if (params.dagRunId) q.set("runId", params.dagRunId);
	if (params.dagId) q.set("dagId", params.dagId);
	if (params.taskId) q.set("taskId", params.taskId);
	return `/ops/logs?${q.toString()}`;
}

export default function LogPreviewDrawer() {
	const { params, open, closeLogPreview } = useLogPreview();
	const navigate = useNavigate();
	const [log, setLog] = useState<string>("");
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const logRef = useRef<HTMLPreElement>(null);

	useEffect(() => {
		if (!open || !params) return;
		setLog("");
		setError(null);
		setLoading(true);

		const fetchLog = async () => {
			try {
				let logText = "";
				if (params.entryKey === "AIRFLOW_DAG" && params.dagId && params.taskId) {
					// Generic Airflow log (non-dbt or explicit taskId)
					const result = await getAirflowTaskLog(
						params.dagId,
						params.dagRunId ?? "",
						params.taskId,
						params.tryNumber ?? 1,
					);
					logText = typeof result === "string" ? result : ((result as any)?.log ?? "");
				} else if ((params.entryKey === "DBT_RUN" || params.entryKey === "AIRFLOW_DAG") && params.dagRunId) {
					// dbt default log (dbt_run task)
					const result = await getDbtRunLog(params.dagRunId, {
						dagId: params.dagId,
						taskId: params.taskId ?? "dbt_run",
						tryNumber: params.tryNumber ?? 1,
					});
					logText = typeof result === "string" ? result : ((result as any)?.log ?? "");
				} else {
					setError("不支持的日志类型或参数不完整");
					return;
				}
				setLog(logText);
				setTimeout(() => {
					if (logRef.current) logRef.current.scrollTop = logRef.current.scrollHeight;
				}, 50);
			} catch (e: unknown) {
				setError((e as { message?: string })?.message ?? "日志加载失败");
			} finally {
				setLoading(false);
			}
		};
		void fetchLog();
	}, [open, params]);

	const title =
		params?.title ??
		`[${params?.entryKey ?? ""}] ${params?.dagId ?? ""}${params?.taskId ? ` / ${params.taskId}` : ""}`;

	return (
		<Drawer
			title={title}
			open={open}
			onClose={closeLogPreview}
			width={680}
			footer={
				<Space>
					<Button
						type="link"
						onClick={() => {
							closeLogPreview();
							navigate(buildLogCenterUrl(params));
						}}
					>
						在日志中心打开 →
					</Button>
					{params?.tryNumber && params.tryNumber > 1 && (
						<Text type="secondary">当前查看第 {params.tryNumber} 次尝试</Text>
					)}
				</Space>
			}
		>
			{loading && (
				<div className="flex h-40 items-center justify-center">
					<Spin tip="加载日志中..." />
				</div>
			)}
			{error && !loading && <Text type="danger">{error}</Text>}
			{!loading && !error && (
				<pre
					ref={logRef}
					style={{
						background: "#1e1e1e",
						color: "#d4d4d4",
						fontFamily: "monospace",
						fontSize: 12,
						lineHeight: 1.6,
						padding: 16,
						borderRadius: 6,
						maxHeight: "calc(100vh - 200px)",
						overflowY: "auto",
						overflowX: "auto",
						whiteSpace: "pre-wrap",
						wordBreak: "break-all",
					}}
				>
					{log || "（日志为空）"}
				</pre>
			)}
		</Drawer>
	);
}
