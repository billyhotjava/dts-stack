import type {
    CarouselConfig,
    ScreenComponent,
    ScreenGlobalVariable,
    ScreenPage,
    ScreenTheme,
} from './types';
import type { ComponentV2, ScreenConfigV2, ScreenPageV2 } from './v2/types';

export interface ScreenV2SpecPayload {
    schemaVersion: 2;
    layout: ScreenConfigV2['layout'];
    referenceViewport?: ScreenConfigV2['referenceViewport'];
}

export interface ScreenUpdateConflictComponentSnapshot {
    id: string;
    component: ScreenComponent;
}

export interface ScreenUpdateConflictMeta {
    mode: 'component';
    baseUpdatedAt?: string | null;
    baseScreen: {
        name?: string | null;
        description?: string | null;
        width: number;
        height: number;
        backgroundColor?: string | null;
        backgroundImage?: string | null;
        theme?: ScreenTheme | null;
    };
    baseComponents: ScreenUpdateConflictComponentSnapshot[];
    baseVariables: ScreenGlobalVariable[];
}

export type ScreenWriteComponent = ScreenComponent | ComponentV2;
export type ScreenWritePage = ScreenPage | ScreenPageV2;

export interface ScreenWritePayload extends Record<string, unknown> {
    schemaVersion: number;
    name: string;
    description?: string;
    width: number;
    height: number;
    backgroundColor?: string;
    backgroundImage?: string;
    theme?: ScreenTheme;
    components: ScreenWriteComponent[];
    globalVariables: ScreenGlobalVariable[];
    pages: ScreenWritePage[];
    carouselConfig?: CarouselConfig;
    v2Spec?: ScreenV2SpecPayload;
    migrationFrom?: string;
    _conflict?: ScreenUpdateConflictMeta;
}
