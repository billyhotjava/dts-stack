import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import { listStandards, createStandard, updateStandard, deleteStandard } from "@/api/platformApi";
import { Icon } from "@/components/icon";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { ScrollArea } from "@/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Textarea } from "@/ui/textarea";
import {
    DataStandardDto,
    DataStandardStatus,
    DOMAIN_OPTIONS,
    STATUS_OPTIONS,
    formatDate,
    statusLabel,
    toTagList,
} from "@/pages/modeling/data-standards-utils";

type FormState = {
    code: string;
    name: string;
    scope: string;
    owner: string;
    tagsText: string;
    status: DataStandardStatus;
    version: string;
    versionNotes: string;
    changeSummary: string;
    description: string;
};

const PAGE_SIZE = 10;

const DEFAULT_FORM: FormState = {
    code: "",
    name: "",
    scope: "",
    owner: "",
    tagsText: "",
    status: "DRAFT",
    version: "v1",
    versionNotes: "",
    changeSummary: "",
    description: "",
};

const DataStandardsPage = () => {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(false);
    const [standards, setStandards] = useState<DataStandardDto[]>([]);
    const [total, setTotal] = useState(0);
    const [page, setPage] = useState(0);
    const [filters, setFilters] = useState({ keyword: "", domain: "ALL", status: "ALL" });
    const [createOpen, setCreateOpen] = useState(false);
    const [formState, setFormState] = useState<FormState>(DEFAULT_FORM);
    const [creating, setCreating] = useState(false);
    const [publishingId, setPublishingId] = useState<string | null>(null);

    const loadStandards = useCallback(async () => {
        setLoading(true);
        try {
            const params: Record<string, any> = { page, size: PAGE_SIZE };
            if (filters.keyword.trim()) params.keyword = filters.keyword.trim();
            if (filters.domain !== "ALL") params.domain = filters.domain;
            if (filters.status !== "ALL") params.status = filters.status;
            const data: any = await listStandards(params);
            const content = Array.isArray(data?.content) ? data.content : [];
            const mapped: DataStandardDto[] = content.map((item: any) => ({ ...item }));
            setStandards(mapped);
            setTotal(data?.total ?? 0);
        } catch (error: any) {
            console.error(error);
            toast.error(error?.message ?? "加载数据标准失败");
        } finally {
            setLoading(false);
        }
    }, [filters, page]);

    useEffect(() => {
        void loadStandards();
    }, [loadStandards]);

    const openCreate = () => {
        setFormState({ ...DEFAULT_FORM });
        setCreateOpen(true);
    };

    const submitCreateForm = async () => {
        if (!formState.code.trim() || !formState.name.trim()) {
            toast.error("请填写名称和编码");
            return;
        }
        setCreating(true);
        const payload = {
            code: formState.code.trim(),
            name: formState.name.trim(),
            scope: formState.scope || undefined,
            owner: formState.owner || undefined,
            tags: toTagList(formState.tagsText),
            status: formState.status,
            version: formState.version.trim() || "v1",
            versionNotes: formState.versionNotes || undefined,
            changeSummary: formState.changeSummary || undefined,
            description: formState.description || undefined,
        };
        try {
            const saved: any = await createStandard(payload);
            toast.success("已新增数据标准");
            await loadStandards();
            if (saved?.id) {
                navigate(`/modeling/standards/${saved.id}?module=basic&edit=true`);
            }
            setCreateOpen(false);
            setFormState({ ...DEFAULT_FORM });
        } catch (error: any) {
            console.error(error);
            toast.error(error?.message ?? "保存失败");
        } finally {
            setCreating(false);
        }
    };

    const handleDelete = async (id: string) => {
        if (!window.confirm("确认删除该数据标准？")) {
            return;
        }
        try {
            await deleteStandard(id);
            toast.success("已删除数据标准");
            await loadStandards();
        } catch (error: any) {
            console.error(error);
            toast.error(error?.message ?? "删除失败");
        }
    };

    const handlePublish = async (standard: DataStandardDto) => {
        if (standard.status === "ACTIVE") {
            toast.success("该数据标准已发布");
            return;
        }
        if (!window.confirm("确认发布该数据标准？")) {
            return;
        }
        setPublishingId(standard.id);
        const payload = {
            code: standard.code,
            name: standard.name,
            scope: standard.scope || undefined,
            owner: standard.owner || undefined,
            tags: Array.isArray(standard.tags) ? standard.tags : [],
            status: "ACTIVE" as DataStandardStatus,
            version: standard.currentVersion ?? "v1",
            versionNotes: standard.versionNotes || undefined,
            changeSummary: undefined,
            description: standard.description || undefined,
        };
        try {
            await updateStandard(standard.id, payload);
            toast.success("数据标准已发布");
            await loadStandards();
        } catch (error: any) {
            console.error(error);
            toast.error(error?.message ?? "发布失败");
        } finally {
            setPublishingId(null);
        }
    };

    const totalPages = useMemo(() => Math.ceil(total / PAGE_SIZE), [total]);

    return (
        <div className="space-y-6">
            <div className="flex items-center justify-center gap-2 rounded-md border border-dashed border-red-200 bg-red-50 px-4 py-3 text-center text-sm font-medium text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200">
                <Icon icon="mdi:star" className="h-5 w-5 text-red-500" />
                <span className="text-center">非密模块禁止处理涉密数据</span>
            </div>
            <Card>
                <CardHeader className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                    <div>
                        <CardTitle>数据标准台账</CardTitle>
                        <p className="text-sm text-muted-foreground">快速维护数据标准基础信息、负责人与版本情况</p>
                    </div>
                    <div className="flex gap-2">
                        <Button onClick={openCreate}>新建标准</Button>
                    </div>
                </CardHeader>
                <CardContent className="space-y-4">
                    <div className="grid gap-3 md:grid-cols-4">
                        <Input
                            placeholder="搜索名称 / 编码 / 负责人"
                            value={filters.keyword}
                            onChange={(event) => {
                                setFilters((prev) => ({ ...prev, keyword: event.target.value }));
                                setPage(0);
                            }}
                        />
                        <Select
                            value={filters.domain}
                            onValueChange={(value) => {
                                setFilters((prev) => ({ ...prev, domain: value }));
                                setPage(0);
                            }}
                        >
                            <SelectTrigger>
                                <SelectValue placeholder="所属域" />
                            </SelectTrigger>
                            <SelectContent>
                                <SelectItem value="ALL">全部域</SelectItem>
                                {DOMAIN_OPTIONS.map((item) => (
                                    <SelectItem key={item} value={item}>
                                        {item}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                        <Select
                            value={filters.status}
                            onValueChange={(value) => {
                                setFilters((prev) => ({ ...prev, status: value }));
                                setPage(0);
                            }}
                        >
                            <SelectTrigger>
                                <SelectValue placeholder="状态" />
                            </SelectTrigger>
                            <SelectContent>
                                <SelectItem value="ALL">全部状态</SelectItem>
                                {STATUS_OPTIONS.map((item) => (
                                    <SelectItem key={item.value} value={item.value}>
                                        {item.label}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    </div>

                    <div className="overflow-x-auto">
                        <table className="w-full min-w-[960px] table-fixed border-collapse text-sm">
                            <thead className="bg-muted/40">
                                <tr>
                                    <th className="w-48 px-3 py-3 text-left">名称 / 编码</th>
                                    <th className="w-28 px-3 py-3 text-left">所属域</th>
                                    <th className="w-32 px-3 py-3 text-left">负责人</th>
                                    <th className="w-28 px-3 py-3 text-left">状态</th>
                                    <th className="w-24 px-3 py-3 text-left">当前版本</th>
                                    <th className="w-32 px-3 py-3 text-left">更新时间</th>
                                    <th className="px-3 py-3 text-right">操作</th>
                                </tr>
                            </thead>
                            <tbody>
                                {loading ? (
                                    <tr>
                                        <td colSpan={7} className="px-3 py-6 text-center text-sm text-muted-foreground">
                                            正在加载数据标准...
                                        </td>
                                    </tr>
                                ) : standards.length ? (
                                    standards.map((standard) => (
                                        <tr key={standard.id} className="border-b last:border-none">
                                            <td className="px-3 py-3">
                                                <div className="font-medium">{standard.name}</div>
                                                <div className="text-xs text-muted-foreground">{standard.code}</div>
                                            </td>
                                            <td className="px-3 py-3">{standard.domain ?? "-"}</td>
                                            <td className="px-3 py-3">{standard.owner ?? "-"}</td>
                                            <td className="px-3 py-3">
                                                <Badge variant={standard.status === "ACTIVE" ? "default" : "secondary"}>
                                                    {statusLabel(standard.status)}
                                                </Badge>
                                            </td>
                                            <td className="px-3 py-3">{standard.currentVersion ?? "-"}</td>
                                            <td className="px-3 py-3">{formatDate(standard.lastModifiedDate ?? standard.createdDate)}</td>
                                            <td className="px-3 py-3 text-right">
                                                <div className="flex justify-end gap-2">
                                                    <Button
                                                        size="sm"
                                                        variant="outline"
                                                        onClick={() =>
                                                            navigate(
                                                                `/modeling/standards/${standard.id}?module=basic&edit=true`
                                                            )
                                                        }
                                                    >
                                                        编辑
                                                    </Button>
                                                    <Button
                                                        size="sm"
                                                        onClick={() => handlePublish(standard)}
                                                        disabled={publishingId === standard.id}
                                                    >
                                                        {publishingId === standard.id ? "发布中..." : "发布"}
                                                    </Button>
                                                    <Button
                                                        size="sm"
                                                        variant="destructive"
                                                        onClick={() => handleDelete(standard.id)}
                                                    >
                                                        删除
                                                    </Button>
                                                </div>
                                            </td>
                                        </tr>
                                    ))
                                ) : (
                                    <tr>
                                        <td colSpan={7} className="px-3 py-6 text-center text-sm text-muted-foreground">
                                            暂无数据标准，请点击“新建标准”开始维护
                                        </td>
                                    </tr>
                                )}
                            </tbody>
                        </table>
                    </div>

                    <div className="flex items-center justify-between text-sm text-muted-foreground">
                        <div>共 {total} 条记录</div>
                        <div className="flex items-center gap-2">
                            <Button
                                variant="outline"
                                size="sm"
                                disabled={page === 0}
                                onClick={() => setPage((prev) => Math.max(0, prev - 1))}
                            >
                                上一页
                            </Button>
                            <span>
                                第 {page + 1} / {Math.max(totalPages, 1)} 页
                            </span>
                            <Button
                                variant="outline"
                                size="sm"
                                disabled={page + 1 >= totalPages}
                                onClick={() => setPage((prev) => prev + 1)}
                            >
                                下一页
                            </Button>
                        </div>
                    </div>
                </CardContent>
            </Card>

            <Dialog open={createOpen} onOpenChange={(open) => {
                setCreateOpen(open);
                if (!open) {
                    setFormState({ ...DEFAULT_FORM });
                }
            }}>
                <DialogContent className="max-w-2xl">
                    <DialogHeader>
                        <DialogTitle>新建数据标准</DialogTitle>
                    </DialogHeader>
                    <ScrollArea className="max-h-[70vh]">
                        <div className="space-y-4 pr-4">
                            <div className="grid gap-4 md:grid-cols-2">
                                <div>
                                    <Label className="text-sm">名称</Label>
                                    <Input
                                        value={formState.name}
                                        onChange={(event) => setFormState((prev) => ({ ...prev, name: event.target.value }))}
                                        placeholder="请输入标准名称"
                                    />
                                </div>
                                <div>
                                    <Label className="text-sm">编码</Label>
                                    <Input
                                        value={formState.code}
                                        onChange={(event) => setFormState((prev) => ({ ...prev, code: event.target.value }))}
                                        placeholder="STD-XXX"
                                    />
                                </div>
                                <div>
                                    <Label className="text-sm">负责人</Label>
                                    <Input
                                        value={formState.owner}
                                        onChange={(event) => setFormState((prev) => ({ ...prev, owner: event.target.value }))}
                                        placeholder="请输入负责人"
                                    />
                                </div>
                                <div>
                                    <Label className="text-sm">状态</Label>
                                    <Select
                                        value={formState.status}
                                        onValueChange={(value: DataStandardStatus) =>
                                            setFormState((prev) => ({ ...prev, status: value }))
                                        }
                                    >
                                        <SelectTrigger>
                                            <SelectValue />
                                        </SelectTrigger>
                                        <SelectContent>
                                            {STATUS_OPTIONS.map((item) => (
                                                <SelectItem key={item.value} value={item.value}>
                                                    {item.label}
                                                </SelectItem>
                                            ))}
                                        </SelectContent>
                                    </Select>
                                </div>
                                <div>
                                    <Label className="text-sm">适用范围</Label>
                                    <Input
                                        value={formState.scope}
                                        onChange={(event) => setFormState((prev) => ({ ...prev, scope: event.target.value }))}
                                        placeholder="业务范围说明"
                                    />
                                </div>
                                <div>
                                    <Label className="text-sm">标签</Label>
                                    <Input
                                        value={formState.tagsText}
                                        onChange={(event) => setFormState((prev) => ({ ...prev, tagsText: event.target.value }))}
                                        placeholder="逗号分隔，如：主数据,共享"
                                    />
                                </div>
                                <div>
                                    <Label className="text-sm">版本号</Label>
                                    <Input
                                        value={formState.version}
                                        onChange={(event) => setFormState((prev) => ({ ...prev, version: event.target.value }))}
                                        placeholder="v1"
                                    />
                                </div>
                                <div>
                                    <Label className="text-sm">版本说明</Label>
                                    <Input
                                        value={formState.versionNotes}
                                        onChange={(event) => setFormState((prev) => ({ ...prev, versionNotes: event.target.value }))}
                                        placeholder="版本说明"
                                    />
                                </div>
                            </div>
                            <div>
                                <Label className="text-sm">变更摘要（用于记录版本调整）</Label>
                                <Textarea
                                    value={formState.changeSummary}
                                    onChange={(event) => setFormState((prev) => ({ ...prev, changeSummary: event.target.value }))}
                                    placeholder="简要描述本次版本的主要变更"
                                    rows={3}
                                />
                            </div>
                            <div>
                                <Label className="text-sm">标准描述</Label>
                                <Textarea
                                    value={formState.description}
                                    onChange={(event) => setFormState((prev) => ({ ...prev, description: event.target.value }))}
                                    placeholder="补充标准定义、指标口径等信息"
                                    rows={5}
                                />
                            </div>
                        </div>
                    </ScrollArea>
                    <DialogFooter>
                        <Button
                            variant="outline"
                            onClick={() => {
                                setCreateOpen(false);
                                setFormState({ ...DEFAULT_FORM });
                            }}
                        >
                            取消
                        </Button>
                        <Button onClick={submitCreateForm} disabled={creating}>
                            {creating ? "保存中..." : "保存"}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
};

export default DataStandardsPage;
