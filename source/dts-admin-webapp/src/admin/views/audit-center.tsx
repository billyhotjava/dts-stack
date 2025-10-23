import { useCallback, useEffect, useMemo, useState, type ChangeEvent } from "react";
import { Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import dayjs from "dayjs";
import { Calendar as CalendarIcon, Clock3 } from "lucide-react";
import { toast } from "sonner";
import { AuditLogService } from "@/api/services/auditLogService";
import type { AuditLog, AuditLogPageResponse, AuditLogDetail } from "#/entity";
import { GLOBAL_CONFIG } from "@/global-config";
import userStore from "@/store/userStore";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Calendar } from "@/ui/calendar";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Popover, PopoverContent, PopoverTrigger } from "@/ui/popover";
import { Text } from "@/ui/typography";
import { cn } from "@/utils";

interface FilterState {
	from?: string;
	to?: string;
	sourceSystem?: string;
	module?: string;
	actor?: string;
	resource?: string;
	clientIp?: string;
	action?: string;
}

// Removed unused types (AuditModuleCatalog, AuditCategoryItem) to satisfy strict TS settings

const ADMIN_LABELS: Record<string, string> = {
	sysadmin: "系统管理员",
	authadmin: "授权管理员",
	auditadmin: "安全审计员",
	opadmin: "运维管理员",
};

const DEFAULT_PAGE_SIZE = 10;

