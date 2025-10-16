import type { IconProps as IconifyIconProps } from "@iconify/react";
import { Icon as IconifyIcon } from "@iconify/react";
import type { CSSProperties } from "react";
import * as lucideIcons from "lucide-react";
import type { LucideIcon as LucideIconComponent } from "lucide-react";
import { cn } from "@/utils";

interface IconProps extends Omit<IconifyIconProps, "icon"> {
	/**
	 * Icon name or path
	 * - Local SVG: local:icon-name
	 * - URL SVG: url:https://example.com/icon.svg
	 * - Third-party icon library: iconify-icon-name
	 */
	icon?: string;
	// Backward compatibility: allow `name` as alias for `icon`
	name?: string;
	size?: string | number;
	color?: string;
	className?: string;
	style?: CSSProperties;
}

const LUCIDE_ALIAS_MAP: Record<string, string> = {
	Sync: "RefreshCcw",
};

const lucideRegistry = lucideIcons as unknown as Record<string, LucideIconComponent | undefined>;

const resolveLucideName = (raw: string): string[] => {
	const alias = LUCIDE_ALIAS_MAP[raw];
	if (alias) {
		return [alias];
	}

	// Support kebab-case, snake_case or lowercase names by converting to PascalCase
	const normalized = raw.includes("-") || raw.includes("_")
		? raw
				.split(/[-_]/)
				.filter(Boolean)
				.map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1))
				.join("")
		: raw;

	// For convenience also try lowercase-first variant (some exports use camelCase)
	const camelCase = normalized ? normalized.charAt(0).toLowerCase() + normalized.slice(1) : normalized;

	return [normalized, camelCase];
};

const lookupLucideIcon = (name: string): LucideIconComponent | null => {
	for (const candidate of resolveLucideName(name)) {
		if (candidate) {
			const component = lucideRegistry[candidate];
			if (component) {
				return component;
			}
		}
	}
	return null;
};

export default function Icon({
	icon,
	name,
	size = "1em",
	color = "currentColor",
	className = "",
	style = {},
	...props
}: IconProps) {
	// Normalize icon from either `icon` or legacy `name`
	const normalized = (icon || name || "").toString();
	// Defensive guard: some call sites may provide an undefined or empty icon key
	if (!normalized) {
		return null;
	}

	if (normalized.startsWith("url:")) {
		const url = normalized.replace("url:", "");
		return (
			<img
				src={url}
				alt="icon"
				className={cn("inline-block", className)}
				style={{
					width: size,
					height: size,
					color,
					...style,
				}}
			/>
		);
	}

	// Fallback to lucide-react icons when no namespace prefix is provided
	if (!normalized.includes(":")) {
		const LucideIcon = lookupLucideIcon(normalized);
		if (LucideIcon) {
			return (
				<LucideIcon
					size={size}
					color={color}
					className={cn("inline-block", className)}
					style={style}
					{...props}
				/>
			);
		}
	}

	// Handle URL SVG
	// Handle local and third-party icon libraries
	return (
		<IconifyIcon
			icon={normalized}
			width={size}
			height={size}
			className={cn("inline-block", className)}
			style={{
				color,
				height: size,
				width: size,
				...style,
			}}
			{...props}
		/>
	);
}
