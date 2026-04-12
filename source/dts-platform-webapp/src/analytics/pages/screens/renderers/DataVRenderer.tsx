/**
 * DataVRenderer — renders DataV border-box and decoration components.
 * Extracted from ComponentRenderer.tsx
 */
import type { ReactNode } from 'react';
import { BorderBox } from './datav/BorderBox';
import { Decoration } from './datav/Decoration';

interface DataVRendererProps {
    type: string;
    c: Record<string, unknown>;
    width: number;
    height: number;
}

export function renderDataV({ type, c, width, height }: DataVRendererProps): ReactNode | null {
    switch (type) {
        case 'border-box':
            return (
                <BorderBox
                    boxType={(c.boxType as number) || 1}
                    color={c.color as string[] | undefined}
                    duration={c.duration as number | undefined}
                    width={width}
                    height={height}
                >
                    {c.children as ReactNode}
                </BorderBox>
            );

        case 'decoration':
            return (
                <Decoration
                    decorationType={(c.decorationType as number) || 1}
                    color={c.color as string[] | undefined}
                    duration={c.duration as number | undefined}
                    style={{ width: '100%', height: '100%' }}
                />
            );

        default:
            return null;
    }
}
