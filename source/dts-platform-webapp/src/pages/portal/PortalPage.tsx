import { useEffect, useState } from "react";
import { useNavigate } from "react-router";
import { Card, Checkbox, Typography, Space, Avatar } from "antd";
import { BarChartOutlined, SettingOutlined } from "@ant-design/icons";
import useUserStore from "@/store/userStore";

const { Title, Text, Paragraph } = Typography;

const PORTAL_PREF_KEY = "dts.portal.preferredApp";

type AppChoice = "analytics" | "platform";

export default function PortalPage() {
	const [remember, setRemember] = useState(false);
	const navigate = useNavigate();
	const displayName = useUserStore((s) => s.userInfo?.fullName ?? s.userInfo?.username ?? "");

	useEffect(() => {
		const preferred = localStorage.getItem(PORTAL_PREF_KEY) as AppChoice | null;
		if (preferred === "analytics") {
			window.location.href = "/analytics";
		} else if (preferred === "platform") {
			navigate("/dashboard/workbench", { replace: true });
		}
	}, [navigate]);

	const handleSelect = (choice: AppChoice) => {
		if (remember) {
			localStorage.setItem(PORTAL_PREF_KEY, choice);
		}
		if (choice === "analytics") {
			window.location.href = "/analytics";
		} else {
			navigate("/dashboard/workbench", { replace: true });
		}
	};

	const preferred = localStorage.getItem(PORTAL_PREF_KEY);
	if (preferred === "analytics" || preferred === "platform") {
		return null;
	}

	return (
		<div
			style={{
				display: "flex",
				flexDirection: "column",
				alignItems: "center",
				justifyContent: "center",
				minHeight: "100vh",
				background: "linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%)",
				padding: 24,
			}}
		>
			<Space direction="vertical" align="center" size={32}>
				<Title level={2} style={{ margin: 0 }}>
					DTS 数据平台
				</Title>

				<Space size={24}>
					<Card
						hoverable
						style={{ width: 260, textAlign: "center", cursor: "pointer" }}
						onClick={() => handleSelect("analytics")}
					>
						<Space direction="vertical" size={12}>
							<Avatar
								size={64}
								icon={<BarChartOutlined />}
								style={{ backgroundColor: "#1677ff" }}
							/>
							<Title level={4} style={{ margin: 0 }}>
								BI 分析
							</Title>
							<Paragraph type="secondary" style={{ margin: 0 }}>
								智能分析，轻松洞察数据价值
							</Paragraph>
						</Space>
					</Card>

					<Card
						hoverable
						style={{ width: 260, textAlign: "center", cursor: "pointer" }}
						onClick={() => handleSelect("platform")}
					>
						<Space direction="vertical" size={12}>
							<Avatar
								size={64}
								icon={<SettingOutlined />}
								style={{ backgroundColor: "#52c41a" }}
							/>
							<Title level={4} style={{ margin: 0 }}>
								大数据平台
							</Title>
							<Paragraph type="secondary" style={{ margin: 0 }}>
								数据全链路专业管控（专业版）
							</Paragraph>
						</Space>
					</Card>
				</Space>

				<Checkbox checked={remember} onChange={(e) => setRemember(e.target.checked)}>
					记住我的选择，下次直接进入
				</Checkbox>

				{displayName && (
					<Text type="secondary">
						欢迎，{displayName}
					</Text>
				)}
			</Space>
		</div>
	);
}
