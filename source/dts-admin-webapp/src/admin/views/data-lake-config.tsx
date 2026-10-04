import { useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Select, Space, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { adminApi } from "@/admin/api/adminApi";
import type { ConnectionTestLog, HiveConnectionTestResult, InfraDataSource } from "@/types/infra";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Text } from "@/ui/typography";
import { toast } from "sonner";

const TYPE_LABELS: Record<string, string> = {
	HIVE: "Hive",
	INCEPTOR: "Inceptor",
	JDBC: "JDBC",
	ICEBERG: "Iceberg",
	CLICKHOUSE: "ClickHouse",
	POSTGRESQL: "PostgreSQL",
};

export default function DataLakeConfigView() {
	const queryClient = useQueryClient();
	const navigate = useNavigate();
	const [testResult, setTestResult] = useState<HiveConnectionTestResult | null>(null);
	const [lastTestName, setLastTestName] = useState<string | null>(null);
	const [lakeTestingId, setLakeTestingId] = useState<string | null>(null);
	const [testLogSourceId, setTestLogSourceId] = useState<string | undefined>(undefined);

	const { data: dataLakes = [] } = useQuery({
		queryKey: ["admin", "data-lakes"],
		queryFn: adminApi.listDataLakes,
	});

	const { data: testLogs = [], isFetching: testLogsLoading } = useQuery({
		queryKey: ["admin", "data-lake-test-logs", testLogSourceId],
		queryFn: () => adminApi.getDataLakeTestLogs(testLogSourceId),
	});

	const dataLakeNameMap = useMemo(() => {
		const entries = dataLakes
			.filter((lake) => lake.id)
			.map((lake) => [lake.id as string, lake.name] as const);
		return new Map(entries);
	}, [dataLakes]);

	const defaultLake = useMemo(() => dataLakes.find((lake) => lake.defaulted), [dataLakes]);
	const defaultStatusTag = useMemo(() => {
		const label = (defaultLake?.status || "").toString().toUpperCase();
		const color = label === "ACTIVE" ? "green" : label === "INACTIVE" ? "red" : "default";
		return <Tag color={color}>{label || "未设置"}</Tag>;
	}, [defaultLake?.status]);

	const handleTestLake = async (record: InfraDataSource) => {
		if (!record.id) {
			toast.error("请先保存后再测试");
			return;
		}
		try {
			setLakeTestingId(record.id);
			setLastTestName(record.name || null);
			const result = await adminApi.testDataLakeConnection(record.id, {});
			setTestResult(result);
			if (result.success) {
				toast.success("连接成功");
			} else {
				toast.error(result.message || "连接失败");
			}
			queryClient.invalidateQueries({ queryKey: ["admin", "data-lake-test-logs"] });
			queryClient.invalidateQueries({ queryKey: ["admin", "data-lakes"] });
		} catch (error: any) {
			toast.error(error?.message || "测试失败");
		} finally {
			setLakeTestingId(null);
		}
	};

	const dataLakeColumns: ColumnsType<InfraDataSource> = useMemo(
		() => [
			{
				title: "名称",
				dataIndex: "name",
				key: "name",
				render: (value) => value || "--",
			},
			{
				title: "类型",
				dataIndex: "type",
				key: "type",
				width: 140,
				render: (value) => {
					const key = (value || "").toString().toUpperCase();
					return TYPE_LABELS[key] || value || "--";
				},
			},
			{
				title: "默认",
				dataIndex: "defaulted",
				key: "defaulted",
				width: 100,
				render: (value) => (value ? <Tag color="green">默认</Tag> : <Tag color="default">--</Tag>),
			},
			{
				title: "JDBC",
				dataIndex: "jdbcUrl",
				key: "jdbcUrl",
				ellipsis: true,
				render: (value) => value || "--",
			},
			{
				title: "状态",
				dataIndex: "status",
				key: "status",
				width: 120,
				render: (value) => {
					const label = (value || "").toString().toUpperCase();
					const color = label === "ACTIVE" ? "green" : label === "INACTIVE" ? "red" : "default";
					return <Tag color={color}>{label || "--"}</Tag>;
				},
			},
			{
				title: "操作",
				key: "actions",
				width: 300,
				render: (_, record) => (
					<Space size={8}>
						<Button
							size="small"
							onClick={async () => {
								if (!record.id) return;
								try {
									await adminApi.setDefaultDataLake(record.id);
									toast.success("已设为默认数据湖");
									queryClient.invalidateQueries({ queryKey: ["admin", "data-lakes"] });
								} catch (error: any) {
									toast.error(error?.message || "设置默认失败");
								}
							}}
						>
							设为默认
						</Button>
						<Button size="small" loading={lakeTestingId === record.id} onClick={() => handleTestLake(record)}>
							测试
						</Button>
						<Button
							size="small"
							onClick={() => {
								if (!record.id) return;
								navigate(`/admin/data-lake/${record.id}?view=detail`);
							}}
						>
							详情
						</Button>
						<Button
							size="small"
							onClick={() => {
								if (!record.id) return;
								navigate(`/admin/data-lake/${record.id}`);
							}}
						>
							编辑
						</Button>
						{record.defaulted ? null : (
							<Button
								size="small"
								danger
								onClick={async () => {
									if (!record.id) return;
									try {
										await adminApi.deleteDataLake(record.id);
										toast.success("已删除数据湖");
										queryClient.invalidateQueries({ queryKey: ["admin", "data-lakes"] });
									} catch (error: any) {
										toast.error(error?.message || "删除失败");
									}
								}}
							>
								删除
							</Button>
						)}
					</Space>
				),
			},
		],
		[queryClient, lakeTestingId, navigate],
	);

	const logColumns: ColumnsType<ConnectionTestLog> = useMemo(
		() => [
			{
				title: "数据湖",
				dataIndex: "dataSourceId",
				key: "dataSourceId",
				width: 160,
				render: (value) => {
					if (!value) return "--";
					return dataLakeNameMap.get(value) || value;
				},
			},
			{
				title: "时间",
				dataIndex: "createdAt",
				key: "createdAt",
				width: 180,
				render: (value) => (value ? new Date(value).toLocaleString() : "--"),
			},
			{
				title: "结果",
				dataIndex: "result",
				key: "result",
				width: 120,
				render: (value) => {
					const label = (value || "").toString().toUpperCase();
					const color = label === "SUCCESS" ? "green" : label === "FAILURE" ? "red" : "default";
					return <Tag color={color}>{label || "--"}</Tag>;
				},
			},
			{
				title: "耗时 (ms)",
				dataIndex: "elapsedMs",
				key: "elapsedMs",
				width: 140,
				render: (value) => (value ?? "--"),
			},
			{
				title: "信息",
				dataIndex: "message",
				key: "message",
				render: (value) => value || "--",
			},
		],
		[dataLakeNameMap],
	);

	return (
		<div className="w-full max-w-none px-6 py-6 space-y-6">
			<div className="flex flex-wrap items-center justify-between gap-3">
				<div>
					<Text variant="body1" className="block text-lg font-semibold">
						数据湖配置
					</Text>
					<Text variant="body3" className="text-muted-foreground">
						配置数据湖连接并指定默认目标端，平台入湖任务将自动使用默认数据湖。
					</Text>
				</div>
			</div>

			<Card>
				<CardHeader>
					<CardTitle>默认数据湖状态</CardTitle>
				</CardHeader>
				<CardContent className="space-y-4 text-sm">
					<div className="flex flex-wrap items-center gap-3">
						{defaultStatusTag}
						<Text variant="body3" className="text-muted-foreground">
							{defaultLake?.name || "未设置默认数据湖"}
						</Text>
					</div>
					<div className="grid gap-3 md:grid-cols-3">
						<div>
							<Text variant="body3" className="text-muted-foreground">
								类型
							</Text>
							<Text variant="body2">{defaultLake?.type || "--"}</Text>
						</div>
						<div>
							<Text variant="body3" className="text-muted-foreground">
								最近验证
							</Text>
							<Text variant="body2">{defaultLake?.lastVerifiedAt || "--"}</Text>
						</div>
						<div>
							<Text variant="body3" className="text-muted-foreground">
								心跳状态
							</Text>
							<Text variant="body2">{defaultLake?.heartbeatStatus || "--"}</Text>
						</div>
					</div>
					{defaultLake?.lastError ? (
						<Alert type="error" message="最近一次错误" description={defaultLake.lastError} showIcon />
					) : null}
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle>数据湖列表</CardTitle>
				</CardHeader>
				<CardContent className="space-y-4 text-sm">
					<div className="flex flex-wrap items-center justify-between gap-3">
						<Text variant="body3" className="text-muted-foreground">
							统一维护数据湖配置与目标端参数。
						</Text>
						<Button type="primary" onClick={() => navigate("/admin/data-lake/new")}>新增数据湖</Button>
					</div>
					<Table
						rowKey={(row) => row.id || `${row.name}-${row.jdbcUrl}`}
						size="small"
						columns={dataLakeColumns}
						dataSource={dataLakes}
						pagination={false}
						className="text-sm"
						rowClassName={() => "text-sm"}
					/>
				</CardContent>
			</Card>

			{testResult ? (
				<Card>
					<CardHeader>
						<CardTitle>最新测试结果</CardTitle>
					</CardHeader>
					<CardContent className="text-sm">
						{(() => {
							const parts = [`耗时 ${testResult.elapsedMillis ?? "--"} ms`];
							if (testResult.engineVersion) {
								parts.push(`引擎 ${testResult.engineVersion}`);
							}
							if (testResult.driverVersion) {
								parts.push(`驱动 ${testResult.driverVersion}`);
							}
							if (lastTestName) {
								parts.unshift(`数据湖 ${lastTestName}`);
							}
							return (
								<Alert
									type={testResult.success ? "success" : "error"}
									message={testResult.message || (testResult.success ? "连接成功" : "连接失败")}
									description={parts.join(" · ")}
									showIcon
								/>
							);
						})()}
					</CardContent>
				</Card>
			) : null}

			<Card>
				<CardHeader>
					<CardTitle>测试记录</CardTitle>
				</CardHeader>
				<CardContent className="space-y-3 text-sm">
					<div className="flex flex-wrap items-center justify-between gap-3">
						<Text variant="body3" className="text-muted-foreground">
							支持按数据湖过滤最近 20 条连接测试记录。
						</Text>
						<Select
							allowClear
							placeholder="全部数据湖"
							value={testLogSourceId}
							options={dataLakes
								.filter((lake) => lake.id)
								.map((lake) => ({ value: lake.id as string, label: lake.name }))}
							style={{ minWidth: 220 }}
							size="small"
							onChange={(value) => setTestLogSourceId(value || undefined)}
						/>
					</div>
					{testLogs.length === 0 && !testLogsLoading ? (
						<Alert type="info" message="暂无测试记录" showIcon />
					) : (
						<Table
							rowKey={(row) => row.id || `${row.createdAt}-${row.result}`}
							size="small"
							columns={logColumns}
							dataSource={testLogs}
							pagination={false}
							loading={testLogsLoading}
							className="text-sm"
							rowClassName={() => "text-sm"}
						/>
					)}
				</CardContent>
			</Card>
		</div>
	);
}
