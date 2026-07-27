import { Select, Space, Spin, Tag, Typography } from "antd";
import { type UIEvent, useCallback, useEffect, useRef, useState } from "react";
import { type DatasetField, getDatasetFields, listDatasets, listDomains } from "@/api/platformApi";

export type DatasetSelection = {
	id: string;
	name: string;
	hiveDatabase?: string;
	hiveTable?: string;
	warehouseLayer?: string;
};

type Props = {
	value?: string;
	onChange?: (datasetId: string | undefined) => void;
	onDatasetSelected?: (dataset: DatasetSelection | undefined) => void;
	onFieldsLoaded?: (fields: DatasetField[]) => void;
	placeholder?: string;
	style?: React.CSSProperties;
	disabled?: boolean;
	sourceId?: string;
	sourceName?: string;
};

const PAGE_SIZE = 50;
const LOAD_MORE_THRESHOLD = 24;
const UNASSIGNED_DOMAIN_VALUE = "__unassigned__";

const LAYER_COLOR: Record<string, string> = {
	ODS: "default",
	DWD: "blue",
	DWS: "cyan",
	ADS: "green",
};

function mergeDatasetOptions(current: DatasetSelection[], incoming: DatasetSelection[]) {
	const merged = new Map(current.map((item) => [item.id, item]));
	incoming.forEach((item) => merged.set(item.id, item));
	return Array.from(merged.values());
}

