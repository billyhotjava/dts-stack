import { Loader2, Eye, EyeOff } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import type { SignInReq } from "@/api/services/userService";
import { useContextActions } from "@/store/contextStore";
import { useBilingualText } from "@/hooks/useBilingualText";
import { useSignIn, useUserActions } from "@/store/userStore";
import { Button } from "@/ui/button";
import { Checkbox } from "@/ui/checkbox";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/ui/form";
import { Input } from "@/ui/input";
import { cn } from "@/utils";
import { LoginStateEnum, useLoginStateContext } from "./providers/login-provider";
import { getPkiChallenge, pkiLogin, trySignWithLocalAgent, createPortalSessionFromPki } from "@/api/services/pkiService";
import { KeycloakLocalizationService } from "@/api/services/keycloakLocalizationService";
import { updateLocalTranslations } from "@/utils/translation";

export function LoginForm({ className, ...props }: React.ComponentPropsWithoutRef<"form">) {
	const [loading, setLoading] = useState(false);
	const [remember, setRemember] = useState(true);
	const [showPassword, setShowPassword] = useState(false);
	const navigate = useNavigate();

	const { loginState } = useLoginStateContext();
	const signIn = useSignIn();
	const { setUserToken, setUserInfo } = useUserActions();
	const bilingual = useBilingualText();

	const form = useForm<SignInReq>({
		defaultValues: {
			username: "",
			password: "",
		},
	});

	if (loginState !== LoginStateEnum.LOGIN) return null;

	const handleFinish = async (values: SignInReq) => {
		const trimmedUsername = values.username?.trim() ?? "";
		const normalizedUsername = trimmedUsername.toLowerCase();
		if (["sysadmin", "authadmin", "auditadmin"].includes(normalizedUsername)) {
			toast.error("系统管理角色用户不能登录业务平台", { position: "top-center" });
			return;
		}

		setLoading(true);
		try {
			const signInResult = await signIn({ ...values, username: trimmedUsername });
			// 初始化作用域/部门上下文，避免首次请求缺少 X-Active-Dept 导致 403
			try {
				useContextActions().initDefaults();
			} catch {}
			// 登录成功后先尝试加载菜单，再进入工作台，避免偶发 404
			const usedFallback = signInResult.mode === "fallback";
			if (!usedFallback) {
				try {
					const svc = await import("@/api/services/menuService");
					await svc.default.getMenuTree().catch(() => undefined);
				} catch {}
			}
			// 统一进入欢迎页（工作台）。注意：Router 已配置 basename=publicPath，
			// 这里必须传入“路由内路径”，不要再拼 publicPath，否则会出现 404。
			navigate("/dashboard/workbench", { replace: true });
			toast.success(bilingual("sys.login.loginSuccessTitle"), {
				closeButton: true,
			});
		} catch (error) {
			// 错误已在signIn中处理，这里不需要额外处理
			console.error("Login failed:", error);
		} finally {
			setLoading(false);
		}
	};

	const handlePkiLogin = async () => {
		setLoading(true);
		try {
			const challenge = await getPkiChallenge();
			const plain = challenge.nonce; // server requires the nonce to be present in the plain text
			const signed = await trySignWithLocalAgent(plain);
			if (!signed) {
				toast.error("未检测到本地签名Agent，请安装或启动后重试", { position: "top-center" });
				return;
			}
            const resp: any = await pkiLogin({
                challengeId: challenge.challengeId,
                plain,
                p7Signature: signed.p7,
                certB64: signed.cert,
                mode: "agent",
            });
            const rawUser = (resp as any)?.user ?? (resp as any)?.userInfo ?? {};
            const username = String(rawUser?.username || rawUser?.preferred_username || "").trim();
            if (!username) throw new Error("无法识别用户名");
            // Exchange to platform portal session tokens
            const portal = await createPortalSessionFromPki(username, rawUser);
            const portalUser = (portal as any)?.user ?? rawUser;
            const accessToken = String((portal as any)?.accessToken || (portal as any)?.token || "").trim();
            const refreshToken = String((portal as any)?.refreshToken || "").trim();
            if (!accessToken) throw new Error("登录响应缺少访问令牌");
            setUserToken({ accessToken, refreshToken });
            setUserInfo(portalUser);
			// 初始化上下文并预加载菜单
			try { useContextActions().initDefaults(); } catch {}
			try {
				const svc = await import("@/api/services/menuService");
				await svc.default.getMenuTree().catch(() => undefined);
			} catch {}
			// 加载Keycloak中文翻译（非阻塞）
			try {
				const translations = await KeycloakLocalizationService.getChineseTranslations();
				updateLocalTranslations(translations);
			} catch {}
			navigate("/dashboard/workbench", { replace: true });
			toast.success(bilingual("sys.login.loginSuccessTitle"), { closeButton: true });
		} catch (error: any) {
			toast.error(error?.message || "证书登录失败", { position: "top-center" });
		} finally {
			setLoading(false);
		}
	};

	return (
		<div className={cn("flex flex-col gap-6", className)}>
			<Form {...form} {...props}>
				<form onSubmit={form.handleSubmit(handleFinish)} className="space-y-4">
					<div className="flex flex-col items-center gap-1 text-center">
						<h1 className="text-2xl font-bold">{bilingual("sys.login.signInFormTitle")}</h1>
						<p className="text-sm text-muted-foreground">(业务端)</p>
						{/* <p className="text-balance text-sm text-muted-foreground">{bilingual("sys.login.signInFormDescription")}</p> */}
					</div>

					<FormField
						control={form.control}
						name="username"
						rules={{ required: bilingual("sys.login.accountPlaceholder") }}
						render={({ field }) => (
							<FormItem>
								<FormLabel>{bilingual("sys.login.userName")}</FormLabel>
								<FormControl>
									<Input placeholder={bilingual("sys.login.accountPlaceholder")} {...field} />
								</FormControl>
								<FormMessage />
							</FormItem>
						)}
					/>

					<FormField
						control={form.control}
						name="password"
						rules={{ required: bilingual("sys.login.passwordPlaceholder") }}
						render={({ field }) => (
							<FormItem>
								<FormLabel>{bilingual("sys.login.password")}</FormLabel>
								<FormControl>
									<div className="relative">
										<Input
											type={showPassword ? "text" : "password"}
											placeholder={bilingual("sys.login.passwordPlaceholder")}
											{...field}
											suppressHydrationWarning
											className="pr-10"
										/>
										<button
											type="button"
											aria-label={showPassword ? "隐藏密码" : "显示密码"}
											onClick={() => setShowPassword((v) => !v)}
											className="absolute inset-y-0 right-0 flex items-center pr-3 text-muted-foreground hover:text-foreground"
										>
											{showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
										</button>
									</div>
								</FormControl>
								<FormMessage />
							</FormItem>
						)}
					/>

					{/* 记住我 */}
					<div className="flex flex-row justify-start">
						<div className="flex items-center space-x-2">
							<Checkbox
								id="remember"
								checked={remember}
								onCheckedChange={(checked) => setRemember(checked === "indeterminate" ? false : checked)}
							/>
							<label
								htmlFor="remember"
								className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70"
							>
								{bilingual("sys.login.rememberMe")}
							</label>
						</div>
					</div>

					{/* 登录按钮 */}
					<Button type="submit" className="w-full">
						{loading && <Loader2 className="animate-spin mr-2" />}
						{bilingual("sys.login.loginButton")}
					</Button>
					<Button type="button" variant="outline" className="w-full" onClick={handlePkiLogin} disabled={loading}>
						{loading && <Loader2 className="animate-spin mr-2" />}
						证书登录
					</Button>
				</form>
			</Form>
		</div>
	);
}

export default LoginForm;
