import { Button, message } from "antd";
import { type ReactNode, useEffect, useRef, useState } from "react";
import "./PortalPreviewFrame.css";

export function PortalPreviewFrame({
	children,
	dark,
	available,
}: {
	children: ReactNode;
	dark: boolean;
	available: boolean;
}) {
	const frame = useRef<HTMLElement>(null);
	const [fullscreen, setFullscreen] = useState(false);
	useEffect(() => {
		const update = () => setFullscreen(Boolean(frame.current && document.fullscreenElement === frame.current));
		document.addEventListener("fullscreenchange", update);
		return () => document.removeEventListener("fullscreenchange", update);
	}, []);
	const toggleFullscreen = async () => {
		try {
			if (document.fullscreenElement === frame.current) await document.exitFullscreen();
			else if (frame.current?.requestFullscreen) await frame.current.requestFullscreen();
			else message.warning("当前浏览器不支持全屏预览");
		} catch {
			message.warning("无法进入或退出全屏，请重试或按 Esc 返回");
		}
	};
	return (
		<section
			ref={frame}
			className={`portal-preview-frame min-h-[520px] min-w-0 flex-1 overflow-auto rounded-xl border border-solid border-border ${dark ? "bg-[#08121f]" : "bg-bg-container"}`}
		>
			{available ? (
				<div className="sticky top-0 z-10 flex justify-end border-0 border-b border-solid border-border bg-bg-container px-4 py-2">
					<Button onClick={() => void toggleFullscreen()}>{fullscreen ? "退出全屏" : "全屏预览"}</Button>
				</div>
			) : null}
			{children}
		</section>
	);
}
