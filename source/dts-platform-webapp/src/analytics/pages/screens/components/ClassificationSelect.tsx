import { useCallback, useEffect, useRef, useState } from 'react';
import { Button, Modal, Input, Select, Tag, message } from 'antd';
import { analyticsApi } from '../../../api/analyticsApi';

/**
 * 大屏密级共享组件（Sprint-24 F1/T01 + F5）
 *
 * 单一真源：编辑器属性面板「画布设置」+ 分享面板「大屏密级」复用本组件，
 * 避免两处独立维护带来的行为分叉。
 *
 * 改密流程统一在组件内部完成（不论受控/非受控）：
 *   1. 用户选新值
 *   2. 检测降级方向（next_rank < before_rank）
 *      - 降级：弹 confirm modal，强制收集 reason（>=10 字符）
 *      - 升级 / 同级：直接发 PATCH，不打扰用户
 *   3. 调 PATCH /api/screens/{id}/classification（含 reason 时一并发送）
 *   4. 成功后通过 onUpdated(next) 通知父组件同步本地状态
 *
 * `value` 提供时为受控展示（组件以 value 为准），但 PATCH 仍由组件发起；
 * `value` 缺失 + autoFetch=true 时组件自己 fetch 当前 screen 拿初始值。
 *
 * isOwner=false 或缺失时显示只读 Tag，与原 ScreenSharePanel 行为一致。
 */
