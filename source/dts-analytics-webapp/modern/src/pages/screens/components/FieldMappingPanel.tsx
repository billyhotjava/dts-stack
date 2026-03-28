import { useCallback, useMemo, useState } from 'react';
import type { ComponentType, FieldMapping, FieldMappingAggregation } from '../types';
import {
	type MappingSlot,
	columnTypeIcon,
	detectStaleFields,
	getMappingSlots,
	isMappable,
} from '../hooks/fieldMappingTransform';

interface SourceColumn {
	name: string;
	displayName: string;
	baseType?: string;
}

interface FieldMappingPanelProps {
	componentType: ComponentType;
	sourceColumns: SourceColumn[];
	mapping: FieldMapping;
	onChange: (mapping: FieldMapping) => void;
}

/**
 * Visual field mapping panel — users click source columns to assign them to chart slots.
 * Uses click-to-assign instead of drag-and-drop for simplicity and Chrome 95 compatibility.
 */
export function FieldMappingPanel({
	componentType,
	sourceColumns,
	mapping,
	onChange,
}: FieldMappingPanelProps) {
	const slots = useMemo(() => getMappingSlots(componentType), [componentType]);
	const [activeSlot, setActiveSlot] = useState<keyof FieldMapping | null>(null);

	const staleFields = useMemo(
		() => detectStaleFields(mapping, sourceColumns),
		[mapping, sourceColumns],
	);

	const handleColumnClick = useCallback((colName: string) => {
		if (!activeSlot) return;
		const slot = slots.find(s => s.key === activeSlot);
		if (!slot) return;

		const next = { ...mapping };
		if (slot.key === 'measures' && slot.multi) {
			const current = next.measures ?? [];
			if (current.includes(colName)) {
				next.measures = current.filter(m => m !== colName);
			} else {
				next.measures = [...current, colName];
			}
		} else if (slot.key === 'measures') {
			next.measures = [colName];
		} else {
			(next as Record<string, unknown>)[slot.key] = colName;
		}
		onChange(next);
	}, [activeSlot, mapping, onChange, slots]);

	const handleClearSlot = useCallback((slotKey: keyof FieldMapping) => {
		const next = { ...mapping };
		if (slotKey === 'measures') {
			next.measures = [];
		} else {
			delete (next as Record<string, unknown>)[slotKey];
		}
		onChange(next);
	}, [mapping, onChange]);

	const handleAggregationChange = useCallback((agg: FieldMappingAggregation) => {
		onChange({ ...mapping, aggregation: agg });
	}, [mapping, onChange]);

	const handleSortChange = useCallback((field: string, order: 'asc' | 'desc') => {
		onChange({ ...mapping, sortField: field || undefined, sortOrder: order });
	}, [mapping, onChange]);

	const getSlotValue = (slot: MappingSlot): string[] => {
		if (slot.key === 'measures') return mapping.measures ?? [];
		const val = mapping[slot.key];
		return val ? [String(val)] : [];
	};

	const isColumnAssigned = (colName: string): boolean => {
		if (mapping.dimension === colName) return true;
		if (mapping.measures?.includes(colName)) return true;
		if (mapping.groupBy === colName) return true;
		if (mapping.sizeField === colName) return true;
		return false;
	};

	if (!isMappable(componentType) || sourceColumns.length === 0) return null;

	return (
		<div style={{ marginTop: 4 }}>
			{/* Stale field warnings */}
			{staleFields.length > 0 && (
				<div
					className="text-xs rounded-sm"
					style={{
						background: 'rgba(239,68,68,0.15)',
						color: '#f87171',
						padding: '6px 10px',
						marginBottom: 8,
					}}
				>
					⚠ 字段已失效: {staleFields.join(', ')}
				</div>
			)}

			<div className="grid grid-cols-2 gap-sm">
				{/* Left: source columns */}
				<div className="flex flex-col" style={{ gap: 3 }}>
					<div className="text-xs font-semibold" style={{ color: '#94a3b8', marginBottom: 6 }}>数据字段</div>
					{sourceColumns.map((col) => {
						const assigned = isColumnAssigned(col.name);
						const stale = staleFields.includes(col.name);
						return (
							<div
								key={col.name}
								className="flex items-center text-xs"
								style={{
									gap: 6,
									padding: '5px 8px',
									background: 'rgba(15,23,42,0.6)',
									border: `1px solid ${stale ? 'rgba(239,68,68,0.4)' : assigned ? 'rgba(0,212,255,0.4)' : 'rgba(148,163,184,0.15)'}`,
									borderRadius: 5,
									color: stale ? '#f87171' : assigned ? '#7dd3fc' : '#cbd5e1',
									cursor: activeSlot ? 'pointer' : 'default',
									transition: 'all 0.15s',
								}}
								onClick={() => handleColumnClick(col.name)}
								title={`${col.displayName} (${col.baseType || 'unknown'})`}
							>
								<span style={{ fontSize: 10, minWidth: 16, textAlign: 'center', opacity: 0.7 }}>
									{columnTypeIcon(col.baseType || '')}
								</span>
								<span className="truncate">{col.displayName || col.name}</span>
							</div>
						);
					})}
				</div>

				{/* Right: target slots */}
				<div className="flex flex-col" style={{ gap: 3 }}>
					<div className="text-xs font-semibold" style={{ color: '#94a3b8', marginBottom: 6 }}>图表映射</div>
					{slots.map((slot) => {
						const values = getSlotValue(slot);
						const isActive = activeSlot === slot.key;
						return (
							<div
								key={slot.key}
								className="cursor-pointer"
								style={{
									padding: '6px 8px',
									background: isActive ? 'rgba(0,212,255,0.06)' : 'rgba(15,23,42,0.4)',
									border: `1px ${values.length > 0 || isActive ? 'solid' : 'dashed'} ${isActive || values.length > 0 ? (isActive ? '#00d4ff' : 'rgba(0,212,255,0.3)') : 'rgba(148,163,184,0.2)'}`,
									borderRadius: 5,
									transition: 'all 0.15s',
								}}
								onClick={() => setActiveSlot(isActive ? null : slot.key)}
							>
								<div className="flex justify-between items-center" style={{ marginBottom: 4 }}>
									<span className="text-xs font-medium" style={{ color: '#94a3b8' }}>
										{slot.label}
										{slot.required && <span style={{ color: '#f87171', marginLeft: 2 }}>*</span>}
									</span>
									{values.length > 0 && (
										<button
											type="button"
											className="cursor-pointer"
											style={{
												background: 'none',
												border: 'none',
												color: '#64748b',
												fontSize: 14,
												padding: '0 2px',
												lineHeight: 1,
											}}
											onClick={(e) => { e.stopPropagation(); handleClearSlot(slot.key); }}
											title="清除"
										>
											×
										</button>
									)}
								</div>
								<div className="flex flex-wrap items-center" style={{ gap: 4, minHeight: 22 }}>
									{values.length > 0
										? values.map(v => (
											<span
												key={v}
												className="inline-block text-xs"
												style={{
													padding: '2px 8px',
													background: staleFields.includes(v) ? 'rgba(239,68,68,0.15)' : 'rgba(0,212,255,0.15)',
													color: staleFields.includes(v) ? '#f87171' : '#7dd3fc',
													borderRadius: 10,
													textDecoration: staleFields.includes(v) ? 'line-through' : undefined,
												}}
											>
												{sourceColumns.find(c => c.name === v)?.displayName || v}
											</span>
										))
										: <span className="text-xs" style={{ color: '#475569', fontStyle: 'italic' }}>
											{isActive ? '点击左侧字段分配' : '点击此处激活'}
										</span>
									}
								</div>
							</div>
						);
					})}
				</div>
			</div>

			{/* Aggregation & sort controls */}
			<div className="flex flex-wrap" style={{ gap: 12, marginTop: 8 }}>
				<div className="flex items-center" style={{ gap: 4 }}>
					<label className="text-xs" style={{ color: '#94a3b8' }}>聚合</label>
					<select
						value={mapping.aggregation ?? 'sum'}
						onChange={(e) => handleAggregationChange(e.target.value as FieldMappingAggregation)}
						style={{
							background: 'rgba(15,23,42,0.8)',
							border: '1px solid rgba(148,163,184,0.2)',
							borderRadius: 4,
							padding: '3px 6px',
							color: '#cbd5e1',
							fontSize: 11,
						}}
					>
						<option value="sum">求和</option>
						<option value="count">计数</option>
						<option value="avg">平均值</option>
						<option value="min">最小值</option>
						<option value="max">最大值</option>
					</select>
				</div>
				<div className="flex items-center" style={{ gap: 4 }}>
					<label className="text-xs" style={{ color: '#94a3b8' }}>排序</label>
					<select
						value={mapping.sortField ?? ''}
						onChange={(e) => handleSortChange(e.target.value, mapping.sortOrder ?? 'asc')}
						style={{
							background: 'rgba(15,23,42,0.8)',
							border: '1px solid rgba(148,163,184,0.2)',
							borderRadius: 4,
							padding: '3px 6px',
							color: '#cbd5e1',
							fontSize: 11,
						}}
					>
						<option value="">无</option>
						{sourceColumns.map(col => (
							<option key={col.name} value={col.name}>{col.displayName || col.name}</option>
						))}
					</select>
					{mapping.sortField && (
						<select
							value={mapping.sortOrder ?? 'asc'}
							onChange={(e) => handleSortChange(mapping.sortField ?? '', e.target.value as 'asc' | 'desc')}
							style={{
								background: 'rgba(15,23,42,0.8)',
								border: '1px solid rgba(148,163,184,0.2)',
								borderRadius: 4,
								padding: '3px 6px',
								color: '#cbd5e1',
								fontSize: 11,
							}}
						>
							<option value="asc">升序</option>
							<option value="desc">降序</option>
						</select>
					)}
				</div>
			</div>
		</div>
	);
}

export { isMappable };
