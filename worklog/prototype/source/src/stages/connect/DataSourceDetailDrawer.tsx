import { Descriptions, Drawer, Tag } from "antd";
import type { DataSource, DataSourceStatus } from "@/types/datasource";
import { StatusDot } from "@/ui/components";
import type { DotTone } from "@/ui/components";

const STATUS_TONE: Record<DataSourceStatus, DotTone> = { connected: "success", error: "error", untested: "muted" };
const STATUS_LABEL: Record<DataSourceStatus, string> = { connected: "已连通", error: "异常", untested: "未测试" };

/** 数据源详情抽屉。 */
export function DataSourceDetailDrawer({ source, onClose }: { source: DataSource | null; onClose: () => void }) {
	return (
		<Drawer open={Boolean(source)} title={source?.name} width={460} onClose={onClose}>
			{source ? (
				<Descriptions column={1} size="small" bordered>
					<Descriptions.Item label="归属">
						<Tag color={source.scope === "platform" ? "blue" : "default"}>
							{source.scope === "platform" ? "平台共享" : "本部门本地"}
						</Tag>
					</Descriptions.Item>
					<Descriptions.Item label="状态">
						<StatusDot tone={STATUS_TONE[source.status]} label={STATUS_LABEL[source.status]} />
					</Descriptions.Item>
					<Descriptions.Item label="系统类型">{source.type}</Descriptions.Item>
					<Descriptions.Item label="连接器">{source.connector}</Descriptions.Item>
					<Descriptions.Item label="JDBC URL">{source.jdbcUrl ?? "—"}</Descriptions.Item>
					<Descriptions.Item label="用户名">{source.username ?? "—"}</Descriptions.Item>
					<Descriptions.Item label="引擎版本">{source.engineVersion ?? "—"}</Descriptions.Item>
					<Descriptions.Item label="驱动版本">{source.driverVersion ?? "—"}</Descriptions.Item>
					<Descriptions.Item label="能力">
						{source.capabilities?.length ? source.capabilities.map((c) => <Tag key={c}>{c}</Tag>) : "—"}
					</Descriptions.Item>
					<Descriptions.Item label="负责">{source.owner ?? "—"}</Descriptions.Item>
					<Descriptions.Item label="最近连通">{source.lastTestedAt ?? "—"}</Descriptions.Item>
					<Descriptions.Item label="创建于">{source.createdAt ?? "—"}</Descriptions.Item>
					<Descriptions.Item label="描述">{source.description ?? "—"}</Descriptions.Item>
				</Descriptions>
			) : null}
		</Drawer>
	);
}
