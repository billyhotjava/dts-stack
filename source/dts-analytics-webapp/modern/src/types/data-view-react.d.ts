// Type declarations for @jiaminghi/data-view-react
declare module '@jiaminghi/data-view-react' {
    import { CSSProperties, ReactNode } from 'react';

    interface BaseProps {
        style?: CSSProperties;
        className?: string;
        children?: ReactNode;
        color?: string[];
    }

    // Border Box Components
    export const BorderBox1: React.FC<BaseProps>;
    export const BorderBox2: React.FC<BaseProps>;
    export const BorderBox3: React.FC<BaseProps>;
    export const BorderBox4: React.FC<BaseProps & { reverse?: boolean }>;
    export const BorderBox5: React.FC<BaseProps & { reverse?: boolean }>;
    export const BorderBox6: React.FC<BaseProps>;
    export const BorderBox7: React.FC<BaseProps>;
    export const BorderBox8: React.FC<BaseProps & { dur?: number; reverse?: boolean }>;
    export const BorderBox9: React.FC<BaseProps>;
    export const BorderBox10: React.FC<BaseProps>;
    export const BorderBox11: React.FC<BaseProps & { title?: string; titleWidth?: number }>;
    export const BorderBox12: React.FC<BaseProps>;
    export const BorderBox13: React.FC<BaseProps>;

    // Decoration Components
    export const Decoration1: React.FC<BaseProps>;
    export const Decoration2: React.FC<BaseProps & { dur?: number; reverse?: boolean }>;
    export const Decoration3: React.FC<BaseProps>;
    export const Decoration4: React.FC<BaseProps & { dur?: number; reverse?: boolean }>;
    export const Decoration5: React.FC<BaseProps & { dur?: number }>;
    export const Decoration6: React.FC<BaseProps>;
    export const Decoration7: React.FC<BaseProps>;
    export const Decoration8: React.FC<BaseProps & { reverse?: boolean }>;
    export const Decoration9: React.FC<BaseProps & { dur?: number }>;
    export const Decoration10: React.FC<BaseProps>;
    export const Decoration11: React.FC<BaseProps>;
    export const Decoration12: React.FC<BaseProps & { scanDur?: number; haloDur?: number }>;

    // ScrollBoard
    interface ScrollBoardConfig {
        header?: string[];
        data?: (string | number)[][];
        rowNum?: number;
        headerBGC?: string;
        oddRowBGC?: string;
        evenRowBGC?: string;
        waitTime?: number;
        headerHeight?: number;
        columnWidth?: number[];
        align?: ('left' | 'center' | 'right')[];
        index?: boolean;
        indexHeader?: string;
        carousel?: 'single' | 'page';
        hoverPause?: boolean;
    }
    export const ScrollBoard: React.FC<{ config: ScrollBoardConfig; style?: CSSProperties; className?: string }>;

    // ScrollRankingBoard
    interface ScrollRankingBoardConfig {
        data?: { name: string; value: number }[];
        rowNum?: number;
        waitTime?: number;
        carousel?: 'single' | 'page';
        unit?: string;
        sort?: boolean;
        valueFormatter?: (item: { name: string; value: number }) => string;
    }
    export const ScrollRankingBoard: React.FC<{ config: ScrollRankingBoardConfig; style?: CSSProperties; className?: string }>;

    // WaterLevelPond
    interface WaterLevelPondConfig {
        data?: number[];
        shape?: 'rect' | 'round' | 'roundRect';
        colors?: string[];
        waveNum?: number;
        waveHeight?: number;
        waveOpacity?: number;
        formatter?: string;
    }
    export const WaterLevelPond: React.FC<{ config: WaterLevelPondConfig; style?: CSSProperties; className?: string }>;

    // DigitalFlop
    interface DigitalFlopConfig {
        number?: number[];
        content?: string;
        toFixed?: number;
        textAlign?: 'left' | 'center' | 'right';
        rowGap?: number;
        style?: {
            fontSize?: number;
            fill?: string;
            fontWeight?: string | number;
        };
        formatter?: (number: number[]) => string;
        animationCurve?: string;
        animationFrame?: number;
    }
    export const DigitalFlop: React.FC<{ config: DigitalFlopConfig; style?: CSSProperties; className?: string }>;

    // Percent (PercentPond)
    interface PercentConfig {
        value?: number;
        colors?: string[];
        borderWidth?: number;
        borderGap?: number;
        lineDash?: number[];
        textColor?: string;
        borderRadius?: number;
        localGradient?: boolean;
        formatter?: string;
    }
    export const Percent: React.FC<{ config: PercentConfig; style?: CSSProperties; className?: string }>;

    // Charts
    interface ChartsConfig {
        // Generic charts config
    }
    export const Charts: React.FC<{ option: ChartsConfig; style?: CSSProperties; className?: string }>;

    // FlylineChart
    interface FlylineChartConfig {
        points?: { name: string; coordinate: [number, number]; halo?: { show: boolean } }[];
        lines?: { source: string; target: string }[];
        icon?: { show: boolean; src: string };
        text?: { show: boolean };
        bgImgSrc?: string;
        k?: number;
        curvature?: number;
        flylineColor?: string;
        flylineRadius?: number;
        duration?: [number, number];
        relative?: boolean;
    }
    export const FlylineChart: React.FC<{ config: FlylineChartConfig; style?: CSSProperties; className?: string; dev?: boolean }>;
    export const FlylineChartEnhanced: React.FC<{ config: FlylineChartConfig; style?: CSSProperties; className?: string }>;

    // Capsule Chart
    interface CapsuleChartConfig {
        data?: { name: string; value: number }[];
        colors?: string[];
        unit?: string;
        showValue?: boolean;
    }
    export const CapsuleChart: React.FC<{ config: CapsuleChartConfig; style?: CSSProperties; className?: string }>;

    // ConicalColumnChart
    interface ConicalColumnChartConfig {
        data?: { name: string; value: number }[];
        img?: string[];
        fontSize?: number;
        imgSideLength?: number;
        columnColor?: string;
        textColor?: string;
        showValue?: boolean;
    }
    export const ConicalColumnChart: React.FC<{ config: ConicalColumnChartConfig; style?: CSSProperties; className?: string }>;

    // Loading
    export const Loading: React.FC<{ style?: CSSProperties; className?: string }>;

    // FullScreenContainer
    export const FullScreenContainer: React.FC<{ style?: CSSProperties; className?: string; children?: ReactNode }>;
}
