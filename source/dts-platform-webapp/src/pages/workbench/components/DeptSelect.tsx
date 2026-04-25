import { TreeSelect } from "antd";
import { useEffect, useState } from "react";
import apiClient from "@/api/apiClient";
import { useWorkbenchRole } from "../hooks/useWorkbenchRole";

export interface DeptSelectProps {
	value: string | "ALL" | null;
	onChange: (value: string | "ALL") => void;
}

interface OrgNode {
	id?: number | string;
	name?: string;
	deptCode?: string;
	parentId?: number | string;
	children?: OrgNode[];
	isRoot?: boolean;
}

interface DeptTreeNode {
	code: string;
	name: string;
	children?: DeptTreeNode[];
}

interface TreeSelectOption {
	value: string;
	title: string;
	children?: TreeSelectOption[];
}

function resolveOrgCode(node: OrgNode): string {
	if (typeof node.deptCode === "string" && node.deptCode.trim()) return node.deptCode.trim();
	if (node.id != null) return String(node.id);
	return "";
}

function mapOrgToDept(nodes: OrgNode[] | undefined): DeptTreeNode[] {
	if (!Array.isArray(nodes)) return [];
	const out: DeptTreeNode[] = [];
	for (const raw of nodes) {
		const code = resolveOrgCode(raw);
		const name = typeof raw.name === "string" && raw.name.trim() ? raw.name.trim() : code;
		if (!code) continue;
		const children = mapOrgToDept(raw.children);
		out.push(children.length ? { code, name, children } : { code, name });
	}
	return out;
}

function extractOrgList(raw: unknown): OrgNode[] {
	if (Array.isArray(raw)) return raw as OrgNode[];
	if (raw && typeof raw === "object") {
		const obj = raw as Record<string, unknown>;
		if (Array.isArray(obj.data)) return obj.data as OrgNode[];
	}
	return [];
}

async function fetchOrgTree(): Promise<DeptTreeNode[]> {
	const raw = await apiClient.get<unknown>({ url: "/directory/orgs", withCredentials: true });
	return mapOrgToDept(extractOrgList(raw));
}

function toTreeSelectData(nodes: DeptTreeNode[]): TreeSelectOption[] {
	return nodes.map((n) => {
		const base: TreeSelectOption = { value: n.code, title: n.name };
		if (n.children && n.children.length) {
			return { ...base, children: toTreeSelectData(n.children) };
		}
		return base;
	});
}

export function DeptSelect({ value, onChange }: DeptSelectProps) {
	const { isInstLeader, deptCode } = useWorkbenchRole();
	const [tree, setTree] = useState<DeptTreeNode[]>([]);
	const [apiFailed, setApiFailed] = useState(false);
	const [loading, setLoading] = useState(false);

	useEffect(() => {
		if (!isInstLeader) return;
		let cancelled = false;
		setLoading(true);
		fetchOrgTree()
			.then((list) => {
				if (cancelled) return;
				setTree(list);
				setApiFailed(list.length === 0);
			})
			.catch(() => {
				if (cancelled) return;
				setApiFailed(true);
				setTree([]);
			})
			.finally(() => {
				if (cancelled) return;
				setLoading(false);
			});
		return () => {
			cancelled = true;
		};
	}, [isInstLeader]);

	if (!isInstLeader) {
		return (
			<div
				data-testid="dept-select-locked"
				style={{
					padding: "4px 11px",
					background: "#f5f5f5",
					borderRadius: 6,
					color: "#595959",
					minHeight: 32,
					display: "inline-flex",
					alignItems: "center",
				}}
			>
				本部门：{deptCode ?? "（未绑定）"}
			</div>
		);
	}

	const treeData: TreeSelectOption[] = apiFailed
		? [
				{ value: "ALL", title: "全所（默认）" },
				...(deptCode ? [{ value: deptCode, title: `本部门：${deptCode}` }] : []),
			]
		: [{ value: "ALL", title: "全所（默认）" }, ...toTreeSelectData(tree)];

	return (
		<TreeSelect
			value={value ?? "ALL"}
			onChange={(v) => onChange(typeof v === "string" && v ? v : "ALL")}
			treeData={treeData}
			loading={loading}
			style={{ minWidth: 220 }}
			treeDefaultExpandAll
			showSearch
			filterTreeNode={(input, node) =>
				String(node?.title ?? "")
					.toLowerCase()
					.includes(input.toLowerCase())
			}
		/>
	);
}

export default DeptSelect;
