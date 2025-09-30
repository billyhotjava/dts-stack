import { Loader2 } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import type { SignInReq } from "@/api/services/userService";
import { GLOBAL_CONFIG } from "@/global-config";
import { useBilingualText } from "@/hooks/useBilingualText";
import { useSignIn } from "@/store/userStore";
import { Button } from "@/ui/button";
import { Checkbox } from "@/ui/checkbox";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/ui/form";
import { Input } from "@/ui/input";
import { cn } from "@/utils";
import { LoginStateEnum, useLoginStateContext } from "./providers/login-provider";

export function LoginForm({ className, ...props }: React.ComponentPropsWithoutRef<"form">) {
	const [loading, setLoading] = useState(false);
	const [remember, setRemember] = useState(true);
	const navigate = useNavigate();

	const { loginState } = useLoginStateContext();
	const signIn = useSignIn();
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
			await signIn(values);
			// 登录成功后跳转到默认页面
			navigate(GLOBAL_CONFIG.defaultRoute, { replace: true });
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
					<div className="flex flex-col items-center gap-2 text-center">
						<h1 className="text-2xl font-bold">{bilingual("sys.login.signInFormTitle")}</h1>
						<p className="text-balance text-sm text-muted-foreground">{bilingual("sys.login.signInFormDescription")}</p>
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
									<Input
										type="password"
										placeholder={bilingual("sys.login.passwordPlaceholder")}
										{...field}
										suppressHydrationWarning
									/>
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
				</form>
			</Form>
		</div>
	);
}

export default LoginForm;
