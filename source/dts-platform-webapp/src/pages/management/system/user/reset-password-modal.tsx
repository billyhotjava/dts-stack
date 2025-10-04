import { useState } from "react";
import { toast } from "sonner";
import type { ResetPasswordRequest } from "#/keycloak";
import { KeycloakUserService } from "@/api/services/keycloakService";
import { Alert, AlertDescription } from "@/ui/alert";
import { Button } from "@/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Switch } from "@/ui/switch";

interface ResetPasswordModalProps {
	open: boolean;
	userId: string;
	username: string;
	onCancel: () => void;
	onSuccess: () => void;
}

export default function ResetPasswordModal({ open, userId, username, onCancel, onSuccess }: ResetPasswordModalProps) {
	const [password, setPassword] = useState("");
	const [confirmPassword, setConfirmPassword] = useState("");
	const [temporary, setTemporary] = useState(true);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState("");

	const handleSubmit = async () => {
		if (!password.trim()) {
			setError("密码不能为空");
			return;
		}

		if (password.length < 6) {
			setError("密码长度至少6位");
			return;
		}

		if (password !== confirmPassword) {
			setError("两次输入的密码不一致");
			return;
		}

		setLoading(true);
		setError("");

		try {
			const resetData: ResetPasswordRequest = {
				password,
				temporary,
			};

			await KeycloakUserService.resetPassword(userId, resetData);
			toast.success(`用户 ${username} 的密码重置成功`);
			onSuccess();
		} catch (err: any) {
			setError(err.message || "密码重置失败");
			console.error("Error resetting password:", err);
		} finally {
			setLoading(false);
		}
	};

	const handleCancel = () => {
		setPassword("");
		setConfirmPassword("");
		setTemporary(true);
		setError("");
		onCancel();
	};

	return (
		<Dialog open={open} onOpenChange={handleCancel}>
			<DialogContent className="max-w-md">
				<DialogHeader>
					<DialogTitle>重置密码</DialogTitle>
				</DialogHeader>

				<div className="space-y-4">
					{error && (
						<Alert variant="destructive">
							<AlertDescription>{error}</AlertDescription>
						</Alert>
					)}

					<div className="space-y-2">
						<Label>用户</Label>
						<div className="text-sm text-muted-foreground">{username}</div>
					</div>

					<div className="space-y-2">
						<Label htmlFor="password">新密码 *</Label>
						<Input
							id="password"
							type="password"
							value={password}
							onChange={(e) => setPassword(e.target.value)}
							placeholder="请输入新密码"
						/>
					</div>

					<div className="space-y-2">
						<Label htmlFor="confirmPassword">确认密码 *</Label>
						<Input
							id="confirmPassword"
							type="password"
							value={confirmPassword}
							onChange={(e) => setConfirmPassword(e.target.value)}
							placeholder="请再次输入新密码"
						/>
					</div>

					<div className="flex items-center space-x-2">
						<Switch id="temporary" checked={temporary} onCheckedChange={setTemporary} />
						<Label htmlFor="temporary">临时密码 (用户下次登录时需要修改)</Label>
					</div>
				</div>

				<DialogFooter>
					<Button variant="outline" onClick={handleCancel}>
						取消
					</Button>
					<Button onClick={handleSubmit} disabled={loading}>
						{loading ? "重置中..." : "确定"}
					</Button>
				</DialogFooter>
			</DialogContent>
		</Dialog>
	);
}
