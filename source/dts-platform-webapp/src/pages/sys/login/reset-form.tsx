import { useForm } from "react-hook-form";
import { Icon } from "@/components/icon";
import { useBilingualText } from "@/hooks/useBilingualText";
import { Button } from "@/ui/button";
import { Form, FormControl, FormField, FormItem, FormMessage } from "@/ui/form";
import { Input } from "@/ui/input";
import { ReturnButton } from "./components/ReturnButton";
import { LoginStateEnum, useLoginStateContext } from "./providers/login-provider";

function ResetForm() {
	const { loginState, backToLogin } = useLoginStateContext();
	const bilingual = useBilingualText();
	const form = useForm();

	const onFinish = (values: any) => {
		console.log("Received values of form: ", values);
	};

	if (loginState !== LoginStateEnum.RESET_PASSWORD) return null;

	return (
		<>
			<div className="mb-8 text-center">
				<Icon icon="local:ic-reset-password" size="100" className="text-primary!" />
			</div>
			<Form {...form}>
				<form onSubmit={form.handleSubmit(onFinish)} className="space-y-4">
					<div className="flex flex-col items-center gap-2 text-center">
						<h1 className="text-2xl font-bold">{bilingual("sys.login.forgetFormTitle")}</h1>
						<p className="text-balance text-sm text-muted-foreground">{bilingual("sys.login.forgetFormSecondTitle")}</p>
					</div>

					<FormField
						control={form.control}
						name="email"
						render={({ field }) => (
							<FormItem>
								<FormControl>
									<Input placeholder={bilingual("sys.login.emaildPlaceholder")} {...field} />
								</FormControl>
								<FormMessage />
							</FormItem>
						)}
					/>
					<Button type="submit" className="w-full">
						{bilingual("sys.login.sendEmailButton")}
					</Button>
					<ReturnButton onClick={backToLogin} />
				</form>
			</Form>
		</>
	);
}

export default ResetForm;
