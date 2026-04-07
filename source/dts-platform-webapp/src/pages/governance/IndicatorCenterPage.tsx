import { Suspense, lazy, useEffect, useState } from "react";
import { Layout, Spin, Tree } from "antd";
import type { DataNode } from "antd/es/tree";
import { useSearchParams } from "react-router";
import { getDomainTree } from "@/api/platformApi";

const IndicatorsPage = lazy(() => import("./IndicatorsPage"));

const { Sider, Content } = Layout;

type DomainNode = {
	id?: string;
	name?: string;
	code?: string;
	children?: DomainNode[];
};

function buildTreeData(nodes: DomainNode[]): DataNode[] {
	return nodes.map((n, i) => ({
		key: n.code || `node-${i}`,
		title: n.name || n.code || "未命名",
		children: n.children?.length ? buildTreeData(n.children) : undefined,
	}));
}

export default function IndicatorCenterPage() {
	const [searchParams, setSearchParams] = useSearchParams();
	const [treeData, setTreeData] = useState<DataNode[]>([]);
	const [treeLoading, setTreeLoading] = useState(true);
	const [treeError, setTreeError] = useState(false);

	const activeDomain = searchParams.get("domain") || "";

	useEffect(() => {
		const controller = new AbortController();
		loadTree(controller.signal);
		return () => controller.abort();
	}, []);

	const loadTree = (signal?: AbortSignal) => {
		setTreeLoading(true);
		setTreeError(false);
		getDomainTree()
			.then((res: any) => {
				if (signal?.aborted) return;
				const nodes: DomainNode[] = Array.isArray(res) ? res : res?.data ?? [];
				const allNode: DataNode = { key: "", title: "全部" };
				setTreeData([allNode, ...buildTreeData(nodes)]);
			})
			.catch(() => { if (!signal?.aborted) setTreeError(true); })
			.finally(() => { if (!signal?.aborted) setTreeLoading(false); });
	};

	const handleSelect = (selectedKeys: React.Key[]) => {
		const code = String(selectedKeys[0] ?? "");
		const next = new URLSearchParams(searchParams);
		if (code) {
			next.set("domain", code);
		} else {
			next.delete("domain");
		}
		setSearchParams(next, { replace: true });
	};

	return (
		<Layout style={{ minHeight: "100%" }}>
			<Sider
				width={240}
				style={{
					background: "#fff",
					borderRight: "1px solid #f0f0f0",
					overflowY: "auto",
					height: "calc(100vh - 64px)",
				}}
			>
				<div className="px-3 py-3 text-sm font-semibold text-slate-700">主题域导航</div>
				{treeLoading ? (
					<div className="flex justify-center py-8"><Spin size="small" /></div>
				) : treeError ? (
					<div className="px-3 py-4 text-xs text-slate-400">
						加载失败，<a onClick={() => loadTree()} className="text-blue-500 cursor-pointer">点击重试</a>
					</div>
				) : (
					<Tree
						treeData={treeData}
						selectedKeys={[activeDomain]}
						onSelect={handleSelect}
						defaultExpandAll
						blockNode
						style={{ padding: "0 4px" }}
					/>
				)}
			</Sider>
			<Content style={{ padding: 0 }}>
				<Suspense fallback={<div className="flex h-64 items-center justify-center"><Spin /></div>}>
					<IndicatorsPage />
				</Suspense>
			</Content>
		</Layout>
	);
}
