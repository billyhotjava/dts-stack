import { Fragment } from "react";
import { Badge } from "@/ui/badge";
import { Text } from "@/ui/typography";
import { cn } from "@/utils";

export interface MenuChangeDisplayEntry {
	id?: string;
	name?: string;
	title?: string;
	path?: string;
	menuName?: string;
	menuTitle?: string;
	menuPath?: string;
	allowedRolesBefore?: string[];
	allowedRolesAfter?: string[];
	addedRoles?: string[];
	removedRoles?: string[];
	allowedPermissionsBefore?: string[];
	allowedPermissionsAfter?: string[];
	addedPermissions?: string[];
	removedPermissions?: string[];
	addedRules?: VisibilityRuleDisplay[];
	removedRules?: VisibilityRuleDisplay[];
	maxDataLevelBeforeLabel?: string;
	maxDataLevelAfterLabel?: string;
	securityLevelBeforeLabel?: string;
	securityLevelAfterLabel?: string;
	statusBeforeLabel?: string;
	statusAfterLabel?: string;
}

export interface VisibilityRuleDisplay {
	role?: string;
	permission?: string;
	dataLevelLabel?: string;
}

export interface MenuChangeViewerProps {
	entries: MenuChangeDisplayEntry[];
	className?: string;
}

export function MenuChangeViewer({ entries, className }: MenuChangeViewerProps) {
	if (!entries || entries.length === 0) {
		return null;
}

	const resolveTitle = (entry: MenuChangeDisplayEntry, index: number) =>
		entry.title ?? entry.menuTitle ?? entry.name ?? entry.menuName ?? entry.path ?? entry.menuPath ?? entry.id ?? `菜单 ${index + 1}`;

	return (
		<div className={cn("space-y-4", className)}>
			{entries.map((entry, index) => (
				<section
					key={entry.id ?? entry.name ?? entry.menuName ?? entry.title ?? entry.menuTitle ?? `menu-change-${index}`}
					className="rounded-md border border-border/60 bg-muted/10"
				>
					<header className="border-b border-border/60 bg-muted/30 px-3 py-2">
						<Text variant="body3" className="font-medium text-foreground">
							{resolveTitle(entry, index)}
						</Text>
						<Text variant="caption" className="text-muted-foreground">
							{buildMenuSubtitle(entry)}
						</Text>
					</header>
					<div className="space-y-3 px-3 py-3 text-xs leading-5">
						{renderRoleDiff(entry)}
						{renderPermissionDiff(entry)}
						{renderVisibilityDiff(entry)}
						{renderStatusDiff(entry)}
					</div>
				</section>
			))}
		</div>
	);
}

function buildMenuSubtitle(entry: MenuChangeDisplayEntry): string {
	const parts: string[] = [];
	const path = entry.path ?? entry.menuPath;
	if (path) {
		parts.push(`路径：${path}`);
	}
	const title = entry.title ?? entry.menuTitle;
	const name = entry.name ?? entry.menuName;
	if (name && name !== title) {
		parts.push(`编码：${name}`);
	}
	if (entry.id) {
		parts.push(`ID：${entry.id}`);
	}
	return parts.join("  •  ");
}

function renderRoleDiff(entry: MenuChangeDisplayEntry) {
	const beforeRoles = entry.allowedRolesBefore ?? [];
	const afterRoles = entry.allowedRolesAfter ?? [];
	const hasDiff =
		(entry.addedRoles && entry.addedRoles.length > 0) ||
		(entry.removedRoles && entry.removedRoles.length > 0) ||
		beforeRoles.length !== afterRoles.length;

	if (!hasDiff && beforeRoles.length === 0 && afterRoles.length === 0) {
		return null;
	}

	return (
		<div className="space-y-2">
			<Text variant="body3" className="font-medium text-foreground">
				允许角色
			</Text>
			<div className="grid gap-2 sm:grid-cols-2">
				<DiffBadgeGroup
					title="变更前"
					values={beforeRoles}
					highlight={new Set(entry.removedRoles ?? [])}
					emptyHint="未绑定角色"
					variant="removed"
				/>
				<DiffBadgeGroup
					title="变更后"
					values={afterRoles}
					highlight={new Set(entry.addedRoles ?? [])}
					emptyHint="未绑定角色"
					variant="added"
				/>
			</div>
		</div>
	);
}

function renderPermissionDiff(entry: MenuChangeDisplayEntry) {
	const beforePermissions = entry.allowedPermissionsBefore ?? [];
	const afterPermissions = entry.allowedPermissionsAfter ?? [];
	const hasDiff =
		(entry.addedPermissions && entry.addedPermissions.length > 0) ||
		(entry.removedPermissions && entry.removedPermissions.length > 0) ||
		beforePermissions.length !== afterPermissions.length;

	if (!hasDiff && beforePermissions.length === 0 && afterPermissions.length === 0) {
		return null;
	}

	return (
		<div className="space-y-2">
			<Text variant="body3" className="font-medium text-foreground">
				允许权限
			</Text>
			<div className="grid gap-2 sm:grid-cols-2">
				<DiffBadgeGroup
					title="变更前"
					values={beforePermissions}
					highlight={new Set(entry.removedPermissions ?? [])}
					emptyHint="无权限限制"
					variant="removed"
				/>
				<DiffBadgeGroup
					title="变更后"
					values={afterPermissions}
					highlight={new Set(entry.addedPermissions ?? [])}
					emptyHint="无权限限制"
					variant="added"
				/>
			</div>
		</div>
	);
}

