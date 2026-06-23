import { Tabs, Tag } from "antd";
import { useEffect, useState } from "react";
import { unwrap } from "@/mock/client";
import { platformService } from "@/mock/services/platformService";
import { useDepartmentStore } from "@/store/departmentStore";
import type { AccessToken, ApiService, BiLink } from "@/types/platform";
import { CompactTable, SectionTitle, StatusDot } from "@/ui/components";
import type { CompactColumn } from "@/ui/components";

export function ServeStage() {
	const depts = useDepartmentStore((s) => s.departments);
	const deptName = (id: string) => depts.find((d) => d.id === id)?.name ?? id;
	const [apis, setApis] = useState<ApiService[]>([]);
	const [tokens, setTokens] = useState<AccessToken[]>([]);
	const [biLinks, setBiLinks] = useState<BiLink[]>([]);

	useEffect(() => {
		void platformService.apiServices().then((r) => setApis(unwrap(r)));
		void platformService.tokens().then((r) => setTokens(unwrap(r)));
		void platformService.biLinks().then((r) => setBiLinks(unwrap(r)));
	}, []);

	const apiCols: CompactColumn<ApiService>[] = [
		{ key: "name", title: "服务", render: (_v, r) => <span style={{ fontWeight: 600 }}>{r.name}</span> },
		{ key: "method", title: "方法", width: 70, render: (_v, r) => <Tag color={r.method === "GET" ? "green" : "blue"}>{r.method}</Tag> },
		{ key: "path", title: "路径", render: (_v, r) => <span style={{ fontFamily: "var(--font-mono, monospace)", fontSize: 12 }}>{r.path}</span> },
		{ key: "dept", title: "归口部门", width: 100, render: (_v, r) => deptName(r.departmentId) },
		{ key: "status", title: "状态", width: 90, render: (_v, r) => <StatusDot tone={r.status === "online" ? "success" : "muted"} label={r.status === "online" ? "在线" : "下线"} /> },
		{ key: "calls", title: "调用量", width: 100, align: "right", render: (_v, r) => r.calls.toLocaleString() },
	];
	const tokenCols: CompactColumn<AccessToken>[] = [
		{ key: "name", title: "令牌", render: (_v, r) => <span style={{ fontWeight: 600 }}>{r.name}</span> },
		{ key: "scope", title: "作用域", render: (_v, r) => <span style={{ fontFamily: "var(--font-mono, monospace)", fontSize: 12 }}>{r.scope}</span> },
		{ key: "createdAt", title: "创建", width: 110, dataIndex: "createdAt" },
		{ key: "lastUsed", title: "最近使用", width: 110, render: (_v, r) => r.lastUsed ?? "—" },
		{ key: "status", title: "状态", width: 90, render: (_v, r) => <StatusDot tone={r.status === "active" ? "success" : "error"} label={r.status === "active" ? "有效" : "已吊销"} /> },
	];
	const biCols: CompactColumn<BiLink>[] = [
		{ key: "name", title: "看板", render: (_v, r) => <span style={{ fontWeight: 600 }}>{r.name}</span> },
		{ key: "tool", title: "工具", width: 110, render: (_v, r) => <Tag>{r.tool}</Tag> },
		{ key: "url", title: "链接", render: (_v, r) => <span style={{ fontFamily: "var(--font-mono, monospace)", fontSize: 12 }}>{r.url}</span> },
		{ key: "dept", title: "归口部门", width: 100, render: (_v, r) => deptName(r.departmentId) },
	];

	return (
		<div style={{ maxWidth: 1080, margin: "0 auto" }}>
			<SectionTitle kicker="平台 · 旁路" title="数据服务" desc="把资产与指标对外提供为可消费的服务（平台级，跨部门）。" />
			<Tabs
				defaultActiveKey="api"
				items={[
					{ key: "api", label: "API 服务", children: <CompactTable<ApiService> columns={apiCols} data={apis} rowKey="id" /> },
					{ key: "token", label: "访问令牌", children: <CompactTable<AccessToken> columns={tokenCols} data={tokens} rowKey="id" /> },
					{ key: "bi", label: "BI 链接", children: <CompactTable<BiLink> columns={biCols} data={biLinks} rowKey="id" /> },
				]}
			/>
		</div>
	);
}
