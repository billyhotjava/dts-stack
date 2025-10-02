import { useCallback, useEffect, useMemo, useState } from "react";
import { AuditLogService } from "@/api/services/auditLogService";
import type { AuditLog, AuditLogPageResponse } from "#/entity";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Text } from "@/ui/typography";
import { toast } from "sonner";

interface FilterState {
	from?: string;
	to?: string;
	module?: string;
	actor?: string;
	resource?: string;
	clientIp?: string;
	action?: string;
}

const ADMIN_LABELS: Record<string, string> = {
	sysadmin: "系统管理员",
	syadmin: "系统管理员",
	authadmin: "授权管理员",
	auditadmin: "安全审计员",
	opadmin: "运维管理员",
};

const DEFAULT_PAGE_SIZE = 20;

export default function AuditCenterView() {
	const [filters, setFilters] = useState<FilterState>({});
	const [logs, setLogs] = useState<AuditLog[]>([]);
	const [loading, setLoading] = useState(false);
	const [page, setPage] = useState(0);
	const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
	const [totalElements, setTotalElements] = useState(0);
	const [totalPages, setTotalPages] = useState(0);
	const [moduleOptions, setModuleOptions] = useState<string[]>([]);
	const [exporting, setExporting] = useState(false);

	const loadLogs = useCallback(
		async (nextPage: number, nextSize: number) => {
			setLoading(true);
			setPage(nextPage);
			setSize(nextSize);
			try {
				const params = buildQuery(filters);
				const response: AuditLogPageResponse = await AuditLogService.getAuditLogs(nextPage, nextSize, "occurredAt,desc", params);
				setLogs(response.content);
				setPage(response.page);
				setSize(response.size);
				setTotalElements(response.totalElements);
				setTotalPages(response.totalPages);
				setModuleOptions((prev) => {
					const values = new Set(prev);
					response.content.forEach((item) => values.add(item.module));
					return Array.from(values).filter(Boolean).sort();
				});
			} catch (error) {
				console.error("Failed to load audit logs", error);
				toast.error("加载审计日志失败");
			} finally {
				setLoading(false);
			}
	},
		[filters],
	);

	useEffect(() => {
		loadLogs(page, size);
	}, [loadLogs, page, size]);

	const handleRefresh = useCallback(() => {
		loadLogs(0, size);
	}, [loadLogs, size]);

	const handleExport = useCallback(async () => {
		try {
			setExporting(true);
			const params = buildQuery(filters);
			const query = new URLSearchParams({
				...params,
				page: page.toString(),
				size: size.toString(),
			}).toString();
			const response = await fetch(`/api/audit-logs/export?${query}`, {
				headers: { Authorization: `Bearer ${sessionStorage.getItem("access_token") ?? ""}` },
			});
			if (!response.ok) {
				throw new Error(`导出失败: ${response.status}`);
			}
			const blob = await response.blob();
			downloadBlob(blob, `audit-logs-${Date.now()}.csv`);
			toast.success("已导出筛选后的审计日志");
		} catch (error) {
			console.error("Export audit logs failed", error);
			toast.error("导出失败，请稍后重试");
		} finally {
			setExporting(false);
		}
	}, [filters, page, size]);

	const paginationText = useMemo(() => {
		const from = page * size + 1;
		const to = Math.min(totalElements, (page + 1) * size);
		return totalElements === 0 ? "暂无记录" : `显示第 ${from}-${to} 条，共 ${totalElements} 条`;
	}, [page, size, totalElements]);

	return (
		<div className="space-y-6">
			<Card>
				<CardHeader className="space-y-2">
					<CardTitle>查询条件</CardTitle>
					<Text variant="body3" className="text-muted-foreground">
						支持按时间范围、功能模块、操作人、目标位置与 IP 筛选审计记录。
					</Text>
				</CardHeader>
				<CardContent className="space-y-4">
					<div className="grid gap-4 md:grid-cols-2 xl:grid-cols-6">
						<DateTimeField
							label="起始时间"
							value={filters.from}
							onChange={(value) => setFilters((prev) => ({ ...prev, from: value }))}
						/>
						<DateTimeField
							label="终止时间"
							value={filters.to}
							onChange={(value) => setFilters((prev) => ({ ...prev, to: value }))}
						/>
						<SelectField
							label="功能模块"
							value={filters.module ?? ""}
							onChange={(value) => setFilters((prev) => ({ ...prev, module: value || undefined }))}
							options={["", ...moduleOptions]}
						/>
						<InputField
							label="操作人"
							placeholder="如 sysadmin"
							value={filters.actor ?? ""}
							onChange={(value) => setFilters((prev) => ({ ...prev, actor: value || undefined }))}
						/>
						<InputField
							label="目标位置"
							placeholder="例如 /admin/approval"
							value={filters.resource ?? ""}
							onChange={(value) => setFilters((prev) => ({ ...prev, resource: value || undefined }))}
						/>
						<InputField
							label="IP 地址"
							placeholder="例如 10.10."
							value={filters.clientIp ?? ""}
							onChange={(value) => setFilters((prev) => ({ ...prev, clientIp: value || undefined }))}
						/>
						<InputField
							label="操作关键词"
							placeholder="创建/审批/导出"
							value={filters.action ?? ""}
							onChange={(value) => setFilters((prev) => ({ ...prev, action: value || undefined }))}
						/>
					</div>
					<div className="flex flex-wrap gap-3">
						<Button type="button" variant="outline" onClick={() => setFilters({})}>
							重置条件
						</Button>
						<Button type="button" variant="ghost" onClick={handleRefresh} disabled={loading}>
							{loading ? "刷新中..." : "刷新"}
						</Button>
					</div>
				</CardContent>
			</Card>

			<Card>
				<CardHeader className="space-y-2 sm:flex sm:items-center sm:justify-between sm:space-y-0">
					<div>
						<CardTitle>日志记录</CardTitle>
						<Text variant="body3" className="text-muted-foreground">
							{paginationText}
						</Text>
					</div>
					<div className="flex items-center gap-2">
						<Button type="button" onClick={handleExport} disabled={exporting || logs.length === 0}>
							{exporting ? "正在导出..." : "导出日志"}
						</Button>
					</div>
				</CardHeader>
				<CardContent className="overflow-x-auto">
					<table className="min-w-full table-fixed text-sm">
						<thead className="bg-muted/60">
							<tr className="text-left">
								<th className="px-4 py-3 font-medium">ID</th>
								<th className="px-4 py-3 font-medium">时间</th>
								<th className="px-4 py-3 font-medium">模块</th>
								<th className="px-4 py-3 font-medium">操作</th>
								<th className="px-4 py-3 font-medium">操作者 / IP</th>
								<th className="px-4 py-3 font-medium">目标</th>
								<th className="px-4 py-3 font-medium">结果</th>
							</tr>
						</thead>
						<tbody>
							{loading ? (
								<tr>
									<td colSpan={7} className="px-4 py-6 text-center text-muted-foreground">
										正在加载...
									</td>
								</tr>
							) : logs.length === 0 ? (
								<tr>
									<td colSpan={7} className="px-4 py-6 text-center text-muted-foreground">
										暂无审计记录
									</td>
								</tr>
							) : (
								logs.map((log) => (
									<tr key={log.id} className="border-b align-top last:border-b-0">
										<td className="px-4 py-3 font-medium">{log.id}</td>
										<td className="px-4 py-3 text-sm">{formatDateTime(log.occurredAt)}</td>
										<td className="px-4 py-3">{log.module}</td>
										<td className="px-4 py-3 text-sm">
											<div className="font-medium">{log.action}</div>
											{log.payloadPreview ? (
												<div className="text-xs text-muted-foreground">{log.payloadPreview}</div>
											) : null}
										</td>
										<td className="px-4 py-3 text-xs text-muted-foreground">
											<div>操作者：{formatOperatorName(log.actor)}</div>
											<div>IP：{log.clientIp || "-"}</div>
										</td>
										<td className="px-4 py-3 text-xs text-muted-foreground">
											<div>{log.resourceType || "-"}</div>
											<div>{log.resourceId || "-"}</div>
										</td>
										<td className="px-4 py-3">
											<Badge variant={log.result === "FAILURE" ? "destructive" : "secondary"}>{log.result}</Badge>
										</td>
									</tr>
								))
							)}
						</tbody>
					</table>
				</CardContent>
				<CardContent className="flex items-center justify-between pt-0">
					<div className="text-sm text-muted-foreground">{paginationText}</div>
					<div className="flex items-center gap-2">
						<Button
							variant="outline"
							size="sm"
							onClick={() => loadLogs(Math.max(page - 1, 0), size)}
							disabled={page === 0 || loading}
						>
							上一页
						</Button>
						<Button
							variant="outline"
							size="sm"
							onClick={() => loadLogs(Math.min(page + 1, totalPages - 1), size)}
							disabled={page >= totalPages - 1 || loading}
						>
							下一页
						</Button>
					</div>
				</CardContent>
			</Card>
		</div>
	);
}

