import type { CSSProperties } from "react";
import bgImg from "@/assets/images/background/banner-1.png";
import DataPlatform from "@/assets/images/illustrations/data-platform.svg";
import { Icon } from "@/components/icon";
import { GLOBAL_CONFIG } from "@/global-config";
import { Button } from "@/ui/button";
import { Text, Title } from "@/ui/typography";

export default function BannerCard() {
	const bgStyle: CSSProperties = {
		position: "absolute",
		top: 0,
		left: 0,
		right: 0,
		bottom: 0,
		// !  When passing a URL of SVG to a manually constructed url() by JS, the variable should be wrapped within double quotes.
		// ! https://vite.dev/guide/assets.html
		backgroundImage: `url("${bgImg}")`,
		backgroundSize: "100%",
		backgroundPosition: "50%",
		backgroundRepeat: "no-repeat",
		opacity: 0.5,
	};
	return (
		<div className="relative bg-primary/90">
			<div className="p-6 z-2 relative">
				<div className="grid grid-cols-2 gap-4">
					<div className="col-span-2 md:col-span-1">
						<div className="flex flex-col gap-4">
							<Title as="h2" className="text-white">
								构建可信赖的大数据管理中枢
							</Title>
							<Text className="text-white">
								掌控从采集、治理到分发的全链路数据流程，依托 {GLOBAL_CONFIG.appName} 快速洞察平台运行态势。
							</Text>

							<Button
								variant="outline"
								className="w-fit bg-white text-black"
								onClick={() => window.open("https://github.com/topics/data-governance")}
							>
								<Icon icon="solar:database-linear" size={22} />
								<span className="ml-2 font-semibold">了解治理方案</span>
							</Button>
						</div>
					</div>

					<div className="col-span-2 md:col-span-1">
						<div className="w-full h-full flex items-center justify-end">
							<img src={DataPlatform} className="w-[15rem] h-[15rem]" alt="data-platform-visual" />
						</div>
					</div>
				</div>
			</div>
			<div style={bgStyle} className="z-1" />
		</div>
	);
}