export interface ClassificationSelectProps {
	screenId?: string | number;
	value?: string | null;
	isOwner?: boolean;
	/** 改完之后的回调（PATCH 成功后触发，便于父组件同步本地展示状态） */
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

/**
 * Sprint-24 F5：密级阶梯。配合 isDowngrade 判定降级方向。
 * 与后端 ScreenResource.classificationRank 同序：PUBLIC(0) < INTERNAL(1) < SECRET(2) < CONFIDENTIAL(3)。
 */
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

function isDowngrade(before: string | null | undefined, next: string): boolean {
	return rankOf(next) < rankOf(before);
}

const DOWNGRADE_REASON_MIN_LENGTH = 10;

export function ClassificationSelect({
	screenId,
	value,
	isOwner,
	onUpdated,
	size = 'small',
	autoFetch = true,
	compact = false,
}: ClassificationSelectProps) {
	const isControlled = value !== undefined;
	// internal = 已落库（DB 中）的密级；显示在「当前密级 Tag」上
	const [internal, setInternal] = useState<string>(typeof value === 'string' ? value.toUpperCase() : '');
	// draft = 用户在 Select 里选了但还没点「确定」的草稿；null 表示草稿与 internal 一致
	const [draft, setDraft] = useState<string | null>(null);
	const [internalIsOwner, setInternalIsOwner] = useState<boolean>(isOwner ?? false);
	const [saving, setSaving] = useState(false);
	const lastFetchedScreenIdRef = useRef<string | number | null>(null);

	// Sprint-24 F5：降级 reason confirm modal 状态。
	// pendingDowngrade 保存「即将提交但还在等用户填 reason」的目标值。
	const [pendingDowngrade, setPendingDowngrade] = useState<string | null>(null);
	const [downgradeReason, setDowngradeReason] = useState<string>('');

	useEffect(() => {
		if (isControlled) {
			setInternal(typeof value === 'string' ? value.toUpperCase() : '');
			// 受控模式下外部 value 变化（如父组件重新 fetch）→ 清空草稿，
			// 避免显示与新真值不一致的旧草稿。
			setDraft(null);
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

	/**
	 * 真正发 PATCH 的逻辑。降级路径会先经过 modal 收集 reason，再调到这里。
	 * 升级 / 同级路径直接调用，reason=undefined。成功后清空草稿（让 internal 接管显示）。
	 */
	const performUpdate = useCallback(
		async (next: string, reason?: string) => {
			const normalized = next.toUpperCase();
			if (!screenId) {
				message.error('缺少 screenId，无法保存密级');
				return;
			}
			setSaving(true);
			try {
				await analyticsApi.updateScreenClassification(screenId, normalized, reason);
				setInternal(normalized);
				setDraft(null);
				message.success(`大屏密级已更新为 ${normalized}`);
				onUpdated?.(normalized);
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
			if (normalized === internal) {
				setDraft(null);
				return;
			}
			setDraft(normalized);
		},
		[internal],
	);

	/**
	 * 用户点「确定」按钮 — 此时根据降级方向决定走 modal 收集 reason 还是直接 PATCH。
	 */
	const handleConfirm = useCallback(async () => {
		if (!draft || draft === internal || saving) return;
		if (isDowngrade(internal, draft)) {
			setPendingDowngrade(draft);
			setDowngradeReason('');
			return;
		}
		await performUpdate(draft);
	}, [draft, internal, saving, performUpdate]);

	const cancelDowngrade = useCallback(() => {
		setPendingDowngrade(null);
		setDowngradeReason('');
		// 取消降级时不清空 draft，用户可改 select 选其它值或点取消草稿
	}, []);

	const confirmDowngrade = useCallback(async () => {
		if (!pendingDowngrade) return;
		const reason = downgradeReason.trim();
		if (reason.length < DOWNGRADE_REASON_MIN_LENGTH) {
			message.error(`降级原因至少 ${DOWNGRADE_REASON_MIN_LENGTH} 个字符`);
			return;
		}
		const target = pendingDowngrade;
		setPendingDowngrade(null);
		setDowngradeReason('');
		await performUpdate(target, reason);
	}, [pendingDowngrade, downgradeReason, performUpdate]);

	const ownerCanEdit = internalIsOwner;
	const savedValue = internal || '';
	// Select 显示值优先用草稿，否则用已落库值。
	const selectShown = (draft ?? savedValue) || undefined;
	const isUnclassified = !savedValue;
	const hasDraftChange = draft != null && draft !== savedValue;

	if (!ownerCanEdit) {
		// 非 owner：只读 Tag。null 显示橙色「未设密级」警示。
		return isUnclassified ? (
			<Tag color="orange" style={{ margin: 0 }} title="该大屏未设密级，对所有登录用户可见">
				未设密级
			</Tag>
		) : (
			<Tag color={LEVEL_TAG_COLOR[savedValue] || 'default'} style={{ margin: 0 }}>
				{LEVEL_TAG_LABEL[savedValue] || savedValue}
			</Tag>
		);
	}

	const reasonValid = downgradeReason.trim().length >= DOWNGRADE_REASON_MIN_LENGTH;

	return (
		<>
			<div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
				<Select
					value={selectShown}
					placeholder="选择密级"
					onChange={(v) => handleSelectChange(v as string)}
					disabled={saving}
					size={size}
					style={{ width: 160 }}
					options={LEVEL_OPTIONS}
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
						未设密级，对所有登录用户可见
					</span>
				)}
			</div>
			{/* Sprint-24 F5：降级 confirm modal — 强制收集 reason，写入审计 */}
			<Modal
				open={!!pendingDowngrade}
				title="确认降低密级"
				okText="确认降级"
				cancelText="取消"
				okType="danger"
				onCancel={cancelDowngrade}
				onOk={confirmDowngrade}
				okButtonProps={{ disabled: !reasonValid || saving, loading: saving }}
				destroyOnClose
				maskClosable={false}
			>
				<div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
					<div style={{ fontSize: 13 }}>
						你正在把大屏密级从{' '}
						<Tag color={LEVEL_TAG_COLOR[savedValue] || 'default'} style={{ margin: 0 }}>
							{LEVEL_TAG_LABEL[savedValue] || savedValue || '未设'}
						</Tag>{' '}
						降低到{' '}
						<Tag color={LEVEL_TAG_COLOR[pendingDowngrade ?? ''] || 'default'} style={{ margin: 0 }}>
							{LEVEL_TAG_LABEL[pendingDowngrade ?? ''] || pendingDowngrade}
						</Tag>
						。降级会扩大可访问人群，请填写原因，写入审计后留档。
					</div>
					<div>
						<div style={{ fontSize: 12, fontWeight: 500, marginBottom: 4 }}>
							降级原因 <span style={{ color: '#ff4d4f' }}>*</span>
						</div>
						<Input.TextArea
							value={downgradeReason}
							onChange={(e) => setDowngradeReason(e.target.value)}
							placeholder={`请说明为什么需要降低密级，至少 ${DOWNGRADE_REASON_MIN_LENGTH} 个字符`}
							rows={3}
							maxLength={500}
							showCount
							autoFocus
						/>
					</div>
				</div>
			</Modal>
		</>
	);
}
