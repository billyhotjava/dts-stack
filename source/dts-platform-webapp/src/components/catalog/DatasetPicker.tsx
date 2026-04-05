import { useEffect, useState, useCallback, useRef } from "react";
import { Select, Space, Spin, Tag } from "antd";
import { listDatasets, listDomains, getDatasetFields, type DatasetField } from "@/api/platformApi";

type DatasetOption = {
	id: string;
	name: string;
	warehouseLayer?: string;
};

type Props = {
	value?: string;
	onChange?: (datasetId: string | undefined) => void;
	onFieldsLoaded?: (fields: DatasetField[]) => void;
	placeholder?: string;
	style?: React.CSSProperties;
	disabled?: boolean;
};

const LAYER_COLOR: Record<string, string> = {
	ODS: "default",
	DWD: "blue",
	DWS: "cyan",
	ADS: "green",
};

export function DatasetPicker({ value, onChange, onFieldsLoaded, placeholder, style, disabled }: Props) {
	const [options, setOptions] = useState<DatasetOption[]>([]);
	const [loading, setLoading] = useState(false);
	const [fieldsLoading, setFieldsLoading] = useState(false);
	const [keyword, setKeyword] = useState("");
	const searchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

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

	const loadDatasets = useCallback(async (kw: string, dId?: string) => {
		setLoading(true);
		try {
			const params: any = { page: 0, size: 50, enabledOnly: true };
			if (kw) params.keyword = kw;
			if (dId) params.domainId = dId;
			const resp: any = await listDatasets(params);
			const content = Array.isArray(resp?.content) ? resp.content : [];
			setOptions(
				content.map((d: any) => ({
					id: String(d.id || ""),
					name: String(d.name || ""),
					warehouseLayer: d.warehouseLayer,
				})),
			);
		} catch {
			/* global interceptor handles */
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		void loadDatasets(keyword, selectedDomainId);
	}, [keyword, selectedDomainId, loadDatasets]);

	const handleChange = async (datasetId: string | undefined) => {
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

	return (
		<Space style={{ width: "100%", ...style }} wrap>
			<Select
				placeholder="筛选主题域"
				allowClear
				style={{ width: 160 }}
				disabled={disabled}
				options={domains.map((d) => ({ label: d.name, value: d.id }))}
				onChange={(v) => setSelectedDomainId(v || undefined)}
			/>
			<Select
				showSearch
				style={{ minWidth: 260 }}
				placeholder={placeholder ?? "选择数据集"}
				filterOption={false}
				loading={loading}
				value={value}
				onChange={handleChange}
				onSearch={handleSearch}
				allowClear
				disabled={disabled}
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
	);
}
