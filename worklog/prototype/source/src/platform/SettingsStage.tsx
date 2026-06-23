import { Divider, Form, Input, Select, Switch } from "antd";
import { SectionTitle, Surface } from "@/ui/components";

/** 平台设置 + 系统管理（平台级，原型为展示态）。 */
export function SettingsStage() {
	return (
		<div style={{ maxWidth: 760, margin: "0 auto" }}>
			<SectionTitle kicker="平台 · 旁路" title="设置" desc="平台与系统设置（平台级）。" />
			<Surface pad="lg">
				<div style={{ fontWeight: 650, marginBottom: 12 }}>平台设置</div>
				<Form layout="vertical">
					<Form.Item label="平台名称">
						<Input defaultValue="DTS 数据管理平台" />
					</Form.Item>
					<Form.Item label="密级标识">
						<Select
							defaultValue="confidential"
							options={[
								{ value: "public", label: "公开" },
								{ value: "internal", label: "内部" },
								{ value: "confidential", label: "机密" },
							]}
						/>
					</Form.Item>
					<Form.Item label="浏览器兼容模式">
						<Select
							defaultValue="chrome95"
							options={[
								{ value: "chrome95", label: "Chrome 95+（兼容，默认）" },
								{ value: "chrome109", label: "Chrome 109+（现代）" },
							]}
						/>
					</Form.Item>
					<Form.Item label="顶栏密级水印">
						<Switch defaultChecked />
					</Form.Item>
				</Form>

				<Divider />
				<div style={{ fontWeight: 650, marginBottom: 12 }}>系统管理</div>
				<div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 10 }}>
					{["用户管理", "角色与权限", "菜单配置", "部门组织", "字典维护", "操作审计"].map((s) => (
						<div key={s} style={{ padding: "12px 14px", border: "1px solid var(--hairline)", borderRadius: "var(--radius-md)", background: "var(--surface-sunken)", fontSize: "var(--text-sm)", fontWeight: 500 }}>
							{s}
						</div>
					))}
				</div>
			</Surface>
		</div>
	);
}
