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
import { getPkiChallenge, pkiLogin, createPortalSessionFromPki, type PkiChallenge } from "@/api/services/pkiService";
import { KoalMiddlewareClient, KoalCertificate, formatKoalError } from "@/api/services/koalPkiClient";
import { KeycloakLocalizationService } from "@/api/services/keycloakLocalizationService";
import { updateLocalTranslations } from "@/utils/translation";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { RadioGroup, RadioGroupItem } from "@/ui/radio-group";

const IS_DEV = typeof import.meta !== "undefined" && Boolean(import.meta.env?.DEV);

export function LoginForm({ className, ...props }: React.ComponentPropsWithoutRef<"form">) {
	const [loading, setLoading] = useState(false);
	const [remember, setRemember] = useState(true);
	const [showPassword, setShowPassword] = useState(false);
	const [pkiDialogOpen, setPkiDialogOpen] = useState(false);
	const [pkiCerts, setPkiCerts] = useState<KoalCertificate[]>([]);
	const [selectedCertId, setSelectedCertId] = useState("");
	const [pinCode, setPinCode] = useState("");
	const [pkiClientState, setPkiClientState] = useState<{ client: KoalMiddlewareClient; challenge: PkiChallenge } | null>(null);
	const [pkiSubmitting, setPkiSubmitting] = useState(false);
	const navigate = useNavigate();

	const { loginState } = useLoginStateContext();
	const signIn = useSignIn();
	const { setUserToken, setUserInfo } = useUserActions();
	const bilingual = useBilingualText();

	const selectedCert = pkiCerts.find((item) => item.id === selectedCertId);



	// 简单开关：默认隐藏账号/密码，仅保留证书登录按钮（仍保留密码登录后端能力）
	const hidePasswordForm: boolean = (() => {
		// 1) 运行时注入配置（容器 entrypoint 生成的 /runtime-config.js）优先
		try {
			const rc: any = (typeof window !== "undefined" && (window as any).__RUNTIME_CONFIG__) || {};
			const enableRaw = rc.enablePasswordLogin;
			if (enableRaw !== undefined && enableRaw !== null && String(enableRaw).trim() !== "") {
				const v = String(enableRaw).trim().toLowerCase();
				if (v === "1" || v === "true" || v === "yes" || v === "on") return false;
				if (v === "0" || v === "false" || v === "no" || v === "off") return true;
			}
			const hideRaw = rc.hidePasswordLogin;
			if (hideRaw !== undefined && hideRaw !== null && String(hideRaw).trim() !== "") {
				const v = String(hideRaw).trim().toLowerCase();
				return v !== "0" && v !== "false";
			}
		} catch {}

		// 2) 回退到构建期变量（Vite 注入）
		const enabledRaw = (import.meta as any)?.env?.WEBAPP_PASSWORD_LOGIN_ENABLED;
		if (enabledRaw !== undefined && enabledRaw !== null && String(enabledRaw).trim() !== "") {
			const v = String(enabledRaw).trim().toLowerCase();
			// 显式开启则不隐藏
			if (v === "1" || v === "true" || v === "yes" || v === "on") return false;
			if (v === "0" || v === "false" || v === "no" || v === "off") return true;
		}
		const raw = (import.meta as any)?.env?.VITE_HIDE_PASSWORD_LOGIN;
		if (raw === undefined || raw === null || String(raw).trim() === "") return true; // 默认隐藏
		const v = String(raw).trim().toLowerCase();
		return v !== "0" && v !== "false";
	})();

	function parseDnFor(keys: string[], dn?: string): string | null {
		if (!dn || typeof dn !== "string") return null;
		try {
			const parts = dn.split(",");
			for (const k of keys) {
				const upper = k.toUpperCase();
				for (const p of parts) {
					const seg = p.trim();
					const idx = seg.indexOf("=");
					if (idx > 0) {
						const name = seg.substring(0, idx).trim().toUpperCase();
						if (name === upper) {
							return seg.substring(idx + 1).trim();
						}
					}
				}
			}
		} catch {}
		return null;
	}

	function deriveUsernameFromCert(cert?: KoalCertificate | null): string {
		if (!cert) return "";
		const raw: any = cert.raw || {};
		// 优先取 UID，再回退 CN
		const subjectName = raw.subjectName || raw.SubjectName || {};
		const uidFromObj = subjectName.UID || raw.UID || raw.uid;
		if (typeof uidFromObj === "string" && uidFromObj.trim()) return uidFromObj.trim();
		// 尝试从字符串 DN 解析
		const subjectStr: string | undefined =
			typeof raw.subject === "string"
				? raw.subject
				: typeof raw.Subject === "string"
				? raw.Subject
				: typeof raw.subjectDN === "string"
				? raw.subjectDN
				: typeof raw.SubjectDN === "string"
				? raw.SubjectDN
				: undefined;
		const uidFromDn = parseDnFor(["UID"], subjectStr || undefined);
		if (uidFromDn) return uidFromDn;
		const cnFromDn = parseDnFor(["CN"], subjectStr || undefined);
		if (cnFromDn) return cnFromDn;
		// 最后回退 cert.subjectCn
		return cert.subjectCn || "";
	}

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
			if (IS_DEV) {
				console.info("[pki-login] challenge", challenge);
			}
			const client = await KoalMiddlewareClient.connect();
			const certificates = await client.listCertificates();
			if (IS_DEV) {
				console.info("[pki-login] certificates", certificates);
			}
			if (!certificates.length) {
				await client.logout();
				setPkiCerts([]);
				setSelectedCertId("");
				setPinCode("");
				setPkiClientState({ client, challenge });
				setPkiDialogOpen(true);      // 先把弹窗打开
				toast.error("未找到可用的签名证书，请确认介质已插入", { position: "top-center" });
				return;                       // 退出后续流程

			}
			const signables = certificates.filter((c) => c.canSign);
			// 去重（部分中间件可能返回重复条目）
			const uniq = (() => {
				const m = new Map<string, KoalCertificate>();
				for (const c of signables) if (!m.has(c.id)) m.set(c.id, c);
				return Array.from(m.values());
			})();
			setPkiCerts(uniq);
			// 若只有一个可签名证书，默认选中；多于一个时要求用户显式选择，避免误选
			if (uniq.length === 1) {
				setSelectedCertId(uniq[0]?.id ?? "");
			} else {
				setSelectedCertId("");
			}
			setPinCode("");
			setPkiClientState({ client, challenge });
			setPkiDialogOpen(true);
		} catch (error) {
			if (IS_DEV) {
				console.error("[pki-login] failed", error);
			}
			toast.error(formatKoalError(error), { position: "top-center" });
		} finally {
			setLoading(false);
		}
	};

	const closePkiDialog = async (logout = true) => {
		const current = pkiClientState;
		setPkiDialogOpen(false);
		setPkiCerts([]);
		setSelectedCertId("");
		setPinCode("");
		setPkiClientState(null);
		if (logout && current) {
			try {
				await current.client.logout();
			} catch {
				// ignore
			}
		}
	};

	const handlePkiDialogOpenChange = (open: boolean) => {
		if (!open) {
			void closePkiDialog(true);
		} else {
			setPkiDialogOpen(true);
		}
	};

	const handleConfirmPki = async () => {
		if (!pkiClientState) {
			toast.error("会话已失效，请重新获取挑战", { position: "top-center" });
			return;
		}
		const certificate = pkiCerts.find((item) => item.id === selectedCertId);
		if (!certificate) {
			toast.error("请选择签名证书", { position: "top-center" });
			return;
		}
		const pin = pinCode.trim();
		if (!pin) {
			toast.error("请输入PIN码", { position: "top-center" });
			return;
		}

        if (!certificate.canSign) {
            const missing = certificate.missingFields.join("、") || "关键信息";
            toast.error(`所选证书缺少必要的信息（${missing}），无法完成签名，请更换证书`, {
                position: "top-center",
            });
            return;
        }

        const { client, challenge } = pkiClientState;
		setPkiSubmitting(true);
		setLoading(true);
		let loggedOut = false;

		try {
			if (IS_DEV) {
				console.info("[pki-login] verifying pin", certificate.id);
			}
			await client.verifyPin(certificate, pin);
			const signed = await client.signData(certificate, challenge.nonce);
			if (IS_DEV) {
				console.info("[pki-login] signed nonce", signed);
			}
			const certContentB64 = signed.dupCertB64 ?? (await client.exportCertificate(certificate));
			if (IS_DEV) {
				console.info("[pki-login] exported certificate (or reused dupCert)");
			}

			const resp: any = await pkiLogin({
				challengeId: challenge.challengeId,
				nonce: challenge.nonce,
				originDataB64: signed.originDataB64,
				signDataB64: signed.signDataB64,
				certContentB64,
				devId: certificate.devId,
				appName: certificate.appName,
				conName: certificate.conName,
				signType: signed.signType,
				dupCertB64: signed.dupCertB64,
				mode: "agent",
			});

			const rawUser = resp?.user ?? resp?.userInfo ?? {};
			const username = String(rawUser?.username || rawUser?.preferred_username || "").trim();
			if (!username) throw new Error("无法识别用户名");

			// 三员账号不允许登录业务平台（与密码登录一致）
			const unameLower = username.toLowerCase();
			if (["sysadmin", "authadmin", "auditadmin"].includes(unameLower)) {
				toast.error("系统管理角色用户不能登录业务平台", { position: "top-center" });
				await client.logout();
				loggedOut = true;
				await closePkiDialog(false);
				return;
			}

			const portal = await createPortalSessionFromPki(username, rawUser);
			const portalUser = portal?.user ?? rawUser;
			const accessToken = String(portal?.accessToken || portal?.token || "").trim();
			const refreshToken = String(portal?.refreshToken || "").trim();
			if (!accessToken) throw new Error("登录响应缺少访问令牌");

			setUserToken({ accessToken, refreshToken });
			setUserInfo(portalUser);

			try {
				useContextActions().initDefaults();
			} catch {
				// ignore
			}
			try {
				const svc = await import("@/api/services/menuService");
				await svc.default.getMenuTree().catch(() => undefined);
			} catch {
				// ignore
			}
			try {
				const translations = await KeycloakLocalizationService.getChineseTranslations();
				updateLocalTranslations(translations);
			} catch {
				// ignore
			}

			navigate("/dashboard/workbench", { replace: true });
			toast.success(bilingual("sys.login.loginSuccessTitle"), { closeButton: true });

			await client.logout();
			loggedOut = true;
		} catch (error) {
			toast.error(formatKoalError(error), { position: "top-center" });
		} finally {
			setPkiSubmitting(false);
			setLoading(false);
			if (!loggedOut) {
				try {
					await client.logout();
				} catch {
					// ignore
				}
			}
			await closePkiDialog(false);
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

					{!hidePasswordForm && (
						<>
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
						</>
					)}

					{/* 记住我 */}
					{!hidePasswordForm && (
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
					)}

					{/* 登录按钮 */}
					{!hidePasswordForm && (
						<Button type="submit" className="w-full">
							{loading && <Loader2 className="animate-spin mr-2" />}
							{bilingual("sys.login.loginButton")}
						</Button>
					)}
					<Button type="button" variant="outline" className="w-full" onClick={handlePkiLogin} disabled={loading}>
						{loading && <Loader2 className="animate-spin mr-2" />}
						证书登录
					</Button>
				</form>
			</Form>
				<Dialog open={pkiDialogOpen} onOpenChange={handlePkiDialogOpenChange}>
					<DialogContent className="sm:max-w-md">
						<DialogHeader>
							<DialogTitle>证书登录</DialogTitle>
						</DialogHeader>
						<div className="space-y-4">
							<div className="space-y-2">
								<label className="text-sm font-medium text-muted-foreground">选择证书</label>
								{pkiCerts.length === 0 ? (
									<p className="text-xs text-muted-foreground">未检测到可用证书</p>
								) : (
									<RadioGroup
										value={selectedCertId}
										onValueChange={setSelectedCertId}
										className="space-y-3"
									>
										{pkiCerts.map((cert, index) => {
											const uname = deriveUsernameFromCert(cert) || cert.subjectCn || cert.sn || cert.id;
											const display = String(uname);
											return (
												<label
													key={cert.id}
													htmlFor={`cert-${index}`}
													className={`flex cursor-pointer gap-3 rounded-md border p-3 text-sm leading-6 transition-colors ${
														selectedCertId === cert.id ? "border-primary bg-primary/5" : "hover:border-primary/50"
													} ${!cert.canSign ? "opacity-70" : ""}`}
												>
													<RadioGroupItem value={cert.id} id={`cert-${index}`} disabled={!cert.canSign} className="mt-1" />
													<div className="flex-1">
														<div className="font-medium text-foreground">用户名：{display}</div>
													</div>
												</label>
											);
										})}
									</RadioGroup>
								)}
							</div>

							{/* 用户名提示 */}
							{selectedCert && (
								<div className="rounded-md bg-muted/40 p-2 text-sm text-muted-foreground">
									将以 <span className="text-foreground font-medium">{deriveUsernameFromCert(selectedCert) || selectedCert.subjectCn || selectedCert.sn}</span> 登录
								</div>
							)}

							<div className="space-y-2">
								<label className="text-sm font-medium text-muted-foreground">输入 PIN 码</label>
								<Input
									type="password"
									value={pinCode}
									onChange={(event) => setPinCode(event.target.value)}
									placeholder="请输入 PIN 码"
								/>
							</div>
						</div>
					<DialogFooter>
						<Button type="button" variant="outline" onClick={() => void closePkiDialog(true)} disabled={pkiSubmitting}>
							取消
						</Button>
                        <Button
                            type="button"
                            onClick={() => void handleConfirmPki()}
                            disabled={
                                pkiSubmitting ||
                                !selectedCertId ||
                                !pinCode.trim() ||
                                (selectedCert && !selectedCert.canSign)
                            }
                        >
							{pkiSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
							开始签名
						</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>
		</div>
	);
}

export default LoginForm;
