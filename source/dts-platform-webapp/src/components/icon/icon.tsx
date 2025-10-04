import type { IconProps as IconifyIconProps } from "@iconify/react";
import { Icon as IconifyIcon } from "@iconify/react";
import type { CSSProperties } from "react";
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

	// Handle URL SVG
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
