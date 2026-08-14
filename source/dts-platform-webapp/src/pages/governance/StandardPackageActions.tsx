import { Button, Card, Space, Typography } from "antd";
import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { toast } from "sonner";
import { downloadDataStandardPackageTemplate } from "@/api/platformApi";
import { useGovernanceManageAccess } from "@/hooks/useModuleManageAccess";
import { buildStandardPackageImportRoute, type StandardPackageSource } from "./standardOwnerNavigation";

const { Text } = Typography;

export function StandardPackageActions({ source }: { source: StandardPackageSource }) {
	const navigate = useNavigate();
	const [searchParams] = useSearchParams();
	const [downloading, setDownloading] = useState(false);
	const canManage = useGovernanceManageAccess();

	const downloadTemplate = async () => {
		if (!canManage) {
			toast.error("当前账号无治理维护权限");
			return;
		}
		setDownloading(true);
		try {
			const blob = await downloadDataStandardPackageTemplate();
			const url = URL.createObjectURL(blob);
			const link = document.createElement("a");
			link.href = url;
			link.download = "data-standard-package-template.zip";
			document.body.appendChild(link);
			link.click();
			link.remove();
			URL.revokeObjectURL(url);
			toast.success("标准包模板已下载");
		} catch (error: any) {
			toast.error(error?.message || "下载标准包模板失败");
		} finally {
			setDownloading(false);
		}
	};

	return (
		<div className="px-4 pt-4" data-testid={`governance-${source}-standard-package-actions`}>
			<Card
				size="small"
				title="标准包批量维护"
				extra={
					<Space wrap>
						<Button
							onClick={() => void downloadTemplate()}
							loading={downloading}
							disabled={!canManage}
							data-testid={`governance-${source}-template-download`}
						>
							下载标准包模板
						</Button>
						<Button
							type="primary"
							onClick={() => navigate(buildStandardPackageImportRoute(searchParams, source))}
							disabled={!canManage}
							data-testid={`governance-${source}-standard-package-import`}
						>
							导入标准包
						</Button>
					</Space>
				}
			>
				<Text type="secondary">线下组织编写或由工具生成 ZIP 后，在统一向导中完成预检、应用和整包回滚。</Text>
			</Card>
		</div>
	);
}
