import { useCallback, useEffect, useRef, useState } from 'react';
import { Button, Select, Tag, message } from 'antd';
import { analyticsApi } from '../../../api/analyticsApi';

/**
 * 大屏人工密级下限编辑器。有效密级由后端按“所有展示数据最高密级”
 * 自动派生，人工值只允许抬高下限，不能降低有效密级。
 */
export interface ClassificationSelectProps {
	screenId?: string | number;
	/** 人工密级下限（受控模式）。 */
	value?: string | null;
	/** 当前派生出的有效密级。 */
	effectiveValue?: string | null;
	isOwner?: boolean;
	/** 更新成功后回传新的有效密级。 */
	onUpdated?: (next: string) => void;
	size?: 'small' | 'middle' | 'large';
	/** 默认 true：value 未提供时自动 fetch 当前 screen 拿 classification + isOwner */
	autoFetch?: boolean;
	/** 紧凑模式（隐藏说明文字，仅 select+tag），属性面板使用 */
	compact?: boolean;
}

const LEVEL_OPTIONS: Array<{ label: string; value: string }> = [
	{ label: '公开 (PUBLIC)', value: 'PUBLIC' },
	{ label: '内部 (INTERNAL)', value: 'INTERNAL' },
	{ label: '秘密 (SECRET)', value: 'SECRET' },
	{ label: '机密 (CONFIDENTIAL)', value: 'CONFIDENTIAL' },
];

const LEVEL_TAG_COLOR: Record<string, string> = {
	PUBLIC: 'default',
	INTERNAL: 'blue',
	SECRET: 'gold',
	CONFIDENTIAL: 'red',
};

const LEVEL_TAG_LABEL: Record<string, string> = {
	PUBLIC: '公开',
	INTERNAL: '内部',
	SECRET: '秘密',
	CONFIDENTIAL: '机密',
};

const LEVEL_RANK: Record<string, number> = {
	PUBLIC: 0,
	INTERNAL: 1,
	SECRET: 2,
	CONFIDENTIAL: 3,
};

function rankOf(level: string | null | undefined): number {
	if (!level) return 0;
	return LEVEL_RANK[level.trim().toUpperCase()] ?? 0;
}

