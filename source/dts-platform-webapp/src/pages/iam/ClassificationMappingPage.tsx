import { useEffect, useMemo, useState } from "react";
import { Icon } from "@/components/icon";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Separator } from "@/ui/separator";
import { ScrollArea } from "@/ui/scroll-area";
import iamService, {
	type DatasetClassificationItem,
	type SyncStatus,
	type UserClassificationItem,
} from "@/api/services/iamService";
import { useRouter } from "@/routes/hooks";
import SensitiveNotice from "@/components/security/SensitiveNotice";

function LevelBadge({ level }: { level: string }) {
	const color =
		level === "机密"
			? "bg-red-100 text-red-700 border-red-300"
			: level === "秘密"
				? "bg-orange-100 text-orange-700 border-orange-300"
				: level === "内部"
					? "bg-amber-100 text-amber-800 border-amber-300"
					: "bg-slate-100 text-slate-700 border-slate-300";
	return (
		<Badge variant="outline" className={`border ${color}`}>
			{level}
		</Badge>
	);
}

export default function ClassificationMappingPage() {
	const router = useRouter();
	const [keyword, setKeyword] = useState("");
	const [userResults, setUserResults] = useState<UserClassificationItem[]>([]);
	const [selectedUser, setSelectedUser] = useState<UserClassificationItem | null>(null);
	const [searching, setSearching] = useState(false);
	const [refreshing, setRefreshing] = useState(false);

	const [datasets, setDatasets] = useState<DatasetClassificationItem[]>([]);
	const [datasetKeyword, setDatasetKeyword] = useState("");

	const [syncStatus, setSyncStatus] = useState<SyncStatus | null>(null);
	const [syncing, setSyncing] = useState(false);
	const [retryingId, setRetryingId] = useState<string | null>(null);

	// load datasets + sync status on mount
	useEffect(() => {
		void iamService.getDatasets().then(setDatasets);
		void iamService.getSyncStatus().then(setSyncStatus);
	}, []);

	const filteredDatasets = useMemo(() => {
		const k = datasetKeyword.trim().toLowerCase();
		if (!k) return datasets;
		return datasets.filter(
			(ds) =>
				ds.name.toLowerCase().includes(k) || ds.domain.toLowerCase().includes(k) || ds.owner.toLowerCase().includes(k),
		);
	}, [datasetKeyword, datasets]);

	const onSearchUsers = async () => {
		setSearching(true);
		try {
			const list = await iamService.searchUsers(keyword.trim());
			setUserResults(list);
			setSelectedUser(list[0] || null);
		} finally {
			setSearching(false);
		}
	};

	const onRefreshUser = async () => {
		if (!selectedUser) return;
		setRefreshing(true);
		try {
			const next = await iamService.refreshUser(selectedUser.id);
			setSelectedUser(next);
			setUserResults((prev) => prev.map((u) => (u.id === next.id ? next : u)));
		} finally {
			setRefreshing(false);
		}
	};

	const onRunSync = async () => {
		setSyncing(true);
		try {
			const status = await iamService.runSync();
			setSyncStatus(status);
		} finally {
			setSyncing(false);
		}
	};

	const onRetryFailure = async (id: string) => {
		setRetryingId(id);
		try {
			const status = await iamService.retryFailure(id);
			setSyncStatus(status);
		} finally {
			setRetryingId(null);
		}
	};

	const goToSimulation = () => {
		if (!selectedUser) return;
		const params = new URLSearchParams({ user: selectedUser.username });
		router.push(`/iam/simulation?${params.toString()}`);
	};

	return (
		<div className="space-y-4">
			<SensitiveNotice />
			<div className="flex flex-col gap-2">
				<h2 className="text-lg font-semibold">密级模型映射</h2>
				<p className="text-sm text-muted-foreground">
					用户密级来自 dts-admin，数据集密级来自 Catalog；本页为只读总览与同步状态。
				</p>
			</div>

			<div className="grid gap-4 2xl:grid-cols-[420px,1fr]">
				{/* 用户密级面板 */}
				<Card>
					<CardHeader className="space-y-3">
						<CardTitle className="text-base flex items-center gap-2">
							<Icon icon="solar:user-bold-duotone" className="text-primary" /> 用户密级（只读）
						</CardTitle>
						<div className="flex gap-2">
							<Input
								placeholder="搜索用户名/姓名"
								value={keyword}
								onChange={(e) => setKeyword(e.target.value)}
								onKeyDown={(e) => {
									if (e.key === "Enter") onSearchUsers();
								}}
							/>
							<Button onClick={onSearchUsers} disabled={searching}>
								{searching ? "搜索中…" : "搜索"}
							</Button>
						</div>
					</CardHeader>
					<CardContent className="space-y-4">
						<div className="grid gap-2">
							<Label className="text-xs text-muted-foreground">匹配结果</Label>
							<div className="flex flex-wrap gap-2">
								{userResults.map((u) => (
									<button
										key={u.id}
										className={`rounded-md border px-3 py-1 text-xs ${
											selectedUser?.id === u.id ? "border-primary bg-primary/10" : ""
										}`}
										onClick={() => setSelectedUser(u)}
									>
										{u.displayName} <span className="text-muted-foreground">@{u.username}</span>
									</button>
								))}
								{!userResults.length && <span className="text-xs text-muted-foreground">无结果</span>}
							</div>
						</div>

						<Separator />

						{selectedUser ? (
							<div className="space-y-3">
								<div className="flex items-center justify-between">
									<div className="flex items-center gap-3">
										<LevelBadge level={selectedUser.securityLevel} />
										<div>
											<div className="font-medium">{selectedUser.displayName}</div>
											<div className="text-xs text-muted-foreground">@{selectedUser.username}</div>
										</div>
									</div>
									<div className="flex gap-2">
										<Button variant="outline" onClick={onRefreshUser} disabled={refreshing}>
											<Icon icon="solar:refresh-bold" />
											{refreshing ? "拉取中…" : "拉取最新"}
										</Button>
										<Button variant="secondary" onClick={goToSimulation}>
											<Icon icon="solar:bolt-bold-duotone" /> 策略模拟
										</Button>
									</div>
								</div>

								<div className="grid gap-3 text-sm">
									<div>
										<div className="text-xs text-muted-foreground">组织</div>
										<div className="flex flex-wrap gap-1">
											{selectedUser.orgPath.map((seg, i) => (
												<Badge key={`${seg}-${i}`} variant="secondary">
													{seg}
												</Badge>
											))}
										</div>
									</div>
									<div>
										<div className="text-xs text-muted-foreground">角色</div>
										<div className="flex flex-wrap gap-1">
											{selectedUser.roles.map((r) => (
												<Badge key={r} variant="outline">
													{r}
												</Badge>
											))}
										</div>
									</div>
									<div>
										<div className="text-xs text-muted-foreground">项目</div>
										<div className="flex flex-wrap gap-1">
											{selectedUser.projects.map((p) => (
												<Badge key={p}>{p}</Badge>
											))}
										</div>
									</div>
									{selectedUser.updatedAt && (
										<div className="text-xs text-muted-foreground">
											最近更新：{new Date(selectedUser.updatedAt).toLocaleString()}
										</div>
									)}
								</div>
							</div>
						) : (
							<div className="text-sm text-muted-foreground">请选择一位用户查看密级信息</div>
						)}
					</CardContent>
				</Card>

				{/* 数据集密级总览 */}
				<Card>
					<CardHeader className="space-y-3">
						<CardTitle className="text-base flex items-center gap-2">
							<Icon icon="solar:database-bold-duotone" className="text-primary" /> 数据集密级总览（只读）
						</CardTitle>
						<div className="flex items-center gap-2">
							<Input
								placeholder="搜索数据集/数据域/负责人"
								value={datasetKeyword}
								onChange={(e) => setDatasetKeyword(e.target.value)}
							/>
							<Button variant="outline" onClick={() => setDatasetKeyword("")}>
								清空
							</Button>
							<Button variant="secondary" onClick={() => router.push("/catalog/classification")}>
								跳转至 Catalog/分级页面
							</Button>
						</div>
					</CardHeader>
					<CardContent className="overflow-x-auto">
						<table className="w-full min-w-[880px] table-fixed border-collapse text-sm">
							<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
								<tr>
									<th className="px-3 py-2 font-medium">数据集</th>
									<th className="px-3 py-2 font-medium">密级</th>
									<th className="px-3 py-2 font-medium">来源域</th>
									<th className="px-3 py-2 font-medium">负责人</th>
								</tr>
							</thead>
							<tbody>
								{filteredDatasets.map((ds) => (
									<tr key={ds.id} className="border-b last:border-b-0">
										<td className="px-3 py-2 font-medium">{ds.name}</td>
										<td className="px-3 py-2">
											<LevelBadge level={ds.classification} />
										</td>
										<td className="px-3 py-2">{ds.domain}</td>
										<td className="px-3 py-2">{ds.owner}</td>
									</tr>
								))}
								{!filteredDatasets.length && (
									<tr>
										<td colSpan={4} className="px-3 py-8 text-center text-xs text-muted-foreground">
											无匹配数据集
										</td>
									</tr>
								)}
							</tbody>
						</table>
					</CardContent>
				</Card>
			</div>

			{/* 同步状态卡片 */}
			<Card>
				<CardHeader className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
					<CardTitle className="text-base flex items-center gap-2">
						<Icon icon="solar:refresh-circle-bold-duotone" className="text-primary" /> 同步状态
					</CardTitle>
					<div className="flex gap-2">
						<Button variant="outline" onClick={() => iamService.getSyncStatus().then(setSyncStatus)}>
							<Icon icon="solar:refresh-bold" /> 刷新
						</Button>
						<Button onClick={onRunSync} disabled={syncing}>
							<Icon icon="solar:play-bold-duotone" /> {syncing ? "同步中…" : "立即同步"}
						</Button>
					</div>
				</CardHeader>
				<CardContent>
					{syncStatus ? (
						<div className="grid gap-4 md:grid-cols-3">
							<div className="rounded-lg border p-4">
								<div className="text-xs text-muted-foreground">上次同步时间</div>
								<div className="text-sm font-medium">{new Date(syncStatus.lastSyncAt).toLocaleString()}</div>
							</div>
							<div className="rounded-lg border p-4">
								<div className="text-xs text-muted-foreground">增量数量</div>
								<div className="text-sm font-medium">{syncStatus.deltaCount}</div>
							</div>
							<div className="rounded-lg border p-4">
								<div className="text-xs text-muted-foreground">失败项</div>
								<div className="text-sm font-medium">{syncStatus.failures.length}</div>
							</div>

							<div className="md:col-span-3">
								<Label className="text-xs text-muted-foreground">失败列表（可重试）</Label>
								<ScrollArea className="mt-2 max-h-[240px]">
									<table className="w-full table-fixed border-collapse text-sm">
										<thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
											<tr>
												<th className="px-3 py-2 font-medium w-[120px]">类型</th>
												<th className="px-3 py-2 font-medium">目标</th>
												<th className="px-3 py-2 font-medium">原因</th>
												<th className="px-3 py-2 font-medium w-[120px]">操作</th>
											</tr>
										</thead>
										<tbody>
											{syncStatus.failures.map((f) => (
												<tr key={f.id} className="border-b last:border-b-0">
													<td className="px-3 py-2">
														<Badge variant="outline">{f.type}</Badge>
													</td>
													<td className="px-3 py-2">{f.target}</td>
													<td className="px-3 py-2">{f.reason}</td>
													<td className="px-3 py-2">
														<Button
															size="sm"
															variant="outline"
															onClick={() => onRetryFailure(f.id)}
															disabled={retryingId === f.id}
														>
															{retryingId === f.id ? "重试中…" : "重试"}
														</Button>
													</td>
												</tr>
											))}
											{!syncStatus.failures.length && (
												<tr>
													<td colSpan={4} className="px-3 py-6 text-center text-xs text-muted-foreground">
														当前无失败项
													</td>
												</tr>
											)}
										</tbody>
									</table>
								</ScrollArea>
							</div>
						</div>
					) : (
						<div className="text-sm text-muted-foreground">加载同步状态中…</div>
					)}
				</CardContent>
			</Card>
		</div>
	);
}
