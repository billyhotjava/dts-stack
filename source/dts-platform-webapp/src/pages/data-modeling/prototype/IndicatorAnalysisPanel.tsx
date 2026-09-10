import { useEffect, useState } from "react";
import { Link } from "react-router";
import { analyticsApi } from "@/analytics/api/analyticsApi";
import api from "@/api/apiClient";
import type {
	IndicatorDefinition,
	IndicatorPredicate,
} from "@/features/modeling/indicators/indicatorDefinitionContract";
import { PredicateFields } from "./IndicatorAnalysisFields";
import { normalizeIndicatorFailure } from "./services/indicatorProjectionService";

type AnalysisResult = {
	columns: string[];
	rows: Record<string, unknown>[];
	queryId: string;
	resolvedVersions: { id: string; version: string }[];
	warnings: string[];
};
type Delivery = {
	status: string;
	version?: number;
	lastSyncError?: string;
	mapping?: { assetKey: string; analyticsMetricRef: string; semanticModelRef: string };
};

export function IndicatorAnalysisPanel({
	indicator,
	canMaintain,
}: {
	indicator: IndicatorDefinition;
	canMaintain: boolean;
}) {
	const [cards, setCards] = useState<Array<{ id: number; name: string }> | null>(null);
	const [usageFailure, setUsageFailure] = useState("");
	const [delivery, setDelivery] = useState<Delivery | null>(null);
	const [failure, setFailure] = useState("");
	const [deliveryFailure, setDeliveryFailure] = useState("");
	const [loading, setLoading] = useState(false);
	const [reload, setReload] = useState(0);
	const [dimensions, setDimensions] = useState<string[]>([]);
	const [filters, setFilters] = useState<IndicatorPredicate[]>([]);
	const [start, setStart] = useState("");
	const [end, setEnd] = useState("");
	const [limit, setLimit] = useState(200);
	const [result, setResult] = useState<AnalysisResult | null>(null);
	const config = indicator.analysisConfig;
	const root = `/governance/indicators/${indicator.id}/versions/${indicator.version}`;
	// biome-ignore lint/correctness/useExhaustiveDependencies: refresh token explicitly re-fetches the same immutable version.
	useEffect(() => {
		let current = true;
		setDelivery(null);
		setDeliveryFailure("");
		void api
			.get<Delivery>({ url: `${root}/analysis-status` })
			.then((value) => {
				if (current) setDelivery(value);
			})
			.catch((error) => {
				if (current) setDeliveryFailure(normalizeIndicatorFailure(error).message);
			});
		return () => {
			current = false;
		};
	}, [root, reload]);
	// biome-ignore lint/correctness/useExhaustiveDependencies: identity changes reset analysis even when the grain is unchanged.
	useEffect(() => {
		setResult(null);
		setDimensions(config?.resultGrain || []);
		setFilters([]);
		setFailure("");
	}, [indicator.id, indicator.version, config?.resultGrain]);
	// biome-ignore lint/correctness/useExhaustiveDependencies: refresh token reloads permitted usage.
	useEffect(() => {
		let current = true;
		setCards(null);
		setUsageFailure("");
		if (!indicator.id || !indicator.version) return;
		void analyticsApi
			.listIndicatorCards(indicator.id, indicator.version)
			.then((value) => {
				if (current) setCards(value);
			})
			.catch(() => {
				if (current) setUsageFailure("BI 使用记录暂时无法读取，请刷新重试。");
			});
		return () => {
			current = false;
		};
	}, [indicator.id, indicator.version, reload]);
	const run = async () => {
		setLoading(true);
		setFailure("");
		setResult(null);
		try {
			const time = config?.timeBinding;
			if (time && (!start || !end)) throw new Error("请选择时间起止范围");
			const response = await api.post<AnalysisResult>({
				url: "/governance/indicators/query",
				data: {
					indicatorRefs: [{ id: indicator.id, version: indicator.version }],
					dimensions,
					filters,
					limit,
					timeRange: time
						? {
								fieldRef: time.fieldRef,
								start: new Date(start).toISOString(),
								endExclusive: new Date(end).toISOString(),
								timezone: time.timezone,
							}
						: null,
				},
			});
			setResult(response);
		} catch (error) {
			setFailure(normalizeIndicatorFailure(error).message);
		} finally {
			setLoading(false);
		}
	};
	return (
		<section className="dmx-metric-section" aria-label="指标分析与使用状态">
			<h3>分析与使用 · {indicator.version}</h3>
			<p>
				分析注册：
				{delivery
					? { UNREGISTERED: "未注册", SYNC_PENDING: "注册中", SYNCED: "已就绪", SYNC_FAILED: "注册失败" }[
							delivery.status
						] || delivery.status
					: deliveryFailure || "加载中…"}
			</p>
			{delivery?.lastSyncError && <p role="alert">{delivery.lastSyncError}</p>}
			<button type="button" onClick={() => setReload((v) => v + 1)}>
				刷新注册状态
			</button>
			{canMaintain && delivery?.status === "SYNC_FAILED" && (
				<button
					type="button"
					disabled={loading}
					onClick={async () => {
						setLoading(true);
						setDeliveryFailure("");
						try {
							await api.post({ url: `${root}/analysis-retry`, data: { expectedVersion: delivery.version } });
							setReload((v) => v + 1);
						} catch (error) {
							setDeliveryFailure(normalizeIndicatorFailure(error).message);
						} finally {
							setLoading(false);
						}
					}}
				>
					重试注册
				</button>
			)}
			{deliveryFailure && <p role="alert">{deliveryFailure}</p>}
			{delivery?.mapping && (
				<p>
					资产：{delivery.mapping.assetKey}；BI 指标：{delivery.mapping.analyticsMetricRef}
					。卡片选择此版本后保持固定，升级需在卡片编辑器重新选择并保存。
				</p>
			)}
			<p>
				使用此版本的卡片：
				{usageFailure ||
					(cards === null
						? "加载中…"
						: cards.length
							? cards.map((card) => (
									<span key={card.id}>
										<Link to={`/bi/questions/${card.id}`}>{card.name}</Link>{" "}
									</span>
								))
							: "当前权限范围内暂无卡片")}
			</p>
			{delivery?.status === "SYNCED" && delivery.mapping && (
				<Link
					to={`/bi/card/new?base=${encodeURIComponent(delivery.mapping.semanticModelRef)}&metric=${encodeURIComponent(delivery.mapping.analyticsMetricRef)}`}
				>
					使用此版本创建 BI 卡片
				</Link>
			)}
			{!canMaintain && <p>当前账号可查看使用记录；试算需要指标维护权限。</p>}
			{!config ? (
				<p>该历史版本缺少分析配置，请创建修订补充维度、粒度和允许的聚合。</p>
			) : (
				<fieldset disabled={loading || !canMaintain}>
					<label>
						分组维度
						<select
							multiple
							value={dimensions}
							onChange={(e) => setDimensions(Array.from(e.target.selectedOptions, (option) => option.value))}
						>
							{Object.keys(config.dimensionBindings).map((key) => (
								<option key={key}>{key}</option>
							))}
						</select>
					</label>
					{config.timeBinding && (
						<div>
							<button
								type="button"
								onClick={() => {
									const now = new Date();
									const a = new Date(now.getFullYear(), 0, 1);
									const b = new Date(now.getFullYear() + 1, 0, 1);
									const local = (date: Date) =>
										new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
									setStart(local(a));
									setEnd(local(b));
								}}
							>
								本年
							</button>
							<button
								type="button"
								onClick={() => {
									const now = new Date();
									const local = (date: Date) =>
										new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
									setStart(local(new Date(now.getFullYear(), now.getMonth(), 1)));
									setEnd(local(new Date(now.getFullYear(), now.getMonth() + 1, 1)));
								}}
							>
								本月
							</button>
						</div>
					)}
					{config.timeBinding && (
						<div>
							<label>
								起始时间
								<input type="datetime-local" value={start} onChange={(e) => setStart(e.target.value)} />
							</label>
							<label>
								截止时间（不含）
								<input type="datetime-local" value={end} onChange={(e) => setEnd(e.target.value)} />
							</label>
							<p>业务时区：{config.timeBinding.timezone}；输入按当前浏览器本地时间转换为明确时刻。</p>
						</div>
					)}
					<PredicateFields fields={Object.keys(config.dimensionBindings)} values={filters} onChange={setFilters} />
					<label>
						结果上限
						<input type="number" min={1} max={1000} value={limit} onChange={(e) => setLimit(Number(e.target.value))} />
					</label>
					<button type="button" onClick={() => void run()}>
						{loading ? "分析中…" : "查询此版本"}
					</button>
				</fieldset>
			)}
			{failure && <p role="alert">{failure}</p>}
			{result && (
				<div>
					<p>
						查询编号：{result.queryId}；解析版本：
						{result.resolvedVersions.map((ref) => `${ref.id}@${ref.version}`).join("、")}
					</p>
					{result.rows.length ? (
						<div style={{ overflowX: "auto" }}>
							<table>
								<thead>
									<tr>
										{result.columns.map((column) => (
											<th key={column}>
												{column === "metric_0"
													? indicator.name
													: column === "metric_0_null_reason"
														? "空值原因"
														: column}
											</th>
										))}
									</tr>
								</thead>
								<tbody>
									{result.rows.map((row) => (
										<tr key={JSON.stringify(row)}>
											{result.columns.map((column) => (
												<td key={column}>{row[column] == null ? "—" : String(row[column])}</td>
											))}
										</tr>
									))}
								</tbody>
							</table>
						</div>
					) : (
						<p>所选范围暂无数据。</p>
					)}
				</div>
			)}
		</section>
	);
}