export function DatasetPicker({
	value,
	onChange,
	onDatasetSelected,
	onFieldsLoaded,
	placeholder,
	style,
	disabled,
	sourceId,
	sourceName,
}: Props) {
	const [options, setOptions] = useState<DatasetSelection[]>([]);
	const [loading, setLoading] = useState(false);
	const [loadingMore, setLoadingMore] = useState(false);
	const [fieldsLoading, setFieldsLoading] = useState(false);
	const [keyword, setKeyword] = useState("");
	const [page, setPage] = useState(0);
	const [total, setTotal] = useState<number>();
	const searchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
	const requestVersionRef = useRef(0);
	const loadingMoreRef = useRef(false);

	const handleSearch = (v: string) => {
		if (searchTimerRef.current) clearTimeout(searchTimerRef.current);
		searchTimerRef.current = setTimeout(() => setKeyword(v), 300);
	};
	const [selectedDomainId, setSelectedDomainId] = useState<string | undefined>();
	const [domains, setDomains] = useState<{ id: string; name: string }[]>([]);

	useEffect(() => {
		void (async () => {
			try {
				const resp: any = await listDomains(0, 200, "");
				const content = Array.isArray(resp?.content) ? resp.content : [];
				setDomains(
					content
						.map((d: any) => ({ id: String(d.id || ""), name: String(d.name || "").trim() }))
						.filter((d: any) => d.id && d.name),
				);
			} catch {
				/* global interceptor handles */
			}
		})();
	}, []);

	const loadDatasets = useCallback(
		async (kw: string, dId: string | undefined, nextPage: number, append: boolean, requestVersion: number) => {
			if (append) {
				loadingMoreRef.current = true;
				setLoadingMore(true);
			} else {
				setLoading(true);
			}
			try {
				const params: any = { page: nextPage, size: PAGE_SIZE, enabledOnly: true };
				if (kw) params.keyword = kw;
				if (dId === UNASSIGNED_DOMAIN_VALUE) {
					params.domainUnassigned = true;
				} else if (dId) {
					params.domainId = dId;
				}
				if (sourceId) params.sourceId = sourceId;
				const resp: any = await listDatasets(params);
				if (requestVersion !== requestVersionRef.current) return;
				const content = Array.isArray(resp?.content) ? resp.content : [];
				const nextOptions = content.map((d: any) => ({
					id: String(d.id || ""),
					name: String(d.name || ""),
					hiveDatabase: typeof d.hiveDatabase === "string" ? d.hiveDatabase.trim() || undefined : undefined,
					hiveTable: typeof d.hiveTable === "string" ? d.hiveTable.trim() || undefined : undefined,
					warehouseLayer: d.warehouseLayer,
				}));
				setOptions((current) => (append ? mergeDatasetOptions(current, nextOptions) : nextOptions));
				const responseTotal = Number(resp?.total);
				setTotal((current) =>
					Number.isFinite(responseTotal)
						? responseTotal
						: append
							? (current ?? 0) + nextOptions.length
							: nextOptions.length,
				);
				setPage(nextPage);
			} catch {
				/* global interceptor handles */
			} finally {
				if (requestVersion === requestVersionRef.current) {
					setLoading(false);
					setLoadingMore(false);
					loadingMoreRef.current = false;
				}
			}
		},
		[sourceId],
	);

	useEffect(() => {
		const requestVersion = requestVersionRef.current + 1;
		requestVersionRef.current = requestVersion;
		loadingMoreRef.current = false;
		setOptions([]);
		setPage(0);
		setTotal(undefined);
		setLoadingMore(false);
		if (disabled) {
			setLoading(false);
			return;
		}
		void loadDatasets(keyword, selectedDomainId, 0, false, requestVersion);
	}, [disabled, keyword, selectedDomainId, loadDatasets]);

	// Fix 1: trigger onFieldsLoaded when controlled value is injected externally (e.g. form.setFieldsValue)
	// biome-ignore lint/correctness/useExhaustiveDependencies: the controlled value is the trigger; existing callers pass inline callbacks.
	useEffect(() => {
		if (value && onFieldsLoaded) {
			void getDatasetFields(value)
				.then((fields) => onFieldsLoaded(fields))
				.catch(() => onFieldsLoaded([]));
		}
	}, [value]); // eslint-disable-line react-hooks/exhaustive-deps

	// Fix 2: cleanup debounce timer on unmount
	useEffect(() => {
		return () => {
			if (searchTimerRef.current) {
				clearTimeout(searchTimerRef.current);
			}
		};
	}, []);

	const handleChange = async (datasetId: string | undefined) => {
		onDatasetSelected?.(datasetId ? options.find((option) => option.id === datasetId) : undefined);
		onChange?.(datasetId);
		if (datasetId && onFieldsLoaded) {
			setFieldsLoading(true);
			try {
				const fields = await getDatasetFields(datasetId);
				onFieldsLoaded(fields);
			} catch {
				/* global interceptor handles */
			} finally {
				setFieldsLoading(false);
			}
		}
	};

	const handlePopupScroll = (event: UIEvent<HTMLDivElement>) => {
		const target = event.currentTarget;
		const reachedEnd = target.scrollTop + target.clientHeight >= target.scrollHeight - LOAD_MORE_THRESHOLD;
		if (!reachedEnd || loading || loadingMoreRef.current || total === undefined || options.length >= total) {
			return;
		}
		void loadDatasets(keyword, selectedDomainId, page + 1, true, requestVersionRef.current);
	};

	return (
		<div style={{ width: "100%", ...style }}>
			<Space style={{ width: "100%" }} wrap>
				<Select
					placeholder="筛选业务域"
					allowClear
					style={{ width: 160 }}
					disabled={disabled}
					options={[
						{ label: "未归属业务域", value: UNASSIGNED_DOMAIN_VALUE },
						...domains.map((d) => ({ label: d.name, value: d.id })),
					]}
					onChange={(v) => setSelectedDomainId(v || undefined)}
				/>
				<Select
					showSearch
					style={{ minWidth: 260 }}
					placeholder={placeholder ?? "选择数据资产"}
					filterOption={false}
					loading={loading}
					value={value}
					onChange={handleChange}
					onSearch={handleSearch}
					onPopupScroll={handlePopupScroll}
					allowClear
					disabled={disabled}
					dropdownRender={(menu) => (
						<>
							{menu}
							{loadingMore && (
								<div className="py-2 text-center text-gray-400">
									<Spin size="small" /> 正在加载更多
								</div>
							)}
						</>
					)}
					options={options.map((d) => ({
						label: (
							<Space size={4}>
								<span>{d.name}</span>
								{d.warehouseLayer && (
									<Tag color={LAYER_COLOR[d.warehouseLayer] ?? "processing"} style={{ fontSize: 10 }}>
										{d.warehouseLayer}
									</Tag>
								)}
							</Space>
						),
						value: d.id,
					}))}
				/>
				{fieldsLoading && <Spin size="small" />}
			</Space>
			{sourceName && (
				<div className="mt-2">
					<Typography.Text type="secondary">
						当前来源：{sourceName}
						{total !== undefined ? ` · 共 ${total} 条可选数据资产` : ""}
					</Typography.Text>
				</div>
			)}
		</div>
	);
}
