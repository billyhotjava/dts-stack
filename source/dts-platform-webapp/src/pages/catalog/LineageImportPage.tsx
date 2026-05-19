import { useState } from "react";
import { } from "@ant-design/icons";
import { Alert, Button, Card, Space, Upload } from "antd";
import type { UploadProps } from "antd";
import { toast } from "sonner";
import { importDbtManifest, syncAddaxLineage } from "@/api/platformApi";
import { LineageSectionNav } from "./lineageShared";

export default function LineageImportPage() {
	const [syncingAddax, setSyncingAddax] = useState(false);

	const dbtUploadProps: UploadProps = {
		accept: ".json",
		showUploadList: false,
		beforeUpload: async (file) => {
			try {
				const result = await importDbtManifest(file as File);
				toast.success(`dbt 血缘导入成功：新建 ${result.created} 条，跳过 ${result.skipped} 条`);
			} catch {
				// global interceptor handles toast
			}
			return false;
		},
	};

	const handleSyncAddaxLineage = async () => {
		setSyncingAddax(true);
		try {
			const result: any = await syncAddaxLineage();
			toast.success(`Addax 血缘同步完成：新增 ${result?.created ?? 0}，更新 ${result?.updated ?? 0}，跳过 ${result?.skipped ?? 0}`);
		} catch {
			// global interceptor handles toast
		} finally {
			setSyncingAddax(false);
		}
	};

	return (
		<div className="space-y-4">
			<Card title="血缘与影响分析 / 血缘导入">
				<div className="mb-3"><LineageSectionNav section="import" /></div>
				<Alert type="info" showIcon message="血缘导入用于平台管理员维护血缘底座。业务人员应在指标发布页查看指标上下文血缘，不需要导入 dbt 或同步 Addax。" />
			</Card>
			<Card title="血缘来源同步">
				<Space wrap>
					<Button loading={syncingAddax} onClick={handleSyncAddaxLineage}>
						同步 Addax 血缘
					</Button>
					<Upload {...dbtUploadProps}>
						<Button>导入 dbt manifest</Button>
					</Upload>
				</Space>
			</Card>
		</div>
	);
}
