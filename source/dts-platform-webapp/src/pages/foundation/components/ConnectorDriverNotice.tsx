import { Alert, Button } from "antd";
import type { ConnectorDriverBinding } from "@/api/services/connectorsService";

type ConnectorDriverNoticeProps = {
	driver?: ConnectorDriverBinding;
	onManageDrivers: () => void;
};

export function ConnectorDriverNotice({ driver, onManageDrivers }: ConnectorDriverNoticeProps) {
	if (!driver || driver.status === "NOT_REQUIRED") {
		return null;
	}
	if (driver.status === "READY") {
		const details = [
			driver.fileName,
			driver.version ? `版本 ${driver.version}` : null,
			driver.jdkSpec ? `JDK ${driver.jdkSpec}` : null,
		]
			.filter(Boolean)
			.join(" · ");
		return (
			<Alert
				type="success"
				showIcon
				className="mb-4"
				message="连接器驱动已就绪"
				description={details || driver.driverClass || driver.message}
			/>
		);
	}
	if (driver.status === "CUSTOM_REQUIRED") {
		return (
			<Alert
				type="info"
				showIcon
				className="mb-4"
				message="请选择通用 JDBC 驱动"
				description="通用 JDBC 是扩展入口，需要从管理员已安装的驱动中明确选择；标准连接器无需此步骤。"
				action={
					<Button size="small" onClick={onManageDrivers}>
						驱动管理
					</Button>
				}
			/>
		);
	}
	return (
		<Alert
			type="error"
			showIcon
			className="mb-4"
			message="连接器驱动未就绪"
			description={driver.message || "该连接器所需驱动不在当前部署包中，请联系管理员补充后再创建数据源。"}
			action={
				<Button size="small" danger onClick={onManageDrivers}>
					驱动管理
				</Button>
			}
		/>
	);
}
