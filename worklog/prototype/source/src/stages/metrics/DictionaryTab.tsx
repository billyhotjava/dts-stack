import { Tag } from "antd";
import { useEffect, useState } from "react";
import { unwrap } from "@/mock/client";
import { metricService } from "@/mock/services/metricService";
import type { GlossaryTerm, ReferenceCode } from "@/types/metric";
import { CompactTable } from "@/ui/components";
import type { CompactColumn } from "@/ui/components";

const TERM_COLUMNS: CompactColumn<GlossaryTerm>[] = [
	{ key: "term", title: "术语", dataIndex: "term", width: 140, render: (v) => <span style={{ fontWeight: 600 }}>{v as string}</span> },
	{ key: "definition", title: "定义", dataIndex: "definition" },
];
const CODE_COLUMNS: CompactColumn<ReferenceCode>[] = [
	{ key: "codeType", title: "码表", dataIndex: "codeType", width: 140, render: (v) => <Tag>{v as string}</Tag> },
	{ key: "code", title: "码值", dataIndex: "code", width: 100, render: (v) => <span style={{ fontFamily: "var(--font-mono, monospace)" }}>{v as string}</span> },
	{ key: "name", title: "含义", dataIndex: "name" },
];

export function DictionaryTab({ departmentId }: { departmentId: string }) {
	const [terms, setTerms] = useState<GlossaryTerm[]>([]);
	const [codes, setCodes] = useState<ReferenceCode[]>([]);

	useEffect(() => {
		let alive = true;
		void Promise.all([metricService.listGlossary(departmentId), metricService.listReferenceCodes()]).then(([g, c]) => {
			if (!alive) return;
			setTerms(unwrap(g));
			setCodes(unwrap(c));
		});
		return () => {
			alive = false;
		};
	}, [departmentId]);

	return (
		<div style={{ display: "flex", flexDirection: "column", gap: 24 }}>
			<div>
				<div style={{ fontWeight: 650, marginBottom: 8 }}>业务术语</div>
				<CompactTable<GlossaryTerm> columns={TERM_COLUMNS} data={terms} rowKey="id" />
			</div>
			<div>
				<div style={{ fontWeight: 650, marginBottom: 8 }}>参考码（码表）</div>
				<CompactTable<ReferenceCode> columns={CODE_COLUMNS} data={codes} rowKey="id" />
			</div>
		</div>
	);
}
