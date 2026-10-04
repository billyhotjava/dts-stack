import type { ExplainabilityResponse } from '../../../../api/analyticsApi';
import { writeTextToClipboard } from '../../../../hooks/clipboard';
import { SectionToggle } from './SectionToggle';
import type { ExplainState } from './types';

interface ExplainConfigSectionOptions {
    canExplain: boolean;
    explainCardId: number | undefined;
    explainState: ExplainState | null;
    handleExplain: () => Promise<void>;
    isSectionCollapsed: (sectionKey: string) => boolean;
    toggleSection: (sectionKey: string) => void;
}

export function renderExplainConfig({
    canExplain,
    explainCardId,
    explainState,
    handleExplain,
    isSectionCollapsed,
    toggleSection,
}: ExplainConfigSectionOptions) {
    const isCollapsed = isSectionCollapsed('explain');

    return (
        <div className="property-section py-3 border-b border-border-default">
            <div className="property-section-title property-section-title-collapsible text-xs font-semibold text-text-secondary uppercase tracking-wide mb-2 flex items-center justify-between cursor-pointer select-none">
                <SectionToggle collapsed={isCollapsed} label="解释" onToggle={() => toggleSection('explain')} />
            </div>
            {!isCollapsed ? (
                canExplain ? (
                    <div style={{ display: 'grid', gap: 8 }}>
                        <button
                            type="button"
                            className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
                            onClick={() => { void handleExplain(); }}
                        >
                            解释当前组件
                        </button>
                        <div style={{ fontSize: 13, color: 'var(--color-text-secondary)', lineHeight: 1.45 }}>
                            解释来源 CardId: {explainCardId}
                        </div>
                        {explainState?.state === 'loading' ? (
                            <div style={{ fontSize: 13, color: 'var(--color-text-secondary)' }}>解释生成中...</div>
                        ) : null}
                        {explainState?.state === 'error' ? (
                            <div style={{ fontSize: 13, color: '#ef4444' }}>
                                解释失败：{explainState.error instanceof Error ? explainState.error.message : 'unknown error'}
                            </div>
                        ) : null}
                        {explainState?.state === 'loaded' ? renderExplainResult(explainState.value) : null}
                    </div>
                ) : (
                    <div style={{ fontSize: 13, color: 'var(--color-text-secondary)', lineHeight: 1.45 }}>
                        当前组件未绑定可解释的 Card 数据源。
                    </div>
                )
            ) : null}
        </div>
    );
}

function renderExplainResult(value: ExplainabilityResponse) {
    return (
        <>
            <button
                type="button"
                className="property-btn-small inline-flex items-center justify-center px-2 py-1 min-h-7 border border-border-default rounded bg-surface-card text-text-primary text-xs cursor-pointer transition-all duration-200 hover:border-brand hover:bg-brand/10 disabled:opacity-45 disabled:cursor-not-allowed"
                onClick={() => {
                    const text = value.copyJson ?? JSON.stringify(value.explainCard ?? {}, null, 2);
                    void writeTextToClipboard(text);
                }}
            >
                复制解释JSON
            </button>
            <pre
                style={{
                    margin: 0,
                    padding: 8,
                    borderRadius: 8,
                    background: 'rgba(15,23,42,0.6)',
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                    fontSize: 13,
                    maxHeight: 240,
                    overflow: 'auto',
                }}
            >
                {JSON.stringify(value.explainCard ?? {}, null, 2)}
            </pre>
        </>
    );
}
