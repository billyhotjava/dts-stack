import { TreeSelect } from "antd";
import { useEffect, useMemo, useState } from "react";
import apiClient from "@/api/apiClient";
import { searchUsers } from "@/api/services/userDirectoryService";
import { useUserInfo } from "@/store/userStore";
import { useWorkbenchRole } from "../hooks/useWorkbenchRole";

/** P0-review HIGH: `onChange` is optional. The non-INST_LEADER render path
 *  is read-only, and callers should not supply an onChange there to keep the
 *  read-only intent explicit at the type level. */
export interface DeptSelectProps {
	value: string | "ALL" | null;
	onChange?: (value: string | "ALL") => void;
}

interface OrgNode {
	id?: number | string;
	name?: string;
	label?: string;
	title?: string;
	code?: string;
	deptCode?: string;
	dept_code?: string;
	deptName?: string;
	dept_name?: string;
	orgName?: string;
	org_name?: string;
	departmentName?: string;
	department_name?: string;
	value?: string;
	key?: string;
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

function readString(...values: unknown[]): string {
	for (const value of values) {
		if (typeof value === "string" && value.trim()) return value.trim();
		if (typeof value === "number" && Number.isFinite(value)) return String(value);
	}
	return "";
}

function resolveOrgCode(node: OrgNode): string {
	return readString(node.deptCode, node.dept_code, node.code, node.value, node.key, node.id);
}

function resolveOrgName(node: OrgNode, fallbackCode: string): string {
	return (
		readString(
			node.name,
			node.deptName,
			node.dept_name,
			node.departmentName,
			node.department_name,
			node.orgName,
			node.org_name,
			node.label,
			node.title,
		) || fallbackCode
	);
}

function mapOrgToDept(nodes: OrgNode[] | undefined): DeptTreeNode[] {
	if (!Array.isArray(nodes)) return [];
	const out: DeptTreeNode[] = [];
	for (const raw of nodes) {
		const code = resolveOrgCode(raw);
		const name = resolveOrgName(raw, code);
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
		if (Array.isArray(obj.records)) return obj.records as OrgNode[];
		if (Array.isArray(obj.items)) return obj.items as OrgNode[];
		if (Array.isArray(obj.children)) return obj.children as OrgNode[];
		if (obj.data && typeof obj.data === "object") {
			const data = obj.data as Record<string, unknown>;
			if (Array.isArray(data.records)) return data.records as OrgNode[];
			if (Array.isArray(data.items)) return data.items as OrgNode[];
			if (Array.isArray(data.children)) return data.children as OrgNode[];
		}
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
		if (n.children?.length) {
			return { ...base, children: toTreeSelectData(n.children) };
		}
		return base;
	});
}

function flattenDeptNames(nodes: DeptTreeNode[], out: Map<string, string>): void {
	for (const n of nodes) {
		if (n.code && n.name && !out.has(n.code)) out.set(n.code, n.name);
		if (n.children?.length) flattenDeptNames(n.children, out);
	}
}

export function DeptSelect({ value, onChange }: DeptSelectProps) {
	const { isInstLeader, deptCode, deptName } = useWorkbenchRole();
	const userInfo = useUserInfo();
	const [tree, setTree] = useState<DeptTreeNode[]>([]);
	const [directoryDeptName, setDirectoryDeptName] = useState<string | null>(null);
	const [apiFailed, setApiFailed] = useState(false);
	const [loading, setLoading] = useState(false);
	const flatDeptNames = useMemo(() => {
		const flat = new Map<string, string>();
		flattenDeptNames(tree, flat);
		return flat;
	}, [tree]);
	const userKeyword = useMemo(() => {
		if (!userInfo || typeof userInfo !== "object") return "";
		const info = userInfo as Record<string, unknown>;
		return readString(info.username, info.login, info.email, info.id);
	}, [userInfo]);

	useEffect(() => {
		// Sprint-17 hotfix: also fetch the org tree for non-INST_LEADER users when their
		// session payload didn't include `deptName`, so the locked label can still resolve
		// to a human-readable name instead of falling back to the bare code.
		if (!isInstLeader && deptName) return;
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
	}, [isInstLeader, deptName]);

	useEffect(() => {
		if (isInstLeader || deptName || !deptCode || !userKeyword) {
			setDirectoryDeptName(null);
			return;
		}
		let cancelled = false;
		searchUsers(userKeyword)
			.then((users) => {
				if (cancelled) return;
				const normalizedKeyword = userKeyword.toLowerCase();
				const matched =
					users.find((user) => String(user.username || "").toLowerCase() === normalizedKeyword) ??
					users.find((user) => user.deptCode === deptCode) ??
					(users.length === 1 ? users[0] : undefined);
				setDirectoryDeptName(matched?.deptName?.trim() || null);
			})
			.catch(() => {
				if (!cancelled) setDirectoryDeptName(null);
			});
		return () => {
			cancelled = true;
		};
	}, [isInstLeader, deptName, deptCode, userKeyword]);

	if (!isInstLeader) {
		// Prefer session-supplied deptName; otherwise look it up from user/org directory (best-effort).
		let resolvedName = deptName ?? directoryDeptName;
		if (!resolvedName && deptCode) {
			resolvedName = flatDeptNames.get(deptCode) ?? null;
		}
		const label = resolvedName ?? (loading && deptCode ? "加载中..." : (deptCode ?? "（未绑定）"));
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
				title={deptCode ?? undefined}
			>
				本部门：{label}
			</div>
		);
	}

	const selfName = deptName ?? (deptCode ? flatDeptNames.get(deptCode) : undefined);
	const selfLabel = selfName && deptCode ? `本部门：${selfName}` : `本部门：${deptCode ?? ""}`;
	const treeData: TreeSelectOption[] = apiFailed
		? [{ value: "ALL", title: "全所（默认）" }, ...(deptCode ? [{ value: deptCode, title: selfLabel }] : [])]
		: [{ value: "ALL", title: "全所（默认）" }, ...toTreeSelectData(tree)];

	return (
		<TreeSelect
			value={value ?? "ALL"}
			onChange={(v) => onChange?.(typeof v === "string" && v ? v : "ALL")}
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
