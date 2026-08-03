import { listDomains } from "@/api/platformApi";

export type DataMartDomainOption = { id: string; code: string; name: string };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const payloadRows = (raw: unknown): Record<string, unknown>[] => {
	if (Array.isArray(raw))
		return raw.filter((item): item is Record<string, unknown> => Boolean(item && typeof item === "object"));
	if (!raw || typeof raw !== "object") return [];
	const record = raw as Record<string, unknown>;
	if (Array.isArray(record.content)) return payloadRows(record.content);
	if (Array.isArray(record.data)) return payloadRows(record.data);
	if (record.data && typeof record.data === "object") return payloadRows(record.data);
	return [];
};

export const loadDataMartDomainOptions = async (): Promise<DataMartDomainOption[]> =>
	payloadRows(await listDomains(0, 500, ""))
		.map((row) => ({
			id: String(row.id ?? "").trim(),
			code: String(row.code ?? "").trim(),
			name: String(row.name ?? row.nameZh ?? "").trim(),
		}))
		.filter((row) => Boolean(UUID.test(row.id) && row.code && row.name));
