import { useCallback, useEffect, useRef, useState } from 'react';
import { Select, Tag, message } from 'antd';
import { analyticsApi } from '../../../api/analyticsApi';

/**
 * 大屏密级共享组件（Sprint-24 F1/T01）
 *
 * 单一真源：编辑器属性面板「画布设置」+ 分享面板「大屏密级」复用本组件，
 * 避免两处独立维护带来的行为分叉。
 *
 * 使用方式：
 * - **受控**：调用方提供 `value` + `onChange`，组件不自己 fetch 也不调 PATCH
 *   （适合分享面板：父组件负责 loadAcl 时同时拿 classification）
 * - **非受控**：调用方仅提供 `screenId`，组件自己 fetch 当前值并调 PATCH
 *   （适合属性面板：减少父组件状态管理负担）
 *
 * isOwner=false 或缺失时显示只读 Tag，与原 ScreenSharePanel 行为一致。
 */
export interface ClassificationSelectProps {
	screenId?: string | number;
	value?: string | null;
	isOwner?: boolean;
	/** 受控模式：父组件提供 onChange 时，组件不会自己调 PATCH endpoint */
	onChange?: (next: string) => void;
	/** 改完之后的回调（无论受控/非受控都会触发，便于父组件刷新展示） */
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

export function ClassificationSelect({
	screenId,
	value,
	isOwner,
	onChange,
	onUpdated,
	size = 'small',
	autoFetch = true,
	compact = false,
}: ClassificationSelectProps) {
	const isControlled = value !== undefined;
	const [internal, setInternal] = useState<string>(typeof value === 'string' ? value.toUpperCase() : '');
	const [internalIsOwner, setInternalIsOwner] = useState<boolean>(isOwner ?? false);
	const [saving, setSaving] = useState(false);
	const lastFetchedScreenIdRef = useRef<string | number | null>(null);

	useEffect(() => {
		if (isControlled) {
			setInternal(typeof value === 'string' ? value.toUpperCase() : '');
		}
	}, [isControlled, value]);

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
				const raw = (screen as { classification?: string | null } | null)?.classification;
				setInternal(typeof raw === 'string' ? raw.toUpperCase() : '');
				const ownerFlag = (screen as { isOwner?: boolean } | null)?.isOwner === true;
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

	const handleChange = useCallback(
		async (next: string) => {
			const normalized = next?.toUpperCase();
			if (!normalized || normalized === internal) return;
			if (isControlled && onChange) {
				onChange(normalized);
				onUpdated?.(normalized);
				return;
			}
			if (!screenId) {
				message.error('缺少 screenId，无法保存密级');
				return;
			}
			const previous = internal;
			setInternal(normalized);
			setSaving(true);
			try {
				await analyticsApi.updateScreenClassification(screenId, normalized);
				message.success(`大屏密级已更新为 ${normalized}`);
				onUpdated?.(normalized);
			} catch (e) {
				setInternal(previous);
				const msg = e instanceof Error ? e.message : '更新密级失败';
				message.error(msg);
			} finally {
				setSaving(false);
			}
		},
		[internal, isControlled, onChange, onUpdated, screenId],
	);

	const ownerCanEdit = internalIsOwner;
	const displayValue = internal || '';
	const isUnclassified = !displayValue;

	if (!ownerCanEdit) {
		// 非 owner：只读 Tag。null 显示橙色「未设密级」警示。
		return isUnclassified ? (
			<Tag color="orange" style={{ margin: 0 }} title="该大屏未设密级，对所有登录用户可见">
				未设密级
			</Tag>
		) : (
			<Tag color={LEVEL_TAG_COLOR[displayValue] || 'default'} style={{ margin: 0 }}>
				{LEVEL_TAG_LABEL[displayValue] || displayValue}
			</Tag>
		);
	}

	return (
		<div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
			<Select
				value={displayValue || undefined}
				placeholder="选择密级"
				onChange={(v) => handleChange(v as string)}
				disabled={saving}
				loading={saving}
				size={size}
				style={{ width: 160 }}
				options={LEVEL_OPTIONS}
				status={isUnclassified ? 'warning' : undefined}
			/>
			{!compact && isUnclassified && (
				<span style={{ fontSize: 12, color: 'var(--color-warning, #faad14)' }}>
					未设密级，对所有登录用户可见
				</span>
			)}
		</div>
	);
}
