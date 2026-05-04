// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import { useMemo, useState } from "react";
import { Button, Input, Modal, Tag } from "antd";
import { CompactTable } from "@/components/table";
import { SearchOutlined } from "@ant-design/icons";
import type { ColumnsType } from "antd/es/table";
import type { CardListItem } from "../../api/analyticsApi";

const DISPLAY_LABELS: Record<string, string> = {
	table: "表格", line: "折线", bar: "柱状", pie: "饼图", area: "面积",
	scalar: "数字", row: "横柱", combo: "组合", funnel: "漏斗", scatter: "散点",
};

interface CardPickerModalProps {
	open: boolean;
	onClose: () => void;
	onAdd: (cards: CardListItem[]) => void;
	allCards: CardListItem[];
	existingCardIds: Set<number>;
}

export function CardPickerModal({ open, onClose, onAdd, allCards, existingCardIds }: CardPickerModalProps) {
	const [search, setSearch] = useState("");
	const [selectedKeys, setSelectedKeys] = useState<number[]>([]);

	const available = useMemo(() => {
		const kw = search.trim().toLowerCase();
		return allCards
			.filter((c) => !c.archived && !existingCardIds.has(c.id))
			.filter((c) => !kw || (c.name ?? "").toLowerCase().includes(kw) || (c.description ?? "").toLowerCase().includes(kw));
	}, [allCards, existingCardIds, search]);

	const handleOk = () => {
		const picked = allCards.filter((c) => selectedKeys.includes(c.id));
		if (picked.length > 0) {
			onAdd(picked);
		}
		setSelectedKeys([]);
		setSearch("");
		onClose();
	};

	const handleCancel = () => {
		setSelectedKeys([]);
		setSearch("");
		onClose();
	};

	const columns: ColumnsType<CardListItem> = [
		{
			title: "名称",
			dataIndex: "name",
			key: "name",
			ellipsis: true,
			render: (name: string) => <span className="font-medium">{name || "-"}</span>,
		},
		{
			title: "类型",
			dataIndex: "display",
			key: "display",
			width: 80,
			render: (display: string) => <Tag>{DISPLAY_LABELS[display] ?? display ?? "-"}</Tag>,
		},
		{
			title: "描述",
			dataIndex: "description",
			key: "description",
			ellipsis: true,
			render: (desc: string | null) => <span className="text-text-secondary text-xs">{desc || "-"}</span>,
		},
	];

	return (
		<Modal
			title="添加分析卡片"
			open={open}
			onOk={handleOk}
			onCancel={handleCancel}
			okText={`添加 ${selectedKeys.length > 0 ? `(${selectedKeys.length})` : ""}`}
			okButtonProps={{ disabled: selectedKeys.length === 0 }}
			cancelText="取消"
			width={720}
			destroyOnClose
		>
			<div className="mb-3">
				<Input
					placeholder="搜索卡片名称或描述..."
					prefix={<SearchOutlined />}
					value={search}
					onChange={(e) => setSearch(e.target.value)}
					allowClear
				/>
			</div>
			<CompactTable<CardListItem>
				columns={columns}
				dataSource={available}
				rowKey={(r) => r.id}
				size="small"
				pagination={available.length > 8 ? { pageSize: 8 } : false}
				rowSelection={{
					type: "checkbox",
					selectedRowKeys: selectedKeys,
					onChange: (keys) => setSelectedKeys(keys as number[]),
				}}
				locale={{ emptyText: search ? "未找到匹配的卡片" : "没有可用的卡片" }}
			/>
		</Modal>
	);
}
