import { useToggle } from "react-use";
import { Icon } from "@/components/icon";
import useLocale from "@/locales/use-locale";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/ui/collapsible";
import { cn } from "@/utils";
import type { NavGroupProps } from "../types";
import { NavList } from "./nav-list";

export function NavGroup({ name, items }: NavGroupProps) {
	const [open, toggleOpen] = useToggle(true);
	const { t } = useLocale();
	const groupLabel = name ? t(name) : "";
	const onlyItemLabel = items.length === 1 ? t(items[0].title) : "";
	const normalizeLabel = (value: string) => value.trim().replace(/\s+/g, " ").toLocaleLowerCase();
	const showGroupLabel =
		Boolean(groupLabel) && (items.length !== 1 || normalizeLabel(groupLabel) !== normalizeLabel(onlyItemLabel));

	return (
		<Collapsible open={showGroupLabel ? open : true} onOpenChange={showGroupLabel ? toggleOpen : undefined}>
			{showGroupLabel ? (
				<CollapsibleTrigger asChild>
					<Group label={groupLabel} open={open} />
				</CollapsibleTrigger>
			) : null}
			<CollapsibleContent>
				<ul className="flex w-full flex-col gap-0.5">
					{items.map((item, index) => (
						<NavList key={item.title || index} data={item} depth={1} />
					))}
				</ul>
			</CollapsibleContent>
		</Collapsible>
	);
}

function Group({ label, open }: { label: string; open: boolean }) {
	return (
		<button
			type="button"
			className={cn(
				"group w-full inline-flex items-center justify-start relative gap-2 cursor-pointer border-0 bg-transparent pt-4 pr-2 pb-2 pl-3 text-left transition-all duration-300 ease-in-out",
				"hover:pl-4",
			)}
		>
			<Icon
				icon="eva:arrow-ios-forward-fill"
				className={cn(
					"absolute left-[-4px] h-4 w-4 inline-flex shrink-0 transition-all duration-300 ease-in-out",
					"opacity-0 group-hover:opacity-100",
					{
						"rotate-90": open,
					},
				)}
			/>

			<span
				className={cn(
					"text-xs font-medium transition-all duration-300 ease-in-out text-text-disabled",
					"hover:text-text-primary",
				)}
			>
				{label}
			</span>
		</button>
	);
}
