import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Space } from "antd";
import { ExternalLink, RadioTower, RefreshCw } from "lucide-react";
import { useLocation } from "react-router";
import { LineLoading } from "@/components/loading";
import { PlatformMetaPill, PlatformPageHero } from "@/components/console-page";

type MetricsStatus = "checking" | "up" | "down";

type MetricsRouteInfo = {
	title: string;
	description: string;
	servicePath: string;
};

const routeInfoFor = (pathname: string): MetricsRouteInfo => {
	const suffix = pathname
		.replace(/^\/bi-apps\/metrics\/?/, "")
		.replace(/\/+$/, "");
	const normalized = suffix || "center";

	if (normalized === "center") {
		return {
			title: "指标工作台",
			description: "在 DTS 平台内查看 dts-metrics 服务状态、指标包交付边界和建模入口。",
			servicePath: "/metrics/center",
		};
	}
	if (normalized === "dictionary" || normalized === "assets") {
		return {
			title: "指标资产",
			description: "沉淀指标名称、口径、公式、负责人、版本和指标包校验结果。",
			servicePath: "/metrics/dictionary",
		};
	}
	if (normalized === "publish" || normalized === "semantic/publish") {
		return {
			title: "发布与运行",
			description: "承接语义模型审核、SQL 预览、数据质量检查、血缘注册和运行监控。",
			servicePath: "/metrics/semantic/publish",
		};
	}
	if (normalized === "operations") {
		return {
			title: "指标运营台",
			description: "跟踪指标资产、发布流程、模型运行和平台事件之间的运营状态。",
			servicePath: "/metrics/operations",
		};
	}
	if (normalized.startsWith("semantic")) {
		return {
			title: "语义建模",
			description: "围绕主题域、业务对象、维度、指标公式和 DWS/ADS 数据集组织建模流程。",
			servicePath: `/metrics/${normalized}`,
		};
	}

	return {
		title: "指标与语义",
		description: "平台保留统一菜单、登录态和权限边界，指标服务负责业务页面和模型能力。",
		servicePath: `/metrics/${normalized}`,
	};
};

const withEmbeddedMode = (servicePath: string, search: string, hash: string) => {
	const params = new URLSearchParams(search);
	params.set("embedded", "1");
	const query = params.toString();
	return `${servicePath}${query ? `?${query}` : ""}${hash}`;
};

const withoutEmbeddedMode = (servicePath: string, search: string, hash: string) => {
	const params = new URLSearchParams(search);
	params.delete("embedded");
	const query = params.toString();
	return `${servicePath}${query ? `?${query}` : ""}${hash}`;
};

export default function MetricsBridgePage() {
	const location = useLocation();
	const routeInfo = useMemo(() => routeInfoFor(location.pathname), [location.pathname]);
	const frameSrc = useMemo(
		() => withEmbeddedMode(routeInfo.servicePath, location.search, location.hash),
		[location.hash, location.search, routeInfo.servicePath],
	);
	const standaloneHref = useMemo(
		() => withoutEmbeddedMode(routeInfo.servicePath, location.search, location.hash),
		[location.hash, location.search, routeInfo.servicePath],
	);
	const [status, setStatus] = useState<MetricsStatus>("checking");
	const [statusText, setStatusText] = useState("检查 dts-metrics 服务中");
	const [frameLoading, setFrameLoading] = useState(true);
	const [reloadNonce, setReloadNonce] = useState(0);

	useEffect(() => {
		let cancelled = false;
		setStatus("checking");
		setStatusText("检查 dts-metrics 服务中");
		fetch("/api/metrics/health", {
			credentials: "same-origin",
			headers: { Accept: "application/json" },
		})
			.then(async (response) => {
				if (!response.ok) {
					throw new Error(`${response.status} ${response.statusText}`);
				}
				const payload = await response.json().catch(() => ({}));
				if (cancelled) return;
				setStatus("up");
				setStatusText(`${payload?.service || "dts-metrics"} ${payload?.status || "UP"}`);
			})
			.catch((error) => {
				if (cancelled) return;
				setStatus("down");
				setStatusText(String(error?.message || "服务状态不可用"));
			});
		return () => {
			cancelled = true;
		};
	}, []);

	useEffect(() => {
		setFrameLoading(true);
	}, [frameSrc, reloadNonce]);

	return (
		<div className="flex min-h-[calc(100vh-132px)] flex-col gap-4">
			<PlatformPageHero
				title={routeInfo.title}
				actions={
					<Space wrap>
						<PlatformMetaPill>
							<RadioTower className="h-3.5 w-3.5" />
							<span>{statusText}</span>
						</PlatformMetaPill>
						<Button href={standaloneHref} target="_blank" rel="noreferrer" icon={<ExternalLink className="h-4 w-4" />}>
							新窗口打开
						</Button>
						<Button icon={<RefreshCw className="h-4 w-4" />} onClick={() => setReloadNonce((value) => value + 1)}>
							刷新
						</Button>
					</Space>
				}
			/>

			{status === "down" ? (
				<Alert
					type="warning"
					showIcon
					message="指标与语义服务暂不可用"
					description={`${routeInfo.description} 请确认 dts-metrics 容器、/api/metrics 转发和平台登录态是否正常。`}
				/>
			) : null}

			<div className="relative flex-1 overflow-hidden rounded-xl border border-border/70 bg-white shadow-sm">
				{frameLoading ? (
					<div className="absolute inset-x-0 top-0 z-10 bg-background/80">
						<LineLoading />
					</div>
				) : null}
				<iframe
					key={`${frameSrc}:${reloadNonce}`}
					src={frameSrc}
					title={`DTS ${routeInfo.title}`}
					className="block min-h-[calc(100vh-220px)] w-full border-0 bg-white"
					onLoad={() => setFrameLoading(false)}
				/>
			</div>
		</div>
	);
}
