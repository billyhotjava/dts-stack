import { Loader2, Eye, EyeOff } from "lucide-react";
import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import type { SignInReq } from "@/api/services/userService";
import { GLOBAL_CONFIG } from "@/global-config";
import { resolveHomePathForRoles } from "@/routes/sections/dashboard";
import { useBilingualText } from "@/hooks/useBilingualText";
import { useSignIn, useUserActions } from "@/store/userStore";
import { Button } from "@/ui/button";
import { Checkbox } from "@/ui/checkbox";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/ui/form";
import { Input } from "@/ui/input";
import { cn } from "@/utils";
import { LoginStateEnum, useLoginStateContext } from "./providers/login-provider";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { RadioGroup, RadioGroupItem } from "@/ui/radio-group";
import { getPkiChallenge, pkiLogin, type PkiChallenge } from "@/api/services/pkiService";
import { KoalMiddlewareClient, KoalCertificate } from "@/api/services/koalPkiClient";

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
		setLoading(true);
		try {
			const user = await signIn(values);
			const roles = Array.isArray(user?.roles) ? (user.roles as string[]) : [];
			const targetRoute = resolveHomePathForRoles(roles);
			navigate(targetRoute || GLOBAL_CONFIG.defaultRoute, { replace: true });
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

	return (
		<div className={cn("flex flex-col gap-6", className)}>
			<Form {...form} {...props}>
				<form onSubmit={form.handleSubmit(handleFinish)} className="space-y-4">
					<div className="flex flex-col items-center gap-1 text-center">
						<h1 className="text-2xl font-bold">{bilingual("sys.login.signInFormTitle")}</h1>
						<p className="text-sm text-muted-foreground">(管理端)</p>
					</div>

					{!hidePasswordForm && (
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
					)}

					{!hidePasswordForm && (
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
			<Dialog open={pkiDialogOpen} onOpenChange={setPkiDialogOpen}>
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
								<RadioGroup value={selectedCertId} onValueChange={setSelectedCertId} className="space-y-3">
									{pkiCerts.map((cert, idx) => {
										const uname = deriveUsernameFromCert(cert) || cert.subjectCn || cert.sn || cert.id;
										const dup = (certNameCounts.get(String(uname)) ?? 0) > 1;
										const tail = (cert.sn && String(cert.sn).slice(-4)) || String(cert.id).slice(-4);
										const display = dup ? `${uname}（尾号 ${tail}）` : String(uname);
										return (
											<label key={cert.id} htmlFor={`cert-${idx}`} className={`flex cursor-pointer gap-3 rounded-md border p-3 text-sm leading-6 transition-colors ${selectedCertId === cert.id ? "border-primary bg-primary/5" : "hover:border-primary/50"}`}>
												<RadioGroupItem value={cert.id} id={`cert-${idx}`} className="mt-1" />
												<div className="flex-1">
													<div className="font-medium text-foreground">用户名：{display}</div>
												</div>
											</label>
										);
									})}
								</RadioGroup>
							)}
						</div>
						{selectedCert && (
							<div className="rounded-md bg-muted/40 p-2 text-sm text-muted-foreground">
								将以 <span className="text-foreground font-medium">{deriveUsernameFromCert(selectedCert) || selectedCert.subjectCn || selectedCert.sn}</span> 登录
							</div>
						)}
						<div className="space-y-2">
							<label className="text-sm font-medium text-muted-foreground">输入 PIN 码</label>
							<Input type="password" value={pinCode} onChange={(e) => setPinCode(e.target.value)} placeholder="请输入 PIN 码" />
						</div>
					</div>
					<DialogFooter>
						<Button type="button" variant="outline" onClick={() => setPkiDialogOpen(false)} disabled={pkiSubmitting}>取消</Button>
						<Button type="button" onClick={handleConfirmPki} disabled={pkiSubmitting || !selectedCertId || !pinCode.trim()}>
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
	// 开关：默认隐藏账号/密码表单，仅保留证书登录入口（后端能力仍保留）
	const hidePasswordForm: boolean = (() => {
		const raw = (import.meta as any)?.env?.VITE_HIDE_PASSWORD_LOGIN;
		if (raw === undefined || raw === null || String(raw).trim() === "") return true; // 默认隐藏
		const v = String(raw).trim().toLowerCase();
		return v !== "0" && v !== "false";
	})();

	// PKI 状态
	const [pkiDialogOpen, setPkiDialogOpen] = useState(false);
	const [pkiCerts, setPkiCerts] = useState<KoalCertificate[]>([]);
	const [selectedCertId, setSelectedCertId] = useState("");
	const [pinCode, setPinCode] = useState("");
	const [pkiClientState, setPkiClientState] = useState<{ client: KoalMiddlewareClient; challenge: PkiChallenge } | null>(null);
	const [pkiSubmitting, setPkiSubmitting] = useState(false);

	const selectedCert = pkiCerts.find((c) => c.id === selectedCertId);
	const certNameCounts = useMemo(() => {
		const m = new Map<string, number>();
		for (const c of pkiCerts) {
			const n = String(deriveUsernameFromCert(c) || c.subjectCn || c.sn || c.id);
			m.set(n, (m.get(n) ?? 0) + 1);
		}
		return m;
	}, [pkiCerts]);

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
						if (name === upper) return seg.substring(idx + 1).trim();
					}
				}
			}
		} catch {}
		return null;
	}

	function deriveUsernameFromCert(cert?: KoalCertificate | null): string {
		if (!cert) return "";
		const raw: any = cert.raw || {};
		const subjectName = raw.subjectName || raw.SubjectName || {};
		const uidFromObj = subjectName.UID || raw.UID || raw.uid;
		if (typeof uidFromObj === "string" && uidFromObj.trim()) return uidFromObj.trim();
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
		return cert.subjectCn || "";
	}

	const handlePkiLogin = async () => {
		setLoading(true);
		try {
			const challenge = await getPkiChallenge();
			const client = await KoalMiddlewareClient.connect();
			const certificates = await client.listCertificates();
			const uniq = (() => {
				const m = new Map<string, KoalCertificate>();
				for (const c of certificates) if (!m.has(c.id)) m.set(c.id, c);
				return Array.from(m.values());
			})();
			setPkiCerts(uniq);
			setSelectedCertId(uniq.length === 1 ? uniq[0]?.id ?? "" : "");
			setPinCode("");
			setPkiClientState({ client, challenge });
			setPkiDialogOpen(true);
		} catch (error) {
			toast.error(String((error as Error)?.message || "证书登录初始化失败"), { position: "top-center" });
		} finally {
			setLoading(false);
		}
	};

	const handleConfirmPki = async () => {
		if (!pkiClientState) return;
		const { client, challenge } = pkiClientState;
		if (!selectedCertId) return;
		const cert = pkiCerts.find((c) => c.id === selectedCertId);
		if (!cert) return;
		setPkiSubmitting(true);
		try {
			await client.verifyPin(cert, pinCode);
			const signed = await client.signData(cert, challenge.nonce);
			const certContentB64 = await client.exportCertificate(cert);
			const resp: any = await pkiLogin({
				challengeId: challenge.challengeId,
				nonce: challenge.nonce,
				originDataB64: signed.originDataB64,
				signDataB64: signed.signDataB64,
				certContentB64,
				mode: "agent",
				signType: signed.signType,
				dupCertB64: signed.dupCertB64,
			});

			const accessToken = String(resp?.accessToken || resp?.token || "").trim();
			const refreshToken = String(resp?.refreshToken || "").trim();
			const user = (resp?.user || resp?.userInfo || {}) as any;
			if (!accessToken) throw new Error("登录响应缺少访问令牌");
			setUserToken({ accessToken, refreshToken });
			setUserInfo(user);
			navigate(GLOBAL_CONFIG.defaultRoute, { replace: true });
			toast.success("登录成功", { closeButton: true });
			await client.logout();
			setPkiDialogOpen(false);
		} catch (error) {
			toast.error(String((error as Error)?.message || "签名失败"), { position: "top-center" });
		} finally {
			setPkiSubmitting(false);
		}
	};