function buildQuery(filters: FilterState): Record<string, string> {
	const params: Record<string, string> = {};
	if (filters.from) {
		params.from = new Date(filters.from).toISOString();
	}
	if (filters.to) {
		params.to = new Date(filters.to).toISOString();
	}
	if (filters.module) {
		params.module = filters.module;
	}
	if (filters.actor) {
		params.actor = filters.actor;
	}
	if (filters.resource) {
		params.resource = filters.resource;
	}
	if (filters.clientIp) {
		params.clientIp = filters.clientIp;
	}
	if (filters.action) {
		params.action = filters.action;
	}
	return params;
}

function formatDateTime(value: string) {
	const date = new Date(value);
	if (Number.isNaN(date.getTime())) {
		return value;
	}
	return date.toLocaleString("zh-CN", { hour12: false });
}

function formatOperatorName(value: string) {
	const normalized = value?.toLowerCase?.();
	if (!normalized) {
		return value;
	}
	const label = ADMIN_LABELS[normalized];
	if (label) {
		return `${label}（${value}）`;
	}
	return value;
}

interface FieldProps {
	label: string;
	value?: string;
	onChange: (value: string | undefined) => void;
	placeholder?: string;
}

function DateTimeField({ label, value, onChange }: FieldProps) {
	return (
		<div className="space-y-2">
			<Text variant="body3" className="text-muted-foreground">
				{label}
			</Text>
			<Input
				type="datetime-local"
				value={value ?? ""}
				onChange={(event) => onChange(event.target.value || undefined)}
			/>
		</div>
	);
}

