import { useState, useCallback, useMemo, createContext, useContext, useEffect, type ReactNode } from "react";
import "./gpmc.css";
import "./gpmc-bigscreen.css";

// ── Screen definitions ──
export type GpmcScreenId = "overview" | "execution" | "quality" | "tech-state" | "cost" | "risk";
export type GpmcBoardScreenId = Exclude<GpmcScreenId, "overview">;

export const GPMC_SCREENS: Array<{ id: GpmcScreenId; label: string; badge?: number }> = [
	{ id: "overview", label: "综合态势感知" },
	{ id: "execution", label: "项目执行监控" },
	{ id: "quality", label: "质量信息与跟进措施" },
	{ id: "tech-state", label: "技术状态与跟进" },
	{ id: "cost", label: "成本与预算控制" },
	{ id: "risk", label: "风险与预警中心" },
];

export function isGpmcScreenId(value: string): value is GpmcScreenId {
	return GPMC_SCREENS.some((screen) => screen.id === value);
}

export function isGpmcBoardScreenId(value: string): value is GpmcBoardScreenId {
	return value !== "overview" && isGpmcScreenId(value);
}

export function getGpmcScreenPath(screen: GpmcScreenId) {
	return screen === "overview" ? "/bi/gpmc" : `/bi/gpmc/${screen}`;
}

export function getGpmcDrillPath(screen: GpmcBoardScreenId) {
	return `/bi/gpmc/drill/${screen}`;
}

// ── Drill layer ──
export type GpmcDrillLayer = "strategic" | "control" | "execution";

const LAYER_LABELS: Record<GpmcDrillLayer, string> = {
	strategic: "战略层",
	control: "管控层",
	execution: "执行层",
};

// ── Context ──
type GpmcContextValue = {
	screen: GpmcScreenId;
	setScreen: (id: GpmcScreenId) => void;
	theme: "light" | "dark";
	toggleTheme: () => void;
	layer: GpmcDrillLayer;
	setLayer: (layer: GpmcDrillLayer) => void;
	drillDown: () => void;
	drillUp: () => void;
	fullscreen: boolean;
	toggleFullscreen: () => void;
	layerLabel: string;
};

const GpmcContext = createContext<GpmcContextValue | null>(null);

export function useGpmc() {
	const ctx = useContext(GpmcContext);
	if (!ctx) throw new Error("useGpmc must be used within GpmcApp");
	return ctx;
}

type GpmcAppProps = {
	children?: ReactNode;
	initialScreen?: GpmcScreenId;
	initialLayer?: GpmcDrillLayer;
	onScreenChange?: (screen: GpmcScreenId) => void;
	onLayerChange?: (layer: GpmcDrillLayer, screen: GpmcScreenId) => void;
	onDrillDown?: (screen: GpmcScreenId, layer: GpmcDrillLayer) => void;
};

// ── Provider ──
export default function GpmcApp({
	children,
	initialScreen = "overview",
	initialLayer = "strategic",
	onScreenChange,
	onLayerChange,
	onDrillDown,
}: GpmcAppProps) {
	const [screen, setScreen] = useState<GpmcScreenId>(initialScreen);
	const [theme, setTheme] = useState<"light" | "dark">("dark");
	const [layer, setLayerState] = useState<GpmcDrillLayer>(initialLayer);
	const [fullscreen, setFullscreen] = useState(false);

	useEffect(() => {
		setScreen(initialScreen);
	}, [initialScreen]);

	useEffect(() => {
		setLayerState(initialLayer);
	}, [initialLayer]);

	const toggleTheme = useCallback(() => setTheme((p) => (p === "light" ? "dark" : "light")), []);
	const toggleFullscreen = useCallback(() => setFullscreen((p) => !p), []);

	const handleSetLayer = useCallback(
		(nextLayer: GpmcDrillLayer) => {
			setLayerState(nextLayer);
			onLayerChange?.(nextLayer, screen);
		},
		[onLayerChange, screen],
	);

	const drillDown = useCallback(() => {
		if (onDrillDown) {
			onDrillDown(screen, layer);
			return;
		}
		handleSetLayer(layer === "strategic" ? "control" : "execution");
	}, [handleSetLayer, layer, onDrillDown, screen]);

	const drillUp = useCallback(() => {
		setLayerState((prev) => {
			if (prev === "execution") return "control";
			if (prev === "control") return "strategic";
			return prev;
		});
	}, []);

	const handleSetScreen = useCallback(
		(id: GpmcScreenId) => {
			setScreen(id);
			setLayerState(id === "overview" ? "strategic" : "control");
			onScreenChange?.(id);
		},
		[onScreenChange],
	);

	const value = useMemo<GpmcContextValue>(
		() => ({
			screen,
			setScreen: handleSetScreen,
			theme,
			toggleTheme,
			layer,
			setLayer: handleSetLayer,
			drillDown,
			drillUp,
			fullscreen,
			toggleFullscreen,
			layerLabel: LAYER_LABELS[layer],
		}),
		[screen, handleSetScreen, theme, toggleTheme, layer, handleSetLayer, drillDown, drillUp, fullscreen, toggleFullscreen],
	);

	return (
		<GpmcContext.Provider value={value}>
			<div
				className={`gpmc${fullscreen ? " gpmc--fullscreen" : ""}`}
				data-theme={layer === "strategic" ? "dark" : theme}
				data-mode={layer === "strategic" ? "bigscreen" : "dashboard"}
			>
				{children}
			</div>
		</GpmcContext.Provider>
	);
}
