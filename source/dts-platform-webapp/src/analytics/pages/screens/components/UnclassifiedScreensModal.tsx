import { useEffect, useMemo, useState, useCallback } from 'react';
import { Modal, Button, Empty, Spin, Tag, message } from 'antd';
import { analyticsApi } from '../../../api/analyticsApi';
import { SortableHeader } from '../../../components/SortableHeader';
import { dateComparator, stringComparator, useTableSort } from '../../../hooks/useTableSort';

/**
 * 大屏密级合规盘点对话框（Sprint-24 F4/T03）
 *
 * 入口对治理角色开放：superuser / OP_ADMIN / 所级或部门数据管理员 / 所级或部门领导
 * （后端 MetabaseAuth.SCREEN_AUDITOR_ROLES）。展示所有 classification=null / 空白的
 * 大屏，帮治理人员一眼盘点出存量未设密大屏并联系 owner 补登。每个条目带「去补登」
 * 按钮直接跳到编辑器属性面板，配合 F1 的入口前移完成补登。
 *
 * 端点本身在 dts-admin 中央审计写一条 screen.compliance.audit_unclassified，
 * 无需前端再触发。
 *
 * 列表为空 = 「全部大屏均已设置密级 ✓」。这是 sprint 完成的核心信号。
 */
export interface UnclassifiedScreensModalProps {
	open: boolean;
	onClose: () => void;
	/** 跳转编辑器的回调（外部决定 `/bi/screens/{id}/edit` 还是别的路由） */
	onJumpToScreen?: (id: number | string) => void;
}

interface UnclassifiedItem {
	id: number | string;
	name?: string | null;
	creatorId?: number | null;
	creatorEmail?: string | null;
	creatorPlatformUsername?: string | null;
	createdAt?: string | null;
}

function formatDate(raw?: string | null): string {
	if (!raw) return '-';
	try {
		const d = new Date(raw);
		if (Number.isNaN(d.getTime())) return raw;
		const pad = (n: number) => String(n).padStart(2, '0');
		return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
	} catch {
		return raw;
	}
}

export function UnclassifiedScreensModal({ open, onClose, onJumpToScreen }: UnclassifiedScreensModalProps) {
	const [loading, setLoading] = useState(false);
	const [items, setItems] = useState<UnclassifiedItem[]>([]);
	const [error, setError] = useState<string | null>(null);

	const itemSortColumns = useMemo(
		() => ({
			name: stringComparator<UnclassifiedItem>((it) => String(it.name ?? '')),
			creator: stringComparator<UnclassifiedItem>(
				(it) => it.creatorPlatformUsername || it.creatorEmail || `user ${it.creatorId ?? '-'}`,
			),
			createdAt: dateComparator<UnclassifiedItem>((it) => it.createdAt),
		}),
		[],
	);
	const { sortedItems: sortedItemsList, sortState: itemSortState, requestSort: requestItemSort } =
		useTableSort(items, {
			columns: itemSortColumns,
			defaultSort: { key: 'createdAt', direction: 'desc' },
		});

	const load = useCallback(async () => {
		setLoading(true);
		setError(null);
		try {
			const result = await analyticsApi.listUnclassifiedScreens();
			setItems(result.items || []);
		} catch (e) {
			const msg = e instanceof Error ? e.message : '加载大屏盘点列表失败';
			// 403 / Forbidden 给一个更友好的引导
			const friendly = /403|forbidden|unauth/i.test(msg)
				? '权限不足：仅数据管理员 / 部门领导 / 所级领导 / OP_ADMIN / superuser 可访问大屏盘点。'
				: msg;
			setError(friendly);
			setItems([]);
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		if (open) {
			load();
		}
	}, [open, load]);

	const handleJump = (id: number | string) => {
		if (onJumpToScreen) {
			onJumpToScreen(id);
		} else {
			message.info(`大屏 ID: ${id}（未配置跳转回调）`);
		}
	};

	return (
		<Modal
			open={open}
			title="合规盘点"
			width={720}
			footer={
				<Button onClick={onClose}>关闭</Button>
			}
			onCancel={onClose}
			destroyOnClose
		>
			{loading ? (
				<div style={{ display: 'flex', justifyContent: 'center', padding: 32 }}>
					<Spin tip="加载中..." />
				</div>
			) : error ? (
				<div
					style={{
						border: '1px solid #ff4d4f',
						background: 'rgba(255,77,79,0.08)',
						color: '#ff4d4f',
						borderRadius: 8,
						padding: 12,
						fontSize: 13,
					}}
				>
					{error}
				</div>
			) : items.length === 0 ? (
				<Empty description="全部大屏均已设置密级 ✓" />
			) : (
				<>
					<div style={{ marginBottom: 12, fontSize: 13 }}>
						<Tag color="orange">未设密级 {items.length} 张</Tag>
						<span style={{ marginLeft: 8, color: 'var(--color-text-tertiary, #6b7280)' }}>
							未设密级的大屏对所有登录用户可见。请联系 owner 在编辑器属性面板补登密级。
						</span>
					</div>
					<div
						style={{
							maxHeight: 480,
							overflowY: 'auto',
							border: '1px solid var(--color-border, rgba(0,0,0,0.1))',
							borderRadius: 8,
						}}
					>
						<table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
							<thead>
								<tr style={{ background: 'var(--color-surface-secondary, rgba(0,0,0,0.04))' }}>
									<SortableHeader
										sortKey="name"
										sortState={itemSortState}
										onSort={requestItemSort}
										style={{ padding: '8px 12px', fontWeight: 600 }}
									>
										名称
									</SortableHeader>
									<SortableHeader
										sortKey="creator"
										sortState={itemSortState}
										onSort={requestItemSort}
										style={{ padding: '8px 12px', fontWeight: 600 }}
									>
										创建者
									</SortableHeader>
									<SortableHeader
										sortKey="createdAt"
										sortState={itemSortState}
										onSort={requestItemSort}
										style={{ padding: '8px 12px', fontWeight: 600, whiteSpace: 'nowrap' }}
									>
										创建时间
									</SortableHeader>
									<th style={{ textAlign: 'right', padding: '8px 12px', fontWeight: 600, whiteSpace: 'nowrap' }}>
										操作
									</th>
								</tr>
							</thead>
							<tbody>
								{sortedItemsList.map((it) => (
									<tr key={String(it.id)} style={{ borderTop: '1px solid var(--color-border, rgba(0,0,0,0.06))' }}>
										<td style={{ padding: '8px 12px' }}>
											<div style={{ fontWeight: 500 }}>{it.name || `大屏 ${it.id}`}</div>
											<div style={{ fontSize: 11, color: 'var(--color-text-tertiary, #6b7280)' }}>id: {it.id}</div>
										</td>
										<td style={{ padding: '8px 12px' }}>
											{it.creatorPlatformUsername || it.creatorEmail || `user ${it.creatorId ?? '-'}`}
										</td>
										<td style={{ padding: '8px 12px', whiteSpace: 'nowrap' }}>{formatDate(it.createdAt)}</td>
										<td style={{ padding: '8px 12px', textAlign: 'right', whiteSpace: 'nowrap' }}>
											<Button size="small" onClick={() => handleJump(it.id)}>
												去补登
											</Button>
										</td>
									</tr>
								))}
							</tbody>
						</table>
					</div>
				</>
			)}
		</Modal>
	);
}
