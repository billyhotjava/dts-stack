// @ts-nocheck — migrated from analytics-webapp, pending unused-import cleanup
import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { useScreen } from '../ScreenContext';

export function LayerPanel() {
	const { state, dispatch, selectComponents, editorReadonly } = useScreen();
	const { config, selectedIds } = state;
	const [keyword, setKeyword] = useState('');
	const [bulkAction, setBulkAction] = useState<'show' | 'hide' | 'lock' | 'unlock' | 'top' | 'bottom'>('show');
	const [openMenuId, setOpenMenuId] = useState<string | null>(null);
	const [dragOverId, setDragOverId] = useState<string | null>(null);
	const dragSourceId = useRef<string | null>(null);

	useEffect(() => {
		const handlePointerDown = (event: MouseEvent) => {
			const target = event.target as Element | null;
			if (!target) {
				setOpenMenuId(null);
				return;
			}
			if (target.closest('[data-layer-actions]')) {
				return;
			}
			setOpenMenuId(null);
		};
		const handleEscape = (event: KeyboardEvent) => {
			if (event.key === 'Escape') {
				setOpenMenuId(null);
			}
		};
		window.addEventListener('mousedown', handlePointerDown);
		window.addEventListener('keydown', handleEscape);
		return () => {
			window.removeEventListener('mousedown', handlePointerDown);
			window.removeEventListener('keydown', handleEscape);
		};
	}, []);

	const componentMap = new Map(config.components.map((item) => [item.id, item]));
	const visited = new Set<string>();

	const topLevelComponents = config.components
		.filter((item) => !item.parentContainerId || !componentMap.has(item.parentContainerId))
		.sort((a, b) => b.zIndex - a.zIndex);

	const layered: Array<{ component: typeof config.components[number]; depth: number }> = [];
	const walk = (component: typeof config.components[number], depth: number) => {
		if (visited.has(component.id)) return;
		visited.add(component.id);
		layered.push({ component, depth });
		if (component.type !== 'container') {
			return;
		}
		const children = config.components
			.filter((item) => item.parentContainerId === component.id)
			.sort((a, b) => b.zIndex - a.zIndex);
		for (const child of children) {
			walk(child, depth + 1);
		}
	};
	for (const component of topLevelComponents) {
		walk(component, 0);
	}
	for (const component of config.components.sort((a, b) => b.zIndex - a.zIndex)) {
		walk(component, 0);
	}

	const handleLayerClick = (id: string, e: React.MouseEvent) => {
		if (e.ctrlKey || e.metaKey) {
			// Multi-select with Ctrl/Cmd
			if (selectedIds.includes(id)) {
				selectComponents(selectedIds.filter((i) => i !== id));
			} else {
				selectComponents([...selectedIds, id]);
			}
		} else {
			selectComponents([id]);
		}
	};

	const handleReorder = (id: string, direction: 'up' | 'down' | 'top' | 'bottom') => {
		if (editorReadonly) return;
		dispatch({ type: 'REORDER_LAYER', payload: { id, direction } });
	};

	const handleVisibilityToggle = (id: string, visible: boolean) => {
		if (editorReadonly) return;
		dispatch({ type: 'UPDATE_COMPONENT', payload: { id, updates: { visible: !visible } } });
	};

	const handleLockToggle = (id: string, locked: boolean) => {
		if (editorReadonly) return;
		dispatch({ type: 'UPDATE_COMPONENT', payload: { id, updates: { locked: !locked } } });
	};

	const handleDragStart = useCallback((e: React.DragEvent, id: string) => {
		if (editorReadonly) {
			e.preventDefault();
			return;
		}
		dragSourceId.current = id;
		e.dataTransfer.effectAllowed = 'move';
		e.dataTransfer.setData('text/plain', id);
		(e.currentTarget as HTMLElement).style.opacity = '0.4';
	}, [editorReadonly]);

	const handleDragEnd = useCallback((e: React.DragEvent) => {
		(e.currentTarget as HTMLElement).style.opacity = '1';
		dragSourceId.current = null;
		setDragOverId(null);
	}, []);

	const handleDragOver = useCallback((e: React.DragEvent, id: string) => {
		e.preventDefault();
		e.dataTransfer.dropEffect = 'move';
		if (id !== dragSourceId.current) {
			setDragOverId(id);
		}
	}, []);

	const handleDrop = useCallback((e: React.DragEvent, targetId: string) => {
		e.preventDefault();
		if (editorReadonly) return;
		setDragOverId(null);
		const sourceId = dragSourceId.current;
		if (!sourceId || sourceId === targetId) return;
		const sorted = config.components.slice().sort((a, b) => b.zIndex - a.zIndex);
		const sourceIdx = sorted.findIndex(c => c.id === sourceId);
		const targetIdx = sorted.findIndex(c => c.id === targetId);
		if (sourceIdx < 0 || targetIdx < 0) return;
		const [moved] = sorted.splice(sourceIdx, 1);
		sorted.splice(targetIdx, 0, moved);
		const nextComponents = sorted.map((c, i) => ({
			...c,
			zIndex: sorted.length - i,
		}));
		dispatch({ type: 'SET_CONFIG', payload: { ...config, components: nextComponents } });
	}, [config, dispatch, editorReadonly]);

	const getComponentIcon = (type: string): string => {
		const iconMap: Record<string, string> = {
			'line-chart': '📈',
			'bar-chart': '📊',
			'pie-chart': '🥧',
			'gauge-chart': '🎯',
			'radar-chart': '🕸️',
			'funnel-chart': '🔽',
			'map-chart': '🗺️',
			'number-card': '🔢',
			'title': '🔤',
			'markdown-text': '📄',
			'countdown': '⏳',
			'marquee': '📢',
			'shape': '🔷',
			'container': '🗂️',
			'datetime': '🕐',
			'progress-bar': '📏',
			'image': '🖼️',
			'video': '🎬',
			'iframe': '🌐',
			'table': '🗂️',
			'filter-input': '⌨️',
			'filter-select': '🔽',
			'filter-date-range': '📅',
			'border-box': '🔲',
			'decoration': '💠',
			'scroll-board': '📜',
			'scroll-ranking': '🏆',
			'water-level': '💧',
			'digital-flop': '🔄',
		};
		return iconMap[type] || '📦';
	};

	const normalizedKeyword = keyword.trim().toLowerCase();
	const filteredLayered = useMemo(() => {
		if (!normalizedKeyword) return layered;
		return layered.filter(({ component }) => {
			const name = String(component.name || '').toLowerCase();
			const type = String(component.type || '').toLowerCase();
			const id = String(component.id || '').toLowerCase();
			return name.includes(normalizedKeyword) || type.includes(normalizedKeyword) || id.includes(normalizedKeyword);
		});
	}, [layered, normalizedKeyword]);

	const applySelectedUpdates = (updates: Partial<typeof config.components[number]>) => {
		if (editorReadonly) return;
		if (selectedIds.length === 0) return;
		for (const id of selectedIds) {
			dispatch({ type: 'UPDATE_COMPONENT', payload: { id, updates } });
		}
	};

	const reorderSelected = (direction: 'top' | 'bottom') => {
		if (editorReadonly) return;
		if (selectedIds.length === 0) return;
		const selected = config.components
			.filter((item) => selectedIds.includes(item.id))
			.sort((a, b) => (direction === 'top' ? a.zIndex - b.zIndex : b.zIndex - a.zIndex));
		for (const item of selected) {
			dispatch({ type: 'REORDER_LAYER', payload: { id: item.id, direction } });
		}
	};

	const selectFiltered = () => {
		if (filteredLayered.length === 0) return;
		selectComponents(filteredLayered.map((item) => item.component.id));
	};
	const hasSelected = selectedIds.length > 0;
	const canBulkEdit = hasSelected && !editorReadonly;
	const executeBulkAction = () => {
		if (!canBulkEdit) return;
		if (bulkAction === 'show') {
			applySelectedUpdates({ visible: true });
			return;
		}
		if (bulkAction === 'hide') {
			applySelectedUpdates({ visible: false });
			return;
		}
		if (bulkAction === 'lock') {
			applySelectedUpdates({ locked: true });
			return;
		}
		if (bulkAction === 'unlock') {
			applySelectedUpdates({ locked: false });
			return;
		}
		if (bulkAction === 'top') {
			reorderSelected('top');
			return;
		}
		reorderSelected('bottom');
	};

	return (
		<div className="flex flex-col h-full" style={{ padding: 18 }}>
			{/* Header */}
			<div className="flex items-center justify-between" style={{ marginBottom: 14 }}>
				<h4 className="text-xs font-semibold uppercase text-secondary" style={{ margin: 0 }}>图层</h4>
				<span className="text-xs font-semibold text-tertiary">
					{selectedIds.length}/{config.components.length}
				</span>
			</div>

			{/* Tools */}
			<div className="grid gap-sm" style={{ marginBottom: 14 }}>
				<input
					type="text"
					className="property-input"
					placeholder="搜索组件名/类型/ID"
					value={keyword}
					onChange={(e) => setKeyword(e.target.value)}
				/>
				<div className="grid grid-cols-3 gap-sm" style={{ gap: 6 }}>
					<button
						className="flex items-center justify-center w-full border rounded-lg bg-transparent text-secondary text-xs cursor-pointer"
						style={{ height: 32, border: '1px solid rgba(255,255,255,0.1)', background: 'rgba(255,255,255,0.06)', borderRadius: 12 }}
						onClick={selectFiltered}
						title="选择当前筛选结果"
					>
						全选
					</button>
					<button
						className="flex items-center justify-center w-full text-secondary text-xs cursor-pointer"
						style={{ height: 32, border: '1px solid rgba(255,255,255,0.1)', background: 'rgba(255,255,255,0.06)', borderRadius: 12 }}
						onClick={() => selectComponents([])}
						title="清空选择"
					>
						清空
					</button>
					<button
						className="flex items-center justify-center w-full text-secondary text-xs cursor-pointer"
						style={{ height: 32, border: '1px solid rgba(255,255,255,0.1)', background: 'rgba(255,255,255,0.06)', borderRadius: 12 }}
						onClick={() => setKeyword('')}
						title="清空搜索"
					>
						重置
					</button>
				</div>
				<div className="grid" style={{ gridTemplateColumns: 'minmax(0,1fr) auto', gap: 6 }}>
					<select
						className="property-input"
						style={{ minHeight: 32, paddingBlock: 0 }}
						value={bulkAction}
						onChange={(e) => {
							const next = e.target.value;
							if (
								next === 'show'
								|| next === 'hide'
								|| next === 'lock'
								|| next === 'unlock'
								|| next === 'top'
								|| next === 'bottom'
							) {
								setBulkAction(next);
							}
						}}
						title="选择批量动作"
					>
						<option value="show">显示选中</option>
						<option value="hide">隐藏选中</option>
						<option value="lock">锁定选中</option>
						<option value="unlock">解锁选中</option>
						<option value="top">置顶选中</option>
						<option value="bottom">置底选中</option>
					</select>
					<button
						className="flex items-center justify-center text-secondary text-xs cursor-pointer"
						style={{
							minWidth: 64,
							height: 32,
							paddingInline: 10,
							border: '1px solid rgba(255,255,255,0.1)',
							background: 'rgba(255,255,255,0.06)',
							borderRadius: 12,
							opacity: canBulkEdit ? 1 : 0.45,
						}}
						onClick={executeBulkAction}
						title="执行批量动作"
						disabled={!canBulkEdit}
					>
						执行
					</button>
				</div>
			</div>

			{/* Layer list */}
			{filteredLayered.length === 0 ? (
				<div className="flex flex-col items-center justify-center text-center text-secondary" style={{ minHeight: 160, padding: '24px 12px' }}>
					<div className="text-xs">
						{config.components.length === 0 ? '暂无组件' : '没有匹配结果'}
					</div>
				</div>
			) : (
				<div className="flex flex-col flex-1 min-h-0 overflow-auto" style={{ gap: 8, paddingRight: 2 }}>
					{filteredLayered.map(({ component, depth }) => {
						const isSelected = selectedIds.includes(component.id);
						const isDragOver = dragOverId === component.id;
						return (
							<div
								key={component.id}
								className="flex items-center cursor-pointer"
								style={{
									minHeight: 44,
									padding: '10px 12px',
									paddingLeft: `calc(12px + ${depth * 16}px)`,
									borderRadius: 8,
									border: isDragOver
										? '2px solid var(--color-primary, #509ee3)'
										: '1px solid rgba(255,255,255,0.06)',
									background: isDragOver
										? 'var(--color-primary-light, rgba(80,158,227,0.08))'
										: isSelected
											? 'rgba(80,158,227,0.12)'
											: 'rgba(255,255,255,0.03)',
									boxShadow: isSelected ? 'inset 0 0 0 1px rgba(80,158,227,0.22)' : undefined,
									opacity: component.visible ? 1 : 0.56,
									transition: 'all 0.2s ease',
								}}
								onClick={(e) => handleLayerClick(component.id, e)}
								draggable={!editorReadonly}
								onDragStart={(e) => handleDragStart(e, component.id)}
								onDragEnd={handleDragEnd}
								onDragOver={(e) => handleDragOver(e, component.id)}
								onDrop={(e) => handleDrop(e, component.id)}
								onDragLeave={() => setDragOverId(null)}
							>
								<span style={{ width: 20, height: 20, marginRight: 8 }} className="text-secondary">
									{getComponentIcon(component.type)}
								</span>
								<span className="flex-1 text-sm text-primary font-semibold truncate">
									{component.parentContainerId ? '↳ ' : ''}
									{component.name}
									{component.groupId ? ' [组]' : ''}
									{component.parentContainerId ? ' [容器]' : ''}
								</span>
								<div className="flex items-center relative" style={{ gap: 4 }} data-layer-actions>
									<button
										className="inline-flex items-center justify-center bg-transparent border-0 cursor-pointer text-secondary text-xs"
										style={{ minWidth: 22, width: 22, height: 22, borderRadius: 12 }}
										onClick={(e) => {
											e.stopPropagation();
											handleVisibilityToggle(component.id, component.visible);
											setOpenMenuId(null);
										}}
										title={component.visible ? '隐藏' : '显示'}
										disabled={editorReadonly}
									>
										{component.visible ? '👁️' : '👁️‍🗨️'}
									</button>
									<button
										className="inline-flex items-center justify-center bg-transparent border-0 cursor-pointer text-secondary text-xs"
										style={{ minWidth: 22, width: 22, height: 22, borderRadius: 12 }}
										onClick={(e) => {
											e.stopPropagation();
											handleLockToggle(component.id, component.locked);
											setOpenMenuId(null);
										}}
										title={component.locked ? '解锁' : '锁定'}
										disabled={editorReadonly}
									>
										{component.locked ? '🔒' : '🔓'}
									</button>
									<button
										className="inline-flex items-center justify-center bg-transparent border-0 cursor-pointer text-secondary text-xs"
										style={{ minWidth: 22, width: 22, height: 22, borderRadius: 12 }}
										onClick={(e) => {
											e.stopPropagation();
											setOpenMenuId((prev) => (prev === component.id ? null : component.id));
										}}
										title="更多动作"
									>
										⋯
									</button>
									{openMenuId === component.id ? (
										<div
											className="absolute grid"
											style={{
												right: 0,
												top: 'calc(100% + 8px)',
												zIndex: 6,
												minWidth: 126,
												padding: 8,
												border: '1px solid var(--color-border)',
												borderRadius: 18,
												background: 'var(--color-bg-primary)',
												boxShadow: '0 8px 22px rgba(2,6,23,0.22)',
												gap: 4,
											}}
											onClick={(e) => e.stopPropagation()}
										>
											<button
												type="button"
												className="text-left cursor-pointer text-primary text-xs"
												style={{
													minHeight: 34,
													borderRadius: 12,
													padding: '0 10px',
													border: '1px solid var(--color-border)',
													background: 'var(--color-bg-secondary)',
												}}
												onClick={() => {
													handleReorder(component.id, 'up');
													setOpenMenuId(null);
												}}
												disabled={editorReadonly}
											>
												上移一层
											</button>
											<button
												type="button"
												className="text-left cursor-pointer text-primary text-xs"
												style={{
													minHeight: 34,
													borderRadius: 12,
													padding: '0 10px',
													border: '1px solid var(--color-border)',
													background: 'var(--color-bg-secondary)',
												}}
												onClick={() => {
													handleReorder(component.id, 'down');
													setOpenMenuId(null);
												}}
												disabled={editorReadonly}
											>
												下移一层
											</button>
											<button
												type="button"
												className="text-left cursor-pointer text-primary text-xs"
												style={{
													minHeight: 34,
													borderRadius: 12,
													padding: '0 10px',
													border: '1px solid var(--color-border)',
													background: 'var(--color-bg-secondary)',
												}}
												onClick={() => {
													handleReorder(component.id, 'top');
													setOpenMenuId(null);
												}}
												disabled={editorReadonly}
											>
												置顶图层
											</button>
											<button
												type="button"
												className="text-left cursor-pointer text-primary text-xs"
												style={{
													minHeight: 34,
													borderRadius: 12,
													padding: '0 10px',
													border: '1px solid var(--color-border)',
													background: 'var(--color-bg-secondary)',
												}}
												onClick={() => {
													handleReorder(component.id, 'bottom');
													setOpenMenuId(null);
												}}
												disabled={editorReadonly}
											>
												置底图层
											</button>
										</div>
									) : null}
								</div>
							</div>
						);
					})}
				</div>
			)}
		</div>
	);
}
