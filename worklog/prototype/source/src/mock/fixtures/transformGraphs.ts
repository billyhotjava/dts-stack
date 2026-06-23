import type { TransformGraphDTO } from "@/types/transform";

/**
 * 种子转换图。
 * "销售准备"(ps-sales-prep)：PLM.订单 → 去重 → 连接 ← ERP.客户 → ODS.宽表。
 * 对齐设计文档 §7 画布草图。其余空间初始为空图。
 */
export const SEED_TRANSFORM_GRAPHS: TransformGraphDTO[] = [
	{
		projectSpaceId: "ps-sales-prep",
		nodes: [
			{ id: "n-plm", kind: "source", label: "PLM.订单", sub: "PLM 生产系统", rowCount: 32000, status: "ok", x: 40, y: 40 },
			{ id: "n-dedup", kind: "clean", label: "去重", sub: "主键 order_id · 保留最新", rowCount: 31800, status: "ok", x: 300, y: 40 },
			{ id: "n-erp", kind: "source", label: "ERP.客户", sub: "ERP 企业系统", rowCount: 5200, status: "ok", x: 40, y: 220 },
			{ id: "n-join", kind: "join", label: "连接", sub: "order.cust_id = cust.id", status: "idle", x: 560, y: 130 },
			{ id: "n-ods", kind: "output", label: "ODS.宽表", sub: "ods_sales_wide", status: "idle", x: 820, y: 130 },
		],
		edges: [
			{ id: "e1", source: "n-plm", target: "n-dedup" },
			{ id: "e2", source: "n-dedup", target: "n-join" },
			{ id: "e3", source: "n-erp", target: "n-join" },
			{ id: "e4", source: "n-join", target: "n-ods" },
		],
	},
];
