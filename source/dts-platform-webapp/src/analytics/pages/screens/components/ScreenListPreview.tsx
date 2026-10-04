import { Alert, Button, Drawer, Spin } from "antd";
import { createContext, type PropsWithChildren, useContext, useState } from "react";
import type { ScreenListItem } from "../../../api/analyticsApi";
import { PageContainer } from "../../../components/PageContainer/PageContainer";
import { resolveRouteForOpen } from "../../../helpers/resolveAnalyticsUrl";

type PreviewScreen = Pick<ScreenListItem, "id" | "name" | "canRead">;
const PreviewContext = createContext<((screen: PreviewScreen) => void) | null>(null);

export function ScreenPreviewName({ screen }: { screen: PreviewScreen }) {
	const selectScreen = useContext(PreviewContext);
	return (
		<button
			type="button"
			className="max-w-full truncate text-left font-medium text-brand hover:underline disabled:cursor-not-allowed disabled:opacity-50"
			data-testid={`analytics-screen-name-link-${screen.id}`}
			title={screen.name || "未命名大屏"}
			disabled={screen.canRead === false || !selectScreen}
			onClick={() => selectScreen?.(screen)}
		>
			{screen.name || "未命名大屏"}
		</button>
	);
}

function ScreenPreviewFrame({ screen }: { screen: PreviewScreen }) {
	const [loading, setLoading] = useState(true);
	const [failed, setFailed] = useState(false);
	const [attempt, setAttempt] = useState(0);
	return (
		<div className="relative h-full min-h-[320px]">
			{loading && (
				<div className="absolute inset-0 flex items-center justify-center">
					<Spin tip="正在加载大屏预览" />
				</div>
			)}
			{failed ? (
				<Alert
					type="error"
					showIcon
					message="大屏预览加载失败"
					action={
						<Button
							onClick={() => {
								setFailed(false);
								setLoading(true);
								setAttempt((value) => value + 1);
							}}
						>
							重试
						</Button>
					}
				/>
			) : (
				<iframe
					key={attempt}
					title={`${screen.name || "未命名大屏"}预览`}
					src={resolveRouteForOpen(
						`/bi/screens/${encodeURIComponent(String(screen.id))}/preview?embed=1&scaleMode=fit`,
					)}
					className="relative h-full w-full border-0"
					style={{ visibility: loading ? "hidden" : "visible" }}
					onLoad={() => setLoading(false)}
					onError={() => {
						setLoading(false);
						setFailed(true);
					}}
				/>
			)}
		</div>
	);
}

export function ScreenListPreview({ children }: PropsWithChildren) {
	const [screen, setScreen] = useState<PreviewScreen | null>(null);
	return (
		<PreviewContext.Provider value={setScreen}>
			<PageContainer>
				{children}
				<Drawer
					title={`大屏预览${screen ? ` · ${screen.name || "未命名大屏"}` : ""}`}
					placement="right"
					width="min(760px, 90vw)"
					mask={false}
					open={screen !== null}
					onClose={() => setScreen(null)}
					destroyOnClose
					styles={{ body: { padding: 0 } }}
				>
					{screen && <ScreenPreviewFrame key={String(screen.id)} screen={screen} />}
				</Drawer>
			</PageContainer>
		</PreviewContext.Provider>
	);
}