export default function AuditCenterView() {
	const [filters, setFilters] = useState<FilterState>({});
	const [logs, setLogs] = useState<AuditLog[]>([]);
	const [loading, setLoading] = useState(false);
	const [page, setPage] = useState(0);
	const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
	const [totalElements, setTotalElements] = useState(0);
	const [exporting, setExporting] = useState(false);
	const [rowDetails, setRowDetails] = useState<Record<number, AuditLogDetail | null>>({});
	const [rowLoading, setRowLoading] = useState<Record<number, boolean>>({});
	const [moduleOptions, setModuleOptions] = useState<Array<{ value: string; label: string }>>([
		{ value: "", label: "全部模块" },
	]);

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
                // totalPages is not used in UI; skip storing
                // Note: 功能模块下拉仅使用审计目录（/audit-logs/modules），不再从日志内容动态补全，
                // 以确保不同角色（含 auditadmin）看到一致的大类选项。
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

	useEffect(() => {
		AuditLogService.getAuditModules()
			.then((modules) => {
				const mapped = modules.map((item) => {
					const label = (item.title && item.title.trim()) || (item.key && item.key.trim()) || "-";
					const value = (item.key && item.key.trim()) || (item.title && item.title.trim()) || "";
					return { value, label };
				});
				setModuleOptions([{ value: "", label: "全部模块" }, ...mapped]);
			})
			.catch((error) => {
				console.error("Failed to load module catalog", error);
				toast.error("加载模块字典失败");
			});
	}, []);

	const handleRefresh = useCallback(() => {
		loadLogs(0, size);
	}, [loadLogs, size]);

	const handleExport = useCallback(async () => {
		try {
			setExporting(true);
			const params = buildQuery(filters);
			const query = new URLSearchParams(params).toString();
			const exportUrl = buildExportUrl(query);
			const token = resolveAccessToken();
			const response = await fetch(exportUrl, {
				headers: token ? { Authorization: `Bearer ${token}` } : undefined,
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
	}, [filters]);

    const handleExpandRow = useCallback(
        async (expanded: boolean, record: AuditLog) => {
            if (!expanded) return;
            const id = record.id;
            if (rowDetails[id] !== undefined) return; // already fetched
            setRowLoading((prev) => ({ ...prev, [id]: true }));
            try {
                const detail = await AuditLogService.getAuditLogById(id);
                setRowDetails((prev) => ({ ...prev, [id]: detail }));
            } catch (e) {
                console.error("加载详情失败", e);
                setRowDetails((prev) => ({ ...prev, [id]: null }));
            } finally {
                setRowLoading((prev) => ({ ...prev, [id]: false }));
            }
        },
        [rowDetails]
    );

    // 使用 Antd Table 的分页展示，无需单独显示分页文案

    const columns = useMemo<ColumnsType<AuditLog>>(
      () => [
        { title: "操作人", dataIndex: "operatorName", key: "operatorName", width: 140, render: (_: string, r) => r.operatorName || formatOperatorName(r.actor) },
        { title: "操作人编码", dataIndex: "actor", key: "actor", width: 140 },
        { title: "IP地址", dataIndex: "clientIp", key: "clientIp", width: 130, render: (v?: string) => v || "127.0.0.1" },
        { title: "操作时间", dataIndex: "occurredAt", key: "occurredAt", width: 180, render: (v: string) => <span className="text-sm">{formatDateTime(v)}</span> },
        { title: "模块名称", dataIndex: "sourceSystemText", key: "sourceSystemText", width: 120, render: (_: string, r) => r.sourceSystemText || r.sourceSystem || "-" },
        { title: "操作内容", dataIndex: "operationContent", key: "operationContent", width: 200, render: (_: string, r) => r.operationContent || r.summary || r.action },
        { title: "操作类型", dataIndex: "operationType", key: "operationType", width: 110, render: (_: string, r) => r.operationType || "操作" },
        { title: "操作结果", dataIndex: "result", key: "result", width: 100, render: (_: string, r) => {
            const raw = (r.result || "").toUpperCase();
            const isFail = raw === "FAILED" || raw === "FAILURE";
            const label = r.resultText || (raw === "SUCCESS" ? "成功" : isFail ? "失败" : r.result || "-");
            return <Badge variant={isFail ? "destructive" : "secondary"}>{label}</Badge>;
        }},
        { title: "日志类型", dataIndex: "logTypeText", key: "logTypeText", width: 110, render: (_: string, r) => r.logTypeText || (r.eventClass && r.eventClass.toLowerCase() === "securityevent" ? "安全审计" : "操作审计") },
      ],
      []
    );

    return (
        <div className="mx-auto w-full max-w-[1400px] px-6 py-6 space-y-6">
            {/* 老页面布局样式：页眉 + 右侧操作区 */}
            <div className="flex flex-wrap items-center gap-3">
                <Text variant="body1" className="text-lg font-semibold">日志审计</Text>
                <div className="ml-auto flex items-center gap-2">
                    <Button variant="outline" onClick={handleRefresh} disabled={loading}>
                        {loading ? "刷新中..." : "刷新"}
                    </Button>
                    <Button onClick={handleExport} disabled={exporting || logs.length === 0}>
                        {exporting ? "正在导出..." : "导出日志"}
                    </Button>
                </div>
            </div>
            <Card>
                <CardHeader className="space-y-2">
                    <CardTitle>查询条件</CardTitle>
                    <Text variant="body3" className="text-muted-foreground">
                        支持按时间范围、来源系统、功能模块、操作人、目标位置与 IP 筛选审计记录。
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
							label="来源系统"
							value={filters.sourceSystem || ""}
							onChange={(value) => setFilters((prev) => ({ ...prev, sourceSystem: value || undefined }))}
                            options={[
                                { value: "", label: "全部来源" },
                                { value: "admin", label: "系统管理" },
                                { value: "platform", label: "业务管理" },
                            ]}
						/>
				{/* 功能模块筛选暂时隐藏，如需恢复请移除此段注释并同步暴露 UI */}
				{false && (
					<SelectField
						label="功能模块"
						value={filters.module || ""}
						onChange={(value) => {
							const trimmed = value.trim();
							setFilters((prev) => ({ ...prev, module: trimmed ? trimmed : undefined }));
						}}
						options={moduleOptions}
					/>
				)}
                            <InputField
                                label="操作者"
                                placeholder="如 系统管理员 或 sysadmin"
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
                            {/* 事件类型筛选已移除 */}
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
                <CardHeader className="pb-2">
                    <CardTitle>日志记录</CardTitle>
                </CardHeader>
                <CardContent>
                    <Table<AuditLog>
                        rowKey="id"
                        columns={columns}
                        dataSource={logs}
                        loading={loading}
                        expandable={{
                            columnWidth: 48,
                            expandRowByClick: true,
                            onExpand: handleExpandRow,
                            expandedRowRender: (record) => {
                                const loadingRow = rowLoading[record.id];
                                const detail = rowDetails[record.id];
                                if (loadingRow) {
                                    return <div className="text-xs text-muted-foreground">加载详情...</div>;
                                }
                                const content = detail?.details ?? null;
                                if (!content) {
                                    return <div className="text-xs text-muted-foreground">无详情</div>;
                                }
                                return (
                                    <pre className="text-xs whitespace-pre-wrap break-words">
                                        {JSON.stringify(content, null, 2)}
                                    </pre>
                                );
                            },
                        }}
                        pagination={{
                            current: page + 1,
                            pageSize: size,
                            total: totalElements,
                            showSizeChanger: true,
                            pageSizeOptions: [10, 20, 50, 100],
                            showQuickJumper: true,
                            showTotal: (total, range) => `第 ${range[0]}-${range[1]} 条，共 ${total} 条`,
                            onChange: (current, pageSize) => {
                                setPage(current - 1);
                                setSize(pageSize);
                            },
                        }}
                        size="small"
                        tableLayout="fixed"
                        scroll={{ x: 1200 }}
                    />
                </CardContent>
            </Card>
		</div>
	);
}

// mergeModuleOptionLists removed (unused)

function buildQuery(filters: FilterState): Record<string, string> {
	const params: Record<string, string> = {};
	if (filters.from) {
		params.from = new Date(filters.from).toISOString();
	}
	if (filters.to) {
		params.to = new Date(filters.to).toISOString();
	}
	if (filters.sourceSystem) {
		params.sourceSystem = filters.sourceSystem;
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

function resolveAccessToken(): string {
	const { userToken } = userStore.getState();
	const raw = userToken?.accessToken;
	if (!raw) {
		return "";
	}
	const trimmed = String(raw).trim();
	if (!trimmed) {
		return "";
	}
	return trimmed.startsWith("Bearer ") ? trimmed.slice(7).trim() : trimmed;
}

function buildExportUrl(query: string): string {
	const base = GLOBAL_CONFIG.apiBaseUrl?.trim() || "/api";
	const normalizedBase = base.endsWith("/") ? base.slice(0, -1) : base;
	return `${normalizedBase}/audit-logs/export${query ? `?${query}` : ""}`;
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
    // 其余用户统一归类为“业务系统”操作人
    return `业务系统（${value}）`;
}

interface FieldProps {
	label: string;
	value?: string;
	onChange: (value: string | undefined) => void;
	placeholder?: string;
}

function DateTimeField({ label, value, onChange, placeholder }: FieldProps) {
	const [open, setOpen] = useState(false);
	const parsed = value ? dayjs(value) : undefined;
	const displayText = parsed ? parsed.format("YYYY-MM-DD HH:mm") : placeholder ?? "yyyy-mm-dd --:--";

	const handleDateSelect = useCallback(
		(selectedDate?: Date) => {
			if (!selectedDate) {
				onChange(undefined);
				return;
			}
			const base = dayjs(selectedDate);
			const next = base
				.hour(parsed?.hour() ?? 0)
				.minute(parsed?.minute() ?? 0)
				.second(parsed?.second() ?? 0)
				.millisecond(0);
			onChange(next.format("YYYY-MM-DDTHH:mm"));
		},
		[onChange, parsed],
	);

	const handleTimeChange = useCallback(
		(event: ChangeEvent<HTMLInputElement>) => {
			const timeValue = event.target.value;
			if (!timeValue) {
				if (parsed) {
					const cleared = parsed.hour(0).minute(0).second(0).millisecond(0);
					onChange(cleared.format("YYYY-MM-DDTHH:mm"));
				}
				return;
			}
			const [hour, minute] = timeValue.split(":").map((item) => Number.parseInt(item, 10));
			const base = parsed ?? dayjs();
			const next = base.hour(Number.isFinite(hour) ? hour : 0).minute(Number.isFinite(minute) ? minute : 0).second(0).millisecond(0);
			onChange(next.format("YYYY-MM-DDTHH:mm"));
		},
		[onChange, parsed],
	);

	return (
		<div className="space-y-2">
			<Text variant="body3" className="text-muted-foreground">
				{label}
			</Text>
			<Popover open={open} onOpenChange={setOpen}>
				<PopoverTrigger asChild>
					<Button
						variant="outline"
						className={cn(
							"w-full justify-start text-left font-normal",
							!parsed && "text-muted-foreground",
						)}
					>
						<CalendarIcon className="mr-2 size-4" />
						{displayText}
					</Button>
				</PopoverTrigger>
				<PopoverContent className="w-72 space-y-4" align="start">
					<Calendar mode="single" selected={parsed?.toDate()} onSelect={handleDateSelect} initialFocus />
					<div className="flex items-center gap-2">
						<div className="relative w-full">
							<Input type="time" value={parsed ? parsed.format("HH:mm") : ""} onChange={handleTimeChange} className="pl-9" />
							<Clock3 className="pointer-events-none absolute left-2 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
						</div>
						<Button
							variant="ghost"
							size="sm"
							onClick={() => {
								onChange(undefined);
								setOpen(false);
							}}
						>
							清除
						</Button>
					</div>
				</PopoverContent>
			</Popover>
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

interface SelectOption { value: string; label: string }
interface SelectProps {
	label: string;
	value: string;
	onChange: (value: string) => void;
	options: SelectOption[];
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
					<option key={opt.value || "all"} value={opt.value}>
						{opt.label}
					</option>
				))}
			</select>
		</div>
	);
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
