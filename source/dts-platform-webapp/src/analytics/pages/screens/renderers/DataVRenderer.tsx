/**
 * DataVRenderer — renders DataV components.
 * Extracted from ComponentRenderer.tsx
 */
import type { ReactNode } from 'react';
import type { ScreenThemeTokens } from '../screenThemes';
import { BorderBox } from './datav/BorderBox';
import { Decoration } from './datav/Decoration';
import { DigitalFlop } from './datav/DigitalFlop';
import { FlylineChart } from './datav/FlylineChart';
import { PercentPond } from './datav/PercentPond';
import { ScrollRanking } from './datav/ScrollRanking';
import { WaterLevel } from './datav/WaterLevel';

interface DataVRendererProps {
    type: string;
    c: Record<string, unknown>;
    width: number;
    height: number;
    t?: ScreenThemeTokens;
    fontFamily?: string;
}

export function renderDataV({ type, c, width, height, t, fontFamily }: DataVRendererProps): ReactNode | null {
    switch (type) {
        case 'border-box':
            return (
                <BorderBox
                    boxType={(c.boxType as number) || 1}
                    color={c.color as string[] | undefined}
                    backgroundColor={c.backgroundColor as string | undefined}
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
                    backgroundColor={c.backgroundColor as string | undefined}
                    duration={c.duration as number | undefined}
                    style={{ width: '100%', height: '100%', fontFamily }}
                />
            );

        case 'scroll-ranking':
            return (
                <ScrollRanking
                    data={c.data as Array<{ name: string; value: number }>}
                    color={c.color as string[] | undefined}
                    textColor={c.textColor as string | undefined}
                    backgroundColor={c.backgroundColor as string | undefined}
                    duration={c.duration as number | undefined}
                    rowCount={c.rowNum as number | undefined}
                    style={{ width: '100%', height: '100%', fontFamily }}
                />
            );

        case 'water-level':
            return (
                <WaterLevel
                    value={c.value as number}
                    shape={c.shape as string}
                    color={c.color as string[]}
                    textColor={c.textColor as string | undefined}
                    backgroundColor={c.backgroundColor as string | undefined}
                    width={width}
                    height={height}
                />
            );

        case 'digital-flop':
            return (
                <DigitalFlop
                    number={Array.isArray(c.number) ? (c.number as number[])[0] : (c.number as number)}
                    content={c.content as string}
                    style={{ ...((c.style as { fontSize?: number; fill?: string }) || {}), fontFamily: String(c.fontFamily || fontFamily || '') || undefined }}
                    backgroundColor={c.backgroundColor as string | undefined}
                />
            );

        case 'percent-pond':
            return (
                <PercentPond
                    value={c.value as number}
                    colors={(c.colors as string[]) || [
                        t?.progressBar.fillGradient[0] ?? '#22d3ee',
                        t?.progressBar.fillGradient[1] ?? '#2563eb',
                    ]}
                    textColor={c.textColor as string | undefined}
                    backgroundColor={c.backgroundColor as string | undefined}
                    borderRadius={c.borderRadius as number}
                    borderWidth={c.borderWidth as number}
                    width={width}
                    height={height}
                />
            );

        case 'flyline-chart':
            return (
                <FlylineChart
                    points={c.points as Array<{ from: [number, number]; to: [number, number] }>}
                    color={c.color as string[]}
                    backgroundColor={c.backgroundColor as string | undefined}
                    duration={c.duration as number}
                    width={width}
                    height={height}
                />
            );

        default:
            return null;
    }
}
