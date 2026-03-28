import { Button, Tooltip } from "antd";
import { AppstoreOutlined } from "@ant-design/icons";

export function AppSwitcher() {
	const handleSwitch = () => {
		localStorage.removeItem("dts.portal.preferredApp");
		window.location.href = "/portal";
	};

	return (
		<Tooltip title="切换应用">
			<Button type="text" icon={<AppstoreOutlined />} onClick={handleSwitch} />
		</Tooltip>
	);
}
