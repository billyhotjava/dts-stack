/**
 * DataVRenderer — renders DataV border-box and decoration components.
 * Extracted from ComponentRenderer.tsx
 */
import type { ReactNode, ComponentType } from 'react';

interface DataVRendererProps {
    type: string;
    c: Record<string, unknown>;
    borderBoxComponents: Record<number, ComponentType<{ children?: ReactNode; color?: string[] }>> | null;
    decorationComponents: Record<number, ComponentType<{ color?: string[]; style?: React.CSSProperties }>> | null;
    renderUnavailableState: (title: string, detail?: string) => ReactNode;
}

export function renderDataV({ type, c, borderBoxComponents, decorationComponents, renderUnavailableState }: DataVRendererProps): ReactNode | null {
    switch (type) {
        case 'border-box': {
            const boxType = (c.boxType as number) || 1;
            const BorderBoxComponent = borderBoxComponents?.[boxType] || borderBoxComponents?.[1];
            if (!BorderBoxComponent) return renderUnavailableState('DataV 运行时未就绪');
            const colors = c.color as string[] | undefined;
            return (
                <BorderBoxComponent color={colors}>
                    <div style={{ width: '100%', height: '100%', padding: 16 }}>
                        {c.children as ReactNode}
                    </div>
                </BorderBoxComponent>
            );
        }

        case 'decoration': {
            const decorationType = (c.decorationType as number) || 1;
            const DecorationComponent = decorationComponents?.[decorationType] || decorationComponents?.[1];
            if (!DecorationComponent) return renderUnavailableState('DataV 运行时未就绪');
            const colors = c.color as string[] | undefined;
            return (
                <DecorationComponent color={colors} style={{ width: '100%', height: '100%' }} />
            );
        }

        default:
            return null;
    }
}
