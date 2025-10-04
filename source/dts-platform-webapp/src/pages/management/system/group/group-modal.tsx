import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import type { CreateGroupRequest, KeycloakGroup, UpdateGroupRequest } from "#/keycloak";
import { KeycloakGroupService } from "@/api/services/keycloakService";
import { Alert, AlertDescription } from "@/ui/alert";
import { Button } from "@/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Textarea } from "@/ui/textarea";

interface GroupModalProps {
	open: boolean;
	mode: "create" | "edit";
	group?: KeycloakGroup;
	onCancel: () => void;
	onSuccess: () => void;
}

const ORG_TYPES = [
	{ value: "COMPANY", label: "公司" },
	{ value: "DEPARTMENT", label: "部门" },
	{ value: "TEAM", label: "团队" },
	{ value: "PROJECT", label: "项目" },
];

interface FormData {
	name: string;
	path: string;
	description: string;
	orgCode: string;
	orgType: string;
}

export default function GroupModal({ open, mode, group, onCancel, onSuccess }: GroupModalProps) {
	const [formData, setFormData] = useState<FormData>({
		name: "",
		path: "",
		description: "",
		orgCode: "",
		orgType: "",
	});

	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string>("");

	// 从attributes中提取描述
	const getAttributeValue = useCallback((attributes: Record<string, string[]> | undefined, key: string): string => {
		if (!attributes) return "";
		const value = attributes[key];
		return value && value.length > 0 ? value[0] : "";
	}, []);

	// 初始化表单数据
	useEffect(() => {
		if (mode === "edit" && group) {
			setFormData({
				name: group.name || "",
				path: group.path || "",
				description: getAttributeValue(group.attributes, "description"),
				orgCode: getAttributeValue(group.attributes, "orgCode"),
				orgType: getAttributeValue(group.attributes, "orgType"),
			});
		} else {
			setFormData({
				name: "",
				path: "",
				description: "",
				orgCode: "",
				orgType: "",
			});
		}
		setError("");
	}, [mode, group, getAttributeValue]);

	// 构建attributes对象
	const buildAttributes = (description: string, orgCode: string, orgType: string): Record<string, string[]> => {
		const attributes: Record<string, string[]> = {};
		if (description.trim()) {
			attributes.description = [description.trim()];
		}
		if (orgCode.trim()) {
			attributes.orgCode = [orgCode.trim()];
		}
		if (orgType.trim()) {
			attributes.orgType = [orgType.trim()];
		}
		return attributes;
	};

	const handleSubmit = async () => {
		if (!formData.name.trim()) {
			setError("组名称不能为空");
			return;
		}

		// 组名称验证
		if (!/^[a-zA-Z0-9_\-\u4e00-\u9fa5\s]+$/.test(formData.name)) {
			setError("组名称只能包含字母、数字、中文、下划线、中划线和空格");
			return;
		}

		setLoading(true);
		setError("");

		try {
			if (mode === "create") {
				const createData: CreateGroupRequest = {
					name: formData.name,
					path: formData.path || `/${formData.name}`,
					attributes: buildAttributes(formData.description, formData.orgCode, formData.orgType),
				};

				await KeycloakGroupService.createGroup(createData);
				toast.success("组创建成功");
			} else if (mode === "edit" && group?.id) {
				const updateData: UpdateGroupRequest = {
					name: formData.name,
					path: formData.path,
					attributes: buildAttributes(formData.description, formData.orgCode, formData.orgType),
				};

				await KeycloakGroupService.updateGroup(group.id, updateData);
				toast.success("组更新成功");
			}

			onSuccess();
		} catch (err: any) {
			setError(err.message || "操作失败");
			console.error("Error saving group:", err);
		} finally {
			setLoading(false);
		}
	};

	const title = mode === "create" ? "创建组" : "编辑组";

	return (
		<Dialog open={open} onOpenChange={onCancel}>
			<DialogContent className="max-w-md">
				<DialogHeader>
					<DialogTitle>{title}</DialogTitle>
				</DialogHeader>

				<div className="space-y-4">
					{error && (
						<Alert variant="destructive">
							<AlertDescription>{error}</AlertDescription>
						</Alert>
					)}

					<div className="space-y-2">
						<Label htmlFor="name">组名称 *</Label>
						<Input
							id="name"
							value={formData.name}
							onChange={(e) => setFormData((prev) => ({ ...prev, name: e.target.value }))}
							placeholder="请输入组名称"
						/>
					</div>

					<div className="space-y-2">
						<Label htmlFor="orgCode">组织编码</Label>
						<Input
							id="orgCode"
							value={formData.orgCode}
							onChange={(e) => setFormData((prev) => ({ ...prev, orgCode: e.target.value }))}
							placeholder="如：DPT-001"
						/>
						<p className="text-xs text-muted-foreground">组织编码用于与外部系统或目录同步。</p>
					</div>

					<div className="space-y-2">
						<Label htmlFor="path">组路径</Label>
						<Input
							id="path"
							value={formData.path}
							onChange={(e) => setFormData((prev) => ({ ...prev, path: e.target.value }))}
							placeholder="如：/department/engineering（留空自动生成）"
						/>
						<p className="text-xs text-muted-foreground">组在Keycloak中的层级路径，留空将自动生成</p>
					</div>

					<div className="space-y-2">
						<Label htmlFor="orgType">组织类型</Label>
						<Select
							value={formData.orgType || undefined}
							onValueChange={(value) => setFormData((prev) => ({ ...prev, orgType: value }))}
						>
							<SelectTrigger id="orgType" className="w-full justify-between">
								<SelectValue placeholder="请选择组织类型" />
							</SelectTrigger>
							<SelectContent>
								{ORG_TYPES.map((item) => (
									<SelectItem key={item.value} value={item.value}>
										{item.label}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
						<p className="text-xs text-muted-foreground">选择组织类型以帮助业务系统理解层级含义。</p>
					</div>

					<div className="space-y-2">
						<Label htmlFor="description">描述</Label>
						<Textarea
							id="description"
							value={formData.description}
							onChange={(e) => setFormData((prev) => ({ ...prev, description: e.target.value }))}
							placeholder="请输入组描述"
							rows={3}
						/>
					</div>
				</div>

				<DialogFooter>
					<Button variant="outline" onClick={onCancel}>
						取消
					</Button>
					<Button onClick={handleSubmit} disabled={loading}>
						{loading ? "处理中..." : "确定"}
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
}
