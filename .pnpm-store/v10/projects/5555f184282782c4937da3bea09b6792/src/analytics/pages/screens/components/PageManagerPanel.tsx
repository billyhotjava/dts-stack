/**
 * PageManagerPanel — 多页管理面板
 *
 * Displayed at the bottom of the designer canvas.
 * Allows creating, deleting, duplicating, reordering, and renaming pages.
 */
import { useState, useCallback, useRef, useEffect } from 'react';
import type { ScreenPage } from '../types';

interface PageManagerPanelProps {
	pages: ScreenPage[];
	currentPageIndex: number;
	onSwitchPage: (index: number) => void;
	onAddPage: () => void;
	onDeletePage: (index: number) => void;
	onDuplicatePage: (index: number) => void;
	onRenamePage: (index: number, name: string) => void;
	onMovePage: (fromIndex: number, toIndex: number) => void;
}

export function PageManagerPanel({
	pages,
	currentPageIndex,
	onSwitchPage,
	onAddPage,
	onDeletePage,
	onDuplicatePage,
	onRenamePage,
	onMovePage,
}: PageManagerPanelProps) {
	const [contextMenuIndex, setContextMenuIndex] = useState<number | null>(null);
	const [renamingIndex, setRenamingIndex] = useState<number | null>(null);
	const [renameValue, setRenameValue] = useState('');
	const renameInputRef = useRef<HTMLInputElement>(null);

	// Close context menu on outside click
	useEffect(() => {
		if (contextMenuIndex === null) return;
		const handler = () => setContextMenuIndex(null);
		window.addEventListener('click', handler);
		return () => window.removeEventListener('click', handler);
	}, [contextMenuIndex]);

	// Focus rename input
	useEffect(() => {
		if (renamingIndex !== null && renameInputRef.current) {
			renameInputRef.current.focus();
			renameInputRef.current.select();
		}
	}, [renamingIndex]);

	const handleContextMenu = useCallback((e: React.MouseEvent, index: number) => {
		e.preventDefault();
		e.stopPropagation();
		setContextMenuIndex(index);
	}, []);

	const handleStartRename = useCallback((index: number) => {
		setRenamingIndex(index);
		setRenameValue(pages[index]?.name ?? '');
		setContextMenuIndex(null);
	}, [pages]);

	const handleFinishRename = useCallback(() => {
		if (renamingIndex !== null && renameValue.trim()) {
			onRenamePage(renamingIndex, renameValue.trim());
		}
		setRenamingIndex(null);
	}, [renamingIndex, renameValue, onRenamePage]);

	if (pages.length <= 1 && pages.length > 0) {
		// Single page — show minimal bar with add button
	}

	return (
		<div
			className="flex items-center overflow-auto"
			style={{
				gap: 8,
				minHeight: 64,
				padding: '10px 14px',
				border: '1px solid rgba(255,255,255,0.08)',
				borderRadius: 8,
				background: 'rgba(255,255,255,0.04)',
			}}
		>
			{pages.map((page, idx) => {
				const isActive = idx === currentPageIndex;
				const isRenaming = idx === renamingIndex;

				return (
					<div
						key={page.id}
						className="relative inline-flex items-center cursor-pointer"
						style={{
							gap: 8,
							minHeight: 40,
							padding: '0 14px',
							border: isActive
								? '1px solid rgba(80,158,227,0.24)'
								: '1px solid rgba(255,255,255,0.08)',
							borderRadius: 6,
							background: isActive
								? 'rgba(80,158,227,0.12)'
								: 'rgba(255,255,255,0.04)',
							color: isActive
								? 'var(--color-text-primary)'
								: 'var(--color-text-secondary)',
							whiteSpace: 'nowrap',
							transition: 'border-color 0.15s, background-color 0.15s, transform 0.15s',
						}}
						onClick={() => onSwitchPage(idx)}
						onContextMenu={(e) => handleContextMenu(e, idx)}
						onDoubleClick={() => handleStartRename(idx)}
					>
						<span
							className="text-xs font-bold"
							style={{ letterSpacing: '0.08em', color: 'var(--color-text-tertiary)', fontSize: 10 }}
						>
							{idx + 1}
						</span>
						{isRenaming ? (
							<input
								ref={renameInputRef}
								className="text-primary"
								style={{
									width: 108,
									minHeight: 30,
									padding: '0 10px',
									border: '1px solid rgba(64,158,255,0.3)',
									borderRadius: 6,
									background: 'rgba(255,255,255,0.06)',
									color: 'var(--color-text-primary)',
									outline: 'none',
								}}
								value={renameValue}
								onChange={(e) => setRenameValue(e.target.value)}
								onBlur={handleFinishRename}
								onKeyDown={(e) => {
									if (e.key === 'Enter') handleFinishRename();
									if (e.key === 'Escape') setRenamingIndex(null);
								}}
								onClick={(e) => e.stopPropagation()}
							/>
						) : (
							<span className="text-xs font-semibold" style={{ color: 'inherit' }}>{page.name}</span>
						)}

						{contextMenuIndex === idx && (
							<div
								className="absolute grid"
								style={{
									top: 'calc(100% + 8px)',
									left: 0,
									zIndex: 10001,
									gap: 6,
									minWidth: 144,
									padding: 8,
									border: '1px solid rgba(255,255,255,0.08)',
									borderRadius: 8,
									background: 'var(--color-bg-secondary)',
									boxShadow: '0 8px 24px rgba(0,0,0,0.4)',
								}}
								onClick={(e) => e.stopPropagation()}
							>
								<button
									type="button"
									className="text-left cursor-pointer text-primary text-xs"
									style={{
										minHeight: 34,
										padding: '0 10px',
										border: '1px solid rgba(255,255,255,0.08)',
										borderRadius: 6,
										background: 'rgba(255,255,255,0.04)',
									}}
									onClick={() => { handleStartRename(idx); }}
								>
									重命名
								</button>
								<button
									type="button"
									className="text-left cursor-pointer text-primary text-xs"
									style={{
										minHeight: 34,
										padding: '0 10px',
										border: '1px solid rgba(255,255,255,0.08)',
										borderRadius: 6,
										background: 'rgba(255,255,255,0.04)',
									}}
									onClick={() => { onDuplicatePage(idx); setContextMenuIndex(null); }}
								>
									复制页面
								</button>
								{idx > 0 && (
									<button
										type="button"
										className="text-left cursor-pointer text-primary text-xs"
										style={{
											minHeight: 34,
											padding: '0 10px',
											border: '1px solid rgba(255,255,255,0.08)',
											borderRadius: 6,
											background: 'rgba(255,255,255,0.04)',
										}}
										onClick={() => { onMovePage(idx, idx - 1); setContextMenuIndex(null); }}
									>
										前移
									</button>
								)}
								{idx < pages.length - 1 && (
									<button
										type="button"
										className="text-left cursor-pointer text-primary text-xs"
										style={{
											minHeight: 34,
											padding: '0 10px',
											border: '1px solid rgba(255,255,255,0.08)',
											borderRadius: 6,
											background: 'rgba(255,255,255,0.04)',
										}}
										onClick={() => { onMovePage(idx, idx + 1); setContextMenuIndex(null); }}
									>
										后移
									</button>
								)}
								{pages.length > 1 && (
									<button
										type="button"
										className="text-left cursor-pointer text-xs"
										style={{
											minHeight: 34,
											padding: '0 10px',
											border: '1px solid rgba(255,255,255,0.08)',
											borderRadius: 6,
											background: 'rgba(255,255,255,0.04)',
											color: '#c2410c',
										}}
										onClick={() => {
											if (window.confirm(`确认删除页面"${page.name}"？`)) {
												onDeletePage(idx);
											}
											setContextMenuIndex(null);
										}}
									>
										删除页面
									</button>
								)}
							</div>
						)}
					</div>
				);
			})}

			<button
				type="button"
				className="flex-none cursor-pointer text-secondary"
				style={{
					width: 40,
					height: 40,
					borderRadius: 18,
					border: '1px dashed rgba(148,163,184,0.3)',
					background: 'transparent',
					fontSize: 18,
				}}
				onClick={onAddPage}
				title="添加页面"
			>
				+
			</button>

			<span
				className="text-xs font-bold uppercase text-tertiary"
				style={{ marginLeft: 'auto', flexShrink: 0, letterSpacing: '0.08em' }}
			>
				{pages.length} 页
			</span>
		</div>
	);
}