function InputField({ label, value, onChange, placeholder }: FieldProps) {
	return (
		<div className="space-y-2">
			<Text variant="body3" className="text-muted-foreground">
				{label}
			</Text>
			<Input
				placeholder={placeholder}
				value={value ?? ""}
				onChange={(event) => onChange(event.target.value || undefined)}
			/>
		</div>
	);
}

interface SelectProps {
	label: string;
	value: string;
	onChange: (value: string) => void;
	options: string[];
}

function SelectField({ label, value, onChange, options }: SelectProps) {
	return (
		<div className="space-y-2">
			<Text variant="body3" className="text-muted-foreground">
				{label}
			</Text>
			<select
				className="h-10 w-full rounded-md border border-border bg-background px-3 text-sm"
				value={value}
				onChange={(event) => onChange(event.target.value)}
			>
				{options.map((opt) => (
					<option key={opt || "all"} value={opt}>
						{opt ? opt : "全部模块"}
					</option>
				))}
			</select>
		</div>
	);
}

function buildAuthHeader(): string {
	const store = localStorage.getItem("userStore");
	if (store) {
		try {
			const parsed = JSON.parse(store);
			const token = parsed?.userToken?.accessToken;
			if (token) {
				return `Bearer ${token}`;
			}
		} catch (error) {
			console.warn("Failed to parse userStore for audit export", error);
		}
	}
	return "";
}

function downloadBlob(blob: Blob, filename: string) {
	const url = URL.createObjectURL(blob);
	const link = document.createElement("a");
	link.href = url;
	link.download = filename;
	document.body.appendChild(link);
	link.click();
	document.body.removeChild(link);
	URL.revokeObjectURL(url);
}
