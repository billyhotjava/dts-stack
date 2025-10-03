import AdminGuard from "@/admin/lib/guard";
import PortalMenusView from "@/admin/views/portal-menus";
import { Alert, AlertDescription, AlertTitle } from "@/ui/alert";
import { Separator } from "@/ui/separator";

export default function PortalMenuManagementPage() {
	return (
		<div className="space-y-6">
			<Alert variant="default">
				<AlertTitle>菜单管理(审批版)</AlertTitle>
				<AlertDescription>
					当前页面已切换至门户菜单审批管理视图，数据会直接读取并刷新后端存储的菜单项。
					如需恢复出厂菜单，请使用页面右上角的“恢复默认菜单”按钮。
				</AlertDescription>
			</Alert>
			<Separator />
			<AdminGuard>
				<PortalMenusView />
			</AdminGuard>
		</div>
	);
}
