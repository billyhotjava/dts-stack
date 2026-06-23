import { SearchOutlined } from "@ant-design/icons";
import { Input, Modal, Tag } from "antd";
import type { InputRef } from "antd";
import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { unwrap } from "@/mock/client";
import { searchService } from "@/mock/services/searchService";
import type { SearchHit, SearchHitType } from "@/mock/services/searchService";
import { useDepartmentStore } from "@/store/departmentStore";

const TYPE_COLOR: Record<SearchHitType, string> = { 部门: "purple", 数据集: "green", 指标: "blue", 数据源: "gold", API: "geekblue" };

/** 全局搜索命令面板（⌘K / Ctrl+K）。跨部门聚合，选中后切上下文并跳转。 */
export function CommandPalette() {
	const [open, setOpen] = useState(false);
	const [keyword, setKeyword] = useState("");
	const [hits, setHits] = useState<SearchHit[]>([]);
	const [active, setActive] = useState(0);
	const inputRef = useRef<InputRef>(null);
	const navigate = useNavigate();
	const setDept = useDepartmentStore((s) => s.setCurrentDepartment);

	// 全局 ⌘K / Ctrl+K 唤起
	useEffect(() => {
		const onKey = (e: KeyboardEvent) => {
			if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
				e.preventDefault();
				setOpen(true);
			}
		};
		const onOpenEvent = () => setOpen(true);
		window.addEventListener("keydown", onKey);
		window.addEventListener("dts:open-search", onOpenEvent);
		return () => {
			window.removeEventListener("keydown", onKey);
			window.removeEventListener("dts:open-search", onOpenEvent);
		};
	}, []);

	useEffect(() => {
		if (open) setTimeout(() => inputRef.current?.focus(), 50);
		else {
			setKeyword("");
			setHits([]);
			setActive(0);
		}
	}, [open]);

	useEffect(() => {
		let alive = true;
		void searchService.search(keyword).then((r) => {
			if (!alive) return;
			setHits(unwrap(r));
			setActive(0);
		});
		return () => {
			alive = false;
		};
	}, [keyword]);

	const select = useCallback(
		(hit: SearchHit) => {
			if (hit.departmentId) setDept(hit.departmentId);
			navigate(hit.to);
			setOpen(false);
		},
		[navigate, setDept],
	);

	const onKeyDown = (e: React.KeyboardEvent) => {
		if (e.key === "ArrowDown") {
			e.preventDefault();
			setActive((i) => Math.min(i + 1, hits.length - 1));
		} else if (e.key === "ArrowUp") {
			e.preventDefault();
			setActive((i) => Math.max(i - 1, 0));
		} else if (e.key === "Enter" && hits[active]) {
			select(hits[active]);
		}
	};

	return (
		<Modal open={open} onCancel={() => setOpen(false)} footer={null} closable={false} width={560} style={{ top: 96 }} styles={{ body: { padding: 0 } }} destroyOnClose>
			<div style={{ padding: 12, borderBottom: "1px solid var(--hairline)" }}>
				<Input
					ref={inputRef}
					size="large"
					variant="borderless"
					prefix={<SearchOutlined style={{ color: "var(--ink-subtle)" }} />}
					placeholder="搜索部门 / 数据集 / 指标 / 数据源 / API…"
					value={keyword}
					onChange={(e) => setKeyword(e.target.value)}
					onKeyDown={onKeyDown}
				/>
			</div>
			<div style={{ maxHeight: 420, overflow: "auto", padding: 8 }}>
				{keyword && hits.length === 0 ? (
					<div style={{ padding: 24, textAlign: "center", color: "var(--ink-subtle)" }}>无匹配结果</div>
				) : null}
				{!keyword ? (
					<div style={{ padding: 24, textAlign: "center", color: "var(--ink-subtle)", fontSize: "var(--text-sm)" }}>
						输入关键词跨部门搜索；↑↓ 选择，回车跳转
					</div>
				) : null}
				{hits.map((h, i) => (
					<button
						key={`${h.type}-${h.id}`}
						type="button"
						onClick={() => select(h)}
						onMouseEnter={() => setActive(i)}
						style={{
							display: "flex",
							alignItems: "center",
							gap: 10,
							width: "100%",
							textAlign: "left",
							padding: "9px 12px",
							border: "none",
							borderRadius: "var(--radius-md)",
							cursor: "pointer",
							background: i === active ? "var(--accent-soft)" : "transparent",
						}}
					>
						<Tag color={TYPE_COLOR[h.type]} style={{ marginInlineEnd: 0, minWidth: 48, textAlign: "center" }}>{h.type}</Tag>
						<span style={{ fontWeight: 600, color: "var(--ink)" }}>{h.label}</span>
						{h.sub ? <span style={{ fontSize: 12, color: "var(--ink-subtle)" }}>{h.sub}</span> : null}
					</button>
				))}
			</div>
		</Modal>
	);
}
