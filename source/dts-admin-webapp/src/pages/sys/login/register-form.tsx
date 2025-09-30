import { useMutation } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import userService from "@/api/services/userService";
import { useBilingualText } from "@/hooks/useBilingualText";
import { Button } from "@/ui/button";
import { Form, FormControl, FormField, FormItem, FormMessage } from "@/ui/form";
import { Input } from "@/ui/input";
import { ReturnButton } from "./components/ReturnButton";
import { LoginStateEnum, useLoginStateContext } from "./providers/login-provider";

function RegisterForm() {
	const { loginState, backToLogin } = useLoginStateContext();
	const bilingual = useBilingualText();

	const signUpMutation = useMutation({
		mutationFn: userService.signup,
	});

	const form = useForm({
		defaultValues: {
			username: "",
			email: "",
			password: "",
			confirmPassword: "",
		},
	});

	const onFinish = async (values: any) => {
		console.log("Received values of form: ", values);
		await signUpMutation.mutateAsync(values);
		backToLogin();
	};

	if (loginState !== LoginStateEnum.REGISTER) return null;

	return (
		<Form {...form}>
			<form onSubmit={form.handleSubmit(onFinish)} className="space-y-4">
				<div className="flex flex-col items-center gap-2 text-center">
					<h1 className="text-2xl font-bold">{bilingual("sys.login.signUpFormTitle")}</h1>
				</div>

				<FormField
					control={form.control}
					name="username"
					rules={{ required: bilingual("sys.login.accountPlaceholder") }}
					render={({ field }) => (
						<FormItem>
							<FormControl>
								<Input placeholder={bilingual("sys.login.accountPlaceholder")} {...field} />
							</FormControl>
							<FormMessage />
						</FormItem>
					)}
				/>

				<FormField
					control={form.control}
					name="email"
					rules={{ required: bilingual("sys.login.emaildPlaceholder") }}
					render={({ field }) => (
						<FormItem>
							<FormControl>
								<Input placeholder={bilingual("sys.login.emaildPlaceholder")} {...field} />
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
							<FormControl>
								<Input type="password" placeholder={bilingual("sys.login.passwordPlaceholder")} {...field} />
							</FormControl>
							<FormMessage />
						</FormItem>
					)}
				/>

				<FormField
					control={form.control}
					name="confirmPassword"
					rules={{
						required: bilingual("sys.login.confirmPasswordPlaceholder"),
						validate: (value) => value === form.getValues("password") || bilingual("sys.login.diffPwd"),
					}}
					render={({ field }) => (
						<FormItem>
							<FormControl>
								<Input type="password" placeholder={bilingual("sys.login.confirmPasswordPlaceholder")} {...field} />
							</FormControl>
							<FormMessage />
						</FormItem>
					)}
				/>

				<Button type="submit" className="w-full">
					{bilingual("sys.login.registerButton")}
				</Button>

				<div className="mb-2 text-xs text-gray">
					<span>{bilingual("sys.login.registerAndAgree")}</span>
					<a href="./" className="text-sm underline! text-primary!">
						{bilingual("sys.login.termsOfService")}
					</a>
					{" & "}
					<a href="./" className="text-sm underline! text-primary!">
						{bilingual("sys.login.privacyPolicy")}
					</a>
				</div>

				<ReturnButton onClick={backToLogin} />
			</form>
		</Form>
	);
}

export default RegisterForm;
