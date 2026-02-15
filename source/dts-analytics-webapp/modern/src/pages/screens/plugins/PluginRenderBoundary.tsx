import { Component, type CSSProperties, type ErrorInfo, type ReactNode } from 'react';

interface PluginRenderBoundaryProps {
    title?: string;
    children: ReactNode;
}

interface PluginRenderBoundaryState {
    hasError: boolean;
    message?: string;
}

export class PluginRenderBoundary extends Component<PluginRenderBoundaryProps, PluginRenderBoundaryState> {
    override state: PluginRenderBoundaryState = {
        hasError: false,
    };

    static getDerivedStateFromError(error: Error): PluginRenderBoundaryState {
        return {
            hasError: true,
            message: error?.message || '插件渲染异常',
        };
    }

    override componentDidCatch(error: Error, info: ErrorInfo): void {
        console.error('[screen-plugin] render failed:', error, info);
    }

    override render(): ReactNode {
        if (!this.state.hasError) {
            return this.props.children;
        }
        return (
            <div style={wrapperStyle}>
                <div style={titleStyle}>{this.props.title || '插件渲染失败'}</div>
                <div style={messageStyle}>{this.state.message || '未知错误'}</div>
                <div style={hintStyle}>已降级为组件级错误卡片，不影响其他组件。</div>
            </div>
        );
    }
}

const wrapperStyle: CSSProperties = {
    width: '100%',
    height: '100%',
    boxSizing: 'border-box',
    borderRadius: 8,
    border: '1px dashed rgba(239,68,68,0.6)',
    background: 'rgba(127,29,29,0.18)',
    color: '#fecaca',
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'center',
    padding: 10,
    gap: 6,
};

const titleStyle: CSSProperties = {
    fontSize: 12,
    fontWeight: 700,
};

const messageStyle: CSSProperties = {
    fontSize: 11,
    opacity: 0.95,
    wordBreak: 'break-word',
};

const hintStyle: CSSProperties = {
    fontSize: 10,
    opacity: 0.8,
};

