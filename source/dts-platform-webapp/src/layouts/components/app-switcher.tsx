import { Button, Tooltip } from "antd";
import { AppstoreOutlined } from "@ant-design/icons";
import { useNavigate } from "react-router";

export function AppSwitcher() {
	const navigate = useNavigate();

	const handleSwitch = () => {
		localStorage.removeItem("dts.portal.preferredApp");
		navigate("/analytics", { replace: true });
	};

	return (
		<Tooltip title="切换应用">
			<Button type="text" icon={<AppstoreOutlined />} onClick={handleSwitch} />
		</Tooltip>
	);
}
