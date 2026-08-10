import { Search } from "lucide-react";
import { useState } from "react";
import type { CatalogDomain } from "@/api/services/catalogDomainService";
import { Button } from "./PrototypePrimitives";
import type { ModelCreateKind } from "./services/modelWorkbenchService";

const LOGICAL_CREATE_ENTRIES: Array<{
	kind: ModelCreateKind | null;
	label: string;
	disabled: boolean;
	title?: string;
}> = [
	{
		kind: null,
		label: "创建贴源表（尚未接入）",
		disabled: true,
		title: "当前 ModelSpec 契约不拥有 ODS/STG 贴源对象",
	},
	{ kind: "dimension-table", label: "创建维度表", disabled: false },
	{ kind: "fact", label: "创建明细表", disabled: false },
	{ kind: "summary", label: "创建汇总表", disabled: false },
	{ kind: "application", label: "创建应用表", disabled: false },
];

export type ModelWorkbenchCreateMenuProps = {
	categoryRoots: CatalogDomain[];
	saving: boolean;
	onCreate: (kind: ModelCreateKind, categoryId: string) => void;
};

export function ModelWorkbenchCreateMenu({ categoryRoots, saving, onCreate }: ModelWorkbenchCreateMenuProps) {
	const [category, setCategory] = useState("");
	const [query, setQuery] = useState("");
	const normalized = query.trim().toLowerCase();
	const visible = (label: string) => !normalized || label.toLowerCase().includes(normalized);

	return (
		<div className="dmx-create-menu">
			<div className="dmx-create-menu-head">
				<select aria-label="请选择业务分类" onChange={(event) => setCategory(event.target.value)} value={category}>
					<option value="">请选择业务分类</option>
					{categoryRoots.map((item) => (
						<option key={item.id} value={item.id}>
							{item.name}
						</option>
					))}
				</select>
				<div>
					<Search size={13} />
					<input
						aria-label="搜索模型类型"
						onChange={(event) => setQuery(event.target.value)}
						placeholder="搜索"
						value={query}
					/>
				</div>
			</div>
			<strong>概念模型</strong>
			{visible("创建维度") ? (
				<Button disabled={saving} onClick={() => onCreate("dimension", category)}>
					创建维度
				</Button>
			) : null}
			<strong>逻辑模型</strong>
			{LOGICAL_CREATE_ENTRIES.filter((entry) => visible(entry.label)).map((entry) =>
				entry.disabled ? (
					<Button disabled key={entry.label} title={entry.title}>
						{entry.label}
					</Button>
				) : (
					<Button disabled={saving} key={entry.label} onClick={() => entry.kind && onCreate(entry.kind, category)}>
						{entry.label}
					</Button>
				),
			)}
		</div>
	);
}