export function ClassificationSelect({
	screenId,
	value,
	effectiveValue,
	isOwner,
	onUpdated,
	size = 'small',
	autoFetch = true,
	compact = false,
}: ClassificationSelectProps) {
	const isControlled = value !== undefined;
	const [manualFloor, setManualFloor] = useState<string>(typeof value === 'string' ? value.toUpperCase() : '');
	const [effective, setEffective] = useState<string>(
		typeof effectiveValue === 'string'
			? effectiveValue.toUpperCase()
			: typeof value === 'string'
				? value.toUpperCase()
				: '',
	);
	// draft = 用户在 Select 里选了但还没点「确定」的草稿；null 表示与已保存人工下限一致
	const [draft, setDraft] = useState<string | null>(null);
	const [internalIsOwner, setInternalIsOwner] = useState<boolean>(isOwner ?? false);
	const [saving, setSaving] = useState(false);
	const lastFetchedScreenIdRef = useRef<string | number | null>(null);

	useEffect(() => {
		if (isControlled) {
			setManualFloor(typeof value === 'string' ? value.toUpperCase() : '');
			setEffective(
				typeof effectiveValue === 'string'
					? effectiveValue.toUpperCase()
					: typeof value === 'string'
						? value.toUpperCase()
						: '',
			);
			// 受控模式下外部 value 变化（如父组件重新 fetch）→ 清空草稿，
			// 避免显示与新真值不一致的旧草稿。
			setDraft(null);
		}
	}, [effectiveValue, isControlled, value]);

	useEffect(() => {
		if (isOwner !== undefined) {
			setInternalIsOwner(isOwner);
		}
	}, [isOwner]);

	useEffect(() => {
		if (isControlled || !autoFetch || !screenId) return;
		// 避免 React Strict Mode 双调用导致的重复请求
		if (lastFetchedScreenIdRef.current === screenId) return;
		lastFetchedScreenIdRef.current = screenId;
		let cancelled = false;
		(async () => {
			try {
				const screen = await analyticsApi.getScreen(screenId);
				if (cancelled) return;
				const rawEffective = screen?.classification;
				const rawFloor = screen?.manualClassificationFloor;
				setEffective(typeof rawEffective === 'string' ? rawEffective.toUpperCase() : '');
				setManualFloor(
					typeof rawFloor === 'string'
						? rawFloor.toUpperCase()
						: typeof rawEffective === 'string'
							? rawEffective.toUpperCase()
							: '',
				);
				const ownerFlag = screen?.isOwner === true;
				if (isOwner === undefined) {
					setInternalIsOwner(ownerFlag);
				}
			} catch {
				// 静默：拿不到 screen 则保持 placeholder 形态，不打断编辑器
			}
		})();
		return () => {
			cancelled = true;
		};
	}, [isControlled, autoFetch, screenId, isOwner]);

	/**
	 * 设置人工下限。后端重新计算有效密级并拒绝任何降低有效密级的请求。
	 */
	const performUpdate = useCallback(
		async (next: string) => {
			const normalized = next.toUpperCase();
			if (!screenId) {
				message.error('缺少 screenId，无法保存密级');
				return;
			}
			setSaving(true);
			try {
				const result = await analyticsApi.updateScreenClassification(screenId, normalized);
				const nextFloor = result.manualClassificationFloor?.toUpperCase() || normalized;
				const nextEffective = result.classification?.toUpperCase() || normalized;
				setManualFloor(nextFloor);
				setEffective(nextEffective);
				setDraft(null);
				message.success(`人工密级下限已更新，有效密级为 ${nextEffective}`);
				onUpdated?.(nextEffective);
			} catch (e) {
				const msg = e instanceof Error ? e.message : '更新密级失败';
				message.error(msg);
				// 失败时保留草稿让用户重试或自行改回
			} finally {
				setSaving(false);
			}
		},
		[onUpdated, screenId],
	);

	/**
	 * Select onChange — 用户选了新值，仅更新草稿，不立即 PATCH。
	 * 让用户必须显式点「确定」才让密级生效；选错可以选回原值（draft 自动清空）。
	 */
	const handleSelectChange = useCallback(
		(next: string) => {
			const normalized = next?.toUpperCase();
			if (!normalized) return;
			// 草稿与已落库一致 → 视作"恢复原值"，清空草稿
			if (normalized === manualFloor) {
				setDraft(null);
				return;
			}
			setDraft(normalized);
		},
		[manualFloor],
	);

	const handleConfirm = useCallback(async () => {
		if (!draft || draft === manualFloor || saving) return;
		await performUpdate(draft);
	}, [draft, manualFloor, saving, performUpdate]);

	const ownerCanEdit = internalIsOwner;
	const savedValue = manualFloor || '';
	const effectiveLevel = effective || savedValue;
	// Select 显示值优先用草稿，否则用已落库值。
	const selectShown = (draft ?? savedValue) || undefined;
	const isUnclassified = !savedValue;
	const hasDraftChange = draft != null && draft !== savedValue;

	if (!ownerCanEdit) {
		// 非 owner：只读 Tag。null 显示橙色「未设密级」警示。
		return !effectiveLevel ? (
			<Tag color="orange" style={{ margin: 0 }} title="该大屏尚未完成密级派生，访问将被阻断">
				待派生
			</Tag>
		) : (
			<Tag color={LEVEL_TAG_COLOR[effectiveLevel] || 'default'} style={{ margin: 0 }} title="展示数据最高密级">
				{LEVEL_TAG_LABEL[effectiveLevel] || effectiveLevel}
			</Tag>
		);
	}

	return (
		<div>
			<div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
				<span style={{ fontSize: 12, color: 'var(--color-text-secondary, #6b7280)' }}>人工下限</span>
				<Select
					value={selectShown}
					placeholder="选择密级"
					onChange={(v) => handleSelectChange(v as string)}
					disabled={saving}
					size={size}
					style={{ width: 160 }}
					options={LEVEL_OPTIONS.map((option) => ({
						...option,
						disabled: rankOf(option.value) < rankOf(effectiveLevel),
					}))}
					status={isUnclassified || hasDraftChange ? 'warning' : undefined}
				/>
				{/* Sprint-24 重构后：选完密级须点「确定」才生效，避免误改。 */}
				<Button
					type="primary"
					size={size}
					onClick={handleConfirm}
					disabled={!hasDraftChange || saving}
					loading={saving}
				>
					确定
				</Button>
				{hasDraftChange && (
					<span style={{ fontSize: 12, color: 'var(--color-warning, #faad14)' }}>
						未保存，点「确定」生效
					</span>
				)}
				{!compact && isUnclassified && !hasDraftChange && (
					<span style={{ fontSize: 12, color: 'var(--color-warning, #faad14)' }}>
						请设置人工下限；未完成密级派生时禁止发布
					</span>
				)}
			</div>
			{effectiveLevel && (
				<div style={{ marginTop: 8, fontSize: 12, color: 'var(--color-text-secondary, #6b7280)' }}>
					有效密级：
					<Tag color={LEVEL_TAG_COLOR[effectiveLevel] || 'default'} style={{ margin: '0 4px' }}>
						{LEVEL_TAG_LABEL[effectiveLevel] || effectiveLevel}
					</Tag>
					取所有展示数据最高密级与人工下限的较高者，不能降低。
				</div>
			)}
		</div>
	);
}
