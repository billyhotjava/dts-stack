import type {
	CarouselConfig,
	ScreenComponent,
	ScreenCustomTheme,
	ScreenGlobalVariable,
	ScreenPage,
	ScreenTheme,
} from "./types";

export interface ScreenUpdateConflictComponentSnapshot {
	id: string;
	component: ScreenComponent;
}

export interface ScreenUpdateConflictMeta {
	mode: "component";
	baseUpdatedAt?: string | null;
	baseScreen: {
		name?: string | null;
		description?: string | null;
		width: number;
		height: number;
		backgroundColor?: string | null;
		backgroundImage?: string | null;
		fontFamily?: string | null;
		theme?: ScreenTheme | null;
	};
	baseComponents: ScreenUpdateConflictComponentSnapshot[];
	baseVariables: ScreenGlobalVariable[];
}

export type ScreenWriteComponent = ScreenComponent;
export type ScreenWritePage = ScreenPage;

export interface ScreenWritePayload extends Record<string, unknown> {
	schemaVersion: number;
	name: string;
	description?: string;
	width: number;
	height: number;
	backgroundColor?: string;
	backgroundImage?: string;
	fontFamily?: string;
	theme?: ScreenTheme;
	customTheme?: ScreenCustomTheme;
	components: ScreenWriteComponent[];
	globalVariables: ScreenGlobalVariable[];
	pages: ScreenWritePage[];
	carouselConfig?: CarouselConfig;
	migrationFrom?: string;
	// Sprint-24 F3：创建大屏强制必填，老版 update 路径仍可省略。
	classification?: "PUBLIC" | "INTERNAL" | "SECRET" | "CONFIDENTIAL";
	domainId?: string;
	_conflict?: ScreenUpdateConflictMeta;
}
