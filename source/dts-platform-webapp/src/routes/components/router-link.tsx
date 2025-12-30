import type { LinkProps } from "react-router";
import { Link } from "react-router";

interface RouterLinkProps extends Omit<LinkProps, "to"> {
	href: string;
	ref?: React.Ref<HTMLAnchorElement>;
}

export const RouterLink: React.FC<RouterLinkProps> = ({ href, children, className, onClick, ...props }) => {
	const isExternal = /^https?:\/\//i.test(href);
	// Some routes (e.g. reverse-proxied BI tools) must trigger a full page load.
	const isProxyEscape =
		href.startsWith("/dashboards") ||
		href.startsWith("/analytics") ||
		href.startsWith("/screen");

	if (isExternal || isProxyEscape) {
		return (
			<a
				href={href}
				className={className}
				onClick={onClick}
				target={isExternal ? "_blank" : undefined}
				rel={isExternal ? "noreferrer noopener" : undefined}
			>
				{children}
			</a>
		);
	}

	return (
		<Link ref={(props as any).ref} to={href} className={className} onClick={onClick} {...props}>
			{children}
		</Link>
	);
};
