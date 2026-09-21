import { lazy, Suspense, useState } from "react";
import type { WarehousePlanSourceBindingView } from "@/api/warehousePlanApi";
const ModelSourceInventoryDialog = lazy(() => import("./ModelSourceInventoryDialog").then(module => ({ default: module.ModelSourceInventoryDialog })));
import { Button } from "./PrototypePrimitives";
export function ReverseImportSourceRegistration({
	planId,
	onSourcesChanged,
}: {
	planId: string;
	onSourcesChanged: (sources: WarehousePlanSourceBindingView[], planId: string) => void;
}) {
	const [open, setOpen] = useState(false);
	return (
		<section className="dmx-import-source-registration">
			<strong>ODS 来源登记与确认</strong>
			<p>直接从资产目录登记来源，无需先创建模型。登记后在下方将包内来源映射到对应物理表。</p>
			<Button disabled={!planId} onClick={() => setOpen(true)}>
				登记并确认来源
			</Button>
			{!planId ? <small>请先选择建模环境。</small> : null}
			{open ? (
				<Suspense fallback={<p>正在加载来源登记…</p>}><ModelSourceInventoryDialog
					key={planId}
					planId={planId}
					onClose={() => setOpen(false)}
					onSourcesChanged={onSourcesChanged}
				/></Suspense>
			) : null}
		</section>
	);
}