function renderVisibilityDiff(entry: MenuChangeDisplayEntry) {
	const added = entry.addedRules ?? [];
	const removed = entry.removedRules ?? [];
	if (added.length === 0 && removed.length === 0) {
		return null;
	}
	return (
		<div className="space-y-2">
			<Text variant="body3" className="font-medium text-foreground">
				可见性规则
			</Text>
			<div className="space-y-1">
				{removed.length > 0 ? (
					<div>
						<Text variant="caption" className="text-muted-foreground">
							移除
						</Text>
						<RuleList items={removed} variant="removed" />
					</div>
				) : null}
				{added.length > 0 ? (
					<div>
						<Text variant="caption" className="text-muted-foreground">
							新增
						</Text>
						<RuleList items={added} variant="added" />
					</div>
				) : null}
			</div>
		</div>
	);
}

function renderStatusDiff(entry: MenuChangeDisplayEntry) {
	const statusChanged =
		entry.statusBeforeLabel &&
		entry.statusAfterLabel &&
		entry.statusBeforeLabel !== entry.statusAfterLabel;
	const maxLevelChanged =
		entry.maxDataLevelBeforeLabel &&
		entry.maxDataLevelAfterLabel &&
		entry.maxDataLevelBeforeLabel !== entry.maxDataLevelAfterLabel;
	const securityChanged =
		entry.securityLevelBeforeLabel &&
		entry.securityLevelAfterLabel &&
		entry.securityLevelBeforeLabel !== entry.securityLevelAfterLabel;

	if (!statusChanged && !maxLevelChanged && !securityChanged) {
		return null;
	}

	return (
		<div className="space-y-2">
			<Text variant="body3" className="font-medium text-foreground">
				其他变化
			</Text>
			<ul className="space-y-1">
				{statusChanged ? (
					<li>
						<Text variant="body3" className="text-muted-foreground">
							状态：<DiffInline before={entry.statusBeforeLabel} after={entry.statusAfterLabel} />
						</Text>
					</li>
				) : null}
				{maxLevelChanged ? (
					<li>
						<Text variant="body3" className="text-muted-foreground">
							最大数据密级：
							<DiffInline before={entry.maxDataLevelBeforeLabel} after={entry.maxDataLevelAfterLabel} />
						</Text>
					</li>
				) : null}
				{securityChanged ? (
					<li>
						<Text variant="body3" className="text-muted-foreground">
							访问密级：<DiffInline before={entry.securityLevelBeforeLabel} after={entry.securityLevelAfterLabel} />
						</Text>
					</li>
				) : null}
			</ul>
		</div>
	);
}

interface DiffBadgeGroupProps {
	title: string;
	values: string[];
	highlight: Set<string>;
	emptyHint: string;
	variant: "added" | "removed";
}

function DiffBadgeGroup({ title, values, highlight, emptyHint, variant }: DiffBadgeGroupProps) {
	return (
		<div className="space-y-1">
			<Text variant="caption" className="text-muted-foreground">
				{title}
			</Text>
			{values.length === 0 ? (
				<Text variant="body3" className="text-muted-foreground">
					{emptyHint}
				</Text>
			) : (
				<div className="flex flex-wrap gap-1">
					{values.map((value) => (
						<Badge
							key={value}
							variant={highlight.has(value) ? "destructive" : "outline"}
							className={cn(
								"rounded-sm px-2 py-0.5 text-[11px]",
								highlight.has(value) ? "border-destructive/50 bg-destructive/10 text-destructive" : "text-muted-foreground",
							)}
						>
							{highlight.has(value) ? `${variant === "added" ? "+" : "−"} ${value}` : value}
						</Badge>
					))}
				</div>
			)}
		</div>
	);
}

interface RuleListProps {
	items: VisibilityRuleDisplay[];
	variant: "added" | "removed";
}

function RuleList({ items, variant }: RuleListProps) {
	if (items.length === 0) {
		return null;
	}
	const badgeVariant = variant === "added" ? "outline" : "destructive";
	return (
		<div className="flex flex-wrap gap-1">
			{items.map((item, idx) => (
				<Badge
					key={`${item.role ?? ""}-${item.permission ?? ""}-${item.dataLevelLabel ?? ""}-${idx}`}
					variant={badgeVariant}
					className={cn(
						"rounded-sm px-2 py-0.5 text-[11px]",
						variant === "added" ? "border-emerald-500/40 text-emerald-600" : "border-destructive/50 bg-destructive/10 text-destructive",
					)}
				>
					{renderRuleLabel(item)}
				</Badge>
			))}
		</div>
	);
}

function renderRuleLabel(rule: VisibilityRuleDisplay) {
	const pieces: string[] = [];
	if (rule.role) {
		pieces.push(rule.role);
	}
	if (rule.permission) {
		pieces.push(`权限:${rule.permission}`);
	}
	if (rule.dataLevelLabel) {
		pieces.push(`密级:${rule.dataLevelLabel}`);
	}
	return pieces.join(" ");
}

interface DiffInlineProps {
	before?: string;
	after?: string;
}

function DiffInline({ before, after }: DiffInlineProps) {
	return (
		<Fragment>
			<span>{before ?? "—"}</span>
			<span className="px-1 text-muted-foreground">→</span>
			<span className="text-destructive">{after ?? "—"}</span>
		</Fragment>
	);
}
