import { useCallback, useEffect, useMemo, useState, type ChangeEvent } from "react";
import {
    listStandards,
    getStandard,
    createStandard,
    updateStandard,
    deleteStandard,
    listStandardVersions,
    listStandardAttachments,
    uploadStandardAttachment,
    deleteStandardAttachment,
} from "@/api/platformApi";
import { GLOBAL_CONFIG } from "@/global-config";
import userStore from "@/store/userStore";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { ScrollArea } from "@/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Separator } from "@/ui/separator";
import { Textarea } from "@/ui/textarea";
import { toast } from "sonner";

interface DataStandardDto {
    id: string;
    code: string;
    name: string;
    domain?: string;
    scope?: string;
    status: DataStandardStatus;
    securityLevel: DataLevel;
    owner?: string;
    tags?: string[];
    currentVersion: string;
    versionNotes?: string;
    description?: string;
    reviewCycle?: string;
    lastReviewAt?: string;
    createdDate?: string;
    createdBy?: string;
    lastModifiedDate?: string;
    lastModifiedBy?: string;
}

interface DataStandardVersionDto {
    id: string;
    version: string;
    status: "DRAFT" | "IN_REVIEW" | "PUBLISHED" | "ARCHIVED";
    changeSummary?: string;
    snapshotJson?: string;
    releasedAt?: string;
    createdDate?: string;
    createdBy?: string;
}

interface DataStandardAttachmentDto {
    id: string;
    fileName: string;
    contentType?: string;
    fileSize: number;
    sha256?: string;
    keyVersion?: string;
    version?: string;
    createdDate?: string;
    createdBy?: string;
}

type DataStandardStatus = "DRAFT" | "IN_REVIEW" | "ACTIVE" | "DEPRECATED" | "RETIRED";
type DataLevel = "DATA_PUBLIC" | "DATA_INTERNAL" | "DATA_SECRET" | "DATA_TOP_SECRET";

type FormState = {
    code: string;
    name: string;
    domain: string;
    scope: string;
    owner: string;
    tagsText: string;
    status: DataStandardStatus;
    securityLevel: DataLevel;
    version: string;
    versionNotes: string;
    changeSummary: string;
    description: string;
};

const STATUS_OPTIONS: { value: DataStandardStatus; label: string }[] = [
    { value: "DRAFT", label: "草稿" },
    { value: "IN_REVIEW", label: "待审批" },
    { value: "ACTIVE", label: "启用" },
    { value: "DEPRECATED", label: "已归档" },
    { value: "RETIRED", label: "已销毁" },
];

const DATA_LEVELS: { value: DataLevel; label: string }[] = [
    { value: "DATA_PUBLIC", label: "公开 (DATA_PUBLIC)" },
    { value: "DATA_INTERNAL", label: "内部 (DATA_INTERNAL)" },
    { value: "DATA_SECRET", label: "秘密 (DATA_SECRET)" },
    { value: "DATA_TOP_SECRET", label: "机密 (DATA_TOP_SECRET)" },
];

const DOMAIN_OPTIONS = ["主数据域", "共享域", "核心域", "分析域"];

const DEFAULT_FORM: FormState = {
    code: "",
    name: "",
    domain: "",
    scope: "",
    owner: "",
    tagsText: "",
    status: "DRAFT",
    securityLevel: "DATA_INTERNAL",
    version: "v1",
    versionNotes: "",
    changeSummary: "",
    description: "",
};

const PAGE_SIZE = 10;

const formatDate = (value?: string) => {
    if (!value) return "-";
    try {
        return new Date(value).toLocaleString();
    } catch {
        return value;
    }
};

const humanFileSize = (size: number) => {
    if (!size) return "0";
    if (size < 1024) return `${size} B`;
    if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
    if (size < 1024 * 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`;
    return `${(size / 1024 / 1024 / 1024).toFixed(1)} GB`;
};

const toLegacyLevel = (v: DataLevel): "PUBLIC" | "INTERNAL" | "SECRET" | "TOP_SECRET" =>
    v === "DATA_PUBLIC" ? "PUBLIC" : v === "DATA_INTERNAL" ? "INTERNAL" : v === "DATA_SECRET" ? "SECRET" : "TOP_SECRET";
const fromLegacyLevel = (v: string): DataLevel => {
    const u = String(v || "").toUpperCase();
    if (u === "PUBLIC") return "DATA_PUBLIC";
    if (u === "INTERNAL") return "DATA_INTERNAL";
    if (u === "SECRET") return "DATA_SECRET";
    if (u === "TOP_SECRET") return "DATA_TOP_SECRET";
    return "DATA_INTERNAL";
};
const securityLabel = (level: DataLevel) => DATA_LEVELS.find((item) => item.value === level)?.label ?? level;

const statusLabel = (status: DataStandardStatus) =>
    STATUS_OPTIONS.find((item) => item.value === status)?.label ?? status;

const toTagList = (input: string): string[] =>
    input
        .split(",")
        .map((item) => item.trim())
        .filter(Boolean);

const fromTagList = (value?: string[] | null) => (value && value.length ? value.join(", ") : "");

const DataStandardsPage = () => {
    const [loading, setLoading] = useState(false);
    const [standards, setStandards] = useState<DataStandardDto[]>([]);
    const [total, setTotal] = useState(0);
    const [page, setPage] = useState(0);
    const [filters, setFilters] = useState({ keyword: "", domain: "ALL", status: "ALL", security: "ALL" });
    const [selectedId, setSelectedId] = useState<string | null>(null);
    const [selectedDetail, setSelectedDetail] = useState<DataStandardDto | null>(null);
    const [versions, setVersions] = useState<DataStandardVersionDto[]>([]);
    const [attachments, setAttachments] = useState<DataStandardAttachmentDto[]>([]);
    const [formOpen, setFormOpen] = useState(false);
    const [formMode, setFormMode] = useState<"create" | "edit">("create");
    const [formState, setFormState] = useState<FormState>(DEFAULT_FORM);
    const [saving, setSaving] = useState(false);
    const [uploading, setUploading] = useState(false);

    const loadStandards = useCallback(async () => {
        setLoading(true);
        try {
            const params: Record<string, any> = { page, size: PAGE_SIZE };
            if (filters.keyword.trim()) params.keyword = filters.keyword.trim();
            if (filters.domain !== "ALL") params.domain = filters.domain;
            if (filters.status !== "ALL") params.status = filters.status;
            if (filters.security !== "ALL") {
                // backend expects legacy; convert DATA_* → legacy
                params.securityLevel = toLegacyLevel(filters.security as DataLevel);
            }
            const data: any = await listStandards(params);
            const content = Array.isArray(data?.content) ? data.content : [];
            const mapped: DataStandardDto[] = content.map((it: any) => ({
                ...it,
                // Normalize legacy to DATA_* for display
                securityLevel: fromLegacyLevel(it?.securityLevel || it?.dataLevel || "INTERNAL"),
            }));
            setStandards(mapped);
            setTotal(data?.total ?? 0);
        } catch (error: any) {
            console.error(error);
            toast.error(error?.message ?? "加载数据标准失败");
        } finally {
            setLoading(false);
        }
    }, [filters, page]);

    const loadDetail = useCallback(
        async (id: string) => {
            try {
                const [detail, versionList, attachmentList] = (await Promise.all([
                    getStandard(id),
                    listStandardVersions(id),
                    listStandardAttachments(id),
                ])) as [any, any[], any[]];
                setSelectedDetail(detail as any);
                setVersions((versionList as any[]) ?? []);
                setAttachments((attachmentList as any[]) ?? []);
            } catch (error: any) {
                console.error(error);
                toast.error(error?.message ?? "加载数据标准详情失败");
            }
        },
        []
    );

    useEffect(() => {
        loadStandards();
    }, [loadStandards]);

    useEffect(() => {
        if (selectedId) {
            loadDetail(selectedId);
        }
    }, [selectedId, loadDetail]);

    const handleSelect = (id: string) => {
        setSelectedId(id);
    };

    const openCreate = () => {
        setFormMode("create");
        setFormState({ ...DEFAULT_FORM });
        setFormOpen(true);
    };

    const openEdit = () => {
        if (!selectedDetail) return;
        setFormMode("edit");
        setFormState({
            code: selectedDetail.code,
            name: selectedDetail.name,
            domain: selectedDetail.domain ?? "",
            scope: selectedDetail.scope ?? "",
            owner: selectedDetail.owner ?? "",
            tagsText: fromTagList(selectedDetail.tags),
            status: selectedDetail.status,
            securityLevel: fromLegacyLevel(selectedDetail.securityLevel),
            version: selectedDetail.currentVersion ?? "v1",
            versionNotes: selectedDetail.versionNotes ?? "",
            changeSummary: "",
            description: selectedDetail.description ?? "",
        });
        setFormOpen(true);
    };

    const submitForm = async () => {
        if (!formState.code.trim() || !formState.name.trim()) {
            toast.error("请填写名称和编码");
            return;
        }
        setSaving(true);
        const payload = {
            code: formState.code.trim(),
            name: formState.name.trim(),
            domain: formState.domain || undefined,
            scope: formState.scope || undefined,
            owner: formState.owner || undefined,
            tags: toTagList(formState.tagsText),
            status: formState.status,
            securityLevel: toLegacyLevel(formState.securityLevel),
            version: formState.version.trim() || "v1",
            versionNotes: formState.versionNotes || undefined,
            changeSummary: formState.changeSummary || undefined,
            description: formState.description || undefined,
        };
        try {
            if (formMode === "create") {
                const saved: any = await createStandard(payload);
                toast.success("已新增数据标准");
                setSelectedId(saved?.id ?? null);
            } else if (selectedId) {
                await updateStandard(selectedId, payload);
                toast.success("已更新数据标准");
            }
            setFormOpen(false);
            setFormState({ ...DEFAULT_FORM });
            await loadStandards();
            if (selectedId) {
                await loadDetail(selectedId);
            }
        } catch (error: any) {
            console.error(error);
            toast.error(error?.message ?? "保存失败");
        } finally {
            setSaving(false);
        }
    };

    const handleDelete = async (id: string) => {
        if (!window.confirm("确认删除该数据标准？")) {
            return;
        }
        try {
            await deleteStandard(id);
            toast.success("已删除数据标准");
            if (selectedId === id) {
                setSelectedId(null);
                setSelectedDetail(null);
                setVersions([]);
                setAttachments([]);
            }
            await loadStandards();
        } catch (error: any) {
            console.error(error);
            toast.error(error?.message ?? "删除失败");
        }
    };

    const handleUpload = async (event: ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        if (!file || !selectedId) {
            return;
        }
        const formData = new FormData();
        formData.append("file", file);
        if (selectedDetail?.currentVersion) {
            formData.append("version", selectedDetail.currentVersion);
        }
        setUploading(true);
        try {
            await uploadStandardAttachment(selectedId, formData);
            toast.success("附件上传成功（已加密存储）");
            await loadDetail(selectedId);
        } catch (error: any) {
            console.error(error);
            toast.error(error?.message ?? "附件上传失败");
        } finally {
            setUploading(false);
            event.target.value = "";
        }
    };

    const handleAttachmentDelete = async (attachmentId: string) => {
        if (!selectedId) return;
        if (!window.confirm("确认删除该附件？")) return;
        try {
            await deleteStandardAttachment(selectedId, attachmentId);
            toast.success("附件已删除");
            await loadDetail(selectedId);
        } catch (error: any) {
            console.error(error);
            toast.error(error?.message ?? "删除附件失败");
        }
    };

    const handleDownload = async (attachment: DataStandardAttachmentDto) => {
        if (!selectedId) return;
        try {
            const url = `${GLOBAL_CONFIG.apiBaseUrl}/modeling/standards/${selectedId}/attachments/${attachment.id}/download`;
            const { userToken } = userStore.getState();
            const token = userToken.accessToken;
            const response = await fetch(url, {
                headers: token ? { Authorization: token.startsWith("Bearer") ? token : `Bearer ${token}` } : {},
            });
            if (!response.ok) {
                throw new Error("下载失败");
            }
            const blob = await response.blob();
            const downloadUrl = window.URL.createObjectURL(blob);
            const anchor = document.createElement("a");
            anchor.href = downloadUrl;
            anchor.download = attachment.fileName;
            document.body.appendChild(anchor);
            anchor.click();
            anchor.remove();
            window.URL.revokeObjectURL(downloadUrl);
        } catch (error: any) {
            console.error(error);
            toast.error(error?.message ?? "下载附件失败");
        }
    };

    const totalPages = useMemo(() => Math.ceil(total / PAGE_SIZE), [total]);

    return (
        <div className="space-y-6">
            <Card>
                <CardHeader className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                    <div>
                        <CardTitle>数据标准台账</CardTitle>
                        <p className="text-sm text-muted-foreground">快速维护数据标准基本信息与数据密级（DATA_*）、版本情况</p>
                    </div>
                    <div className="flex gap-2">
                        <Button onClick={openCreate}>新建标准</Button>
                        <Button variant="outline" disabled={!selectedId} onClick={openEdit}>
                            编辑
                        </Button>
                        <Button variant="destructive" disabled={!selectedId} onClick={() => selectedId && handleDelete(selectedId)}>
                            删除
                        </Button>
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
                        <Select
                            value={filters.security}
                            onValueChange={(value) => {
                                setFilters((prev) => ({ ...prev, security: value }));
                                setPage(0);
                            }}
                        >
                            <SelectTrigger>
                                <SelectValue placeholder="数据密级（DATA_*）" />
                            </SelectTrigger>
                            <SelectContent>
                                <SelectItem value="ALL">全部数据密级</SelectItem>
                                {DATA_LEVELS.map((item) => (
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
                                    <th className="w-28 px-3 py-3 text-left">数据密级（DATA_*）</th>
                                    <th className="w-24 px-3 py-3 text-left">当前版本</th>
                                    <th className="w-32 px-3 py-3 text-left">更新时间</th>
                                    <th className="px-3 py-3 text-right">操作</th>
                                </tr>
                            </thead>
                            <tbody>
                                {loading ? (
                                    <tr>
                                        <td colSpan={8} className="px-3 py-6 text-center text-sm text-muted-foreground">
                                            正在加载数据标准...
                                        </td>
                                    </tr>
                                ) : standards.length ? (
                                    standards.map((standard) => (
                                        <tr
                                            key={standard.id}
                                            className={`border-b last:border-none ${selectedId === standard.id ? "bg-muted/40" : ""}`}
                                        >
                                            <td className="px-3 py-3">
                                                <div className="font-medium">{standard.name}</div>
                                                <div className="text-xs text-muted-foreground">{standard.code}</div>
                                            </td>
                                            <td className="px-3 py-3">{standard.domain ?? "-"}</td>
                                            <td className="px-3 py-3">{standard.owner ?? "-"}</td>
                                            <td className="px-3 py-3">
                                                <Badge variant={standard.status === "ACTIVE" ? "default" : "secondary"}>{statusLabel(standard.status)}</Badge>
                                            </td>
                                            <td className="px-3 py-3">
                                                <Badge variant="outline">{securityLabel(standard.securityLevel)}</Badge>
                                            </td>
                                            <td className="px-3 py-3">{standard.currentVersion ?? "-"}</td>
                                            <td className="px-3 py-3">{formatDate(standard.lastModifiedDate ?? standard.createdDate)}</td>
                                            <td className="px-3 py-3 text-right">
                                                <div className="flex justify-end gap-2">
                                                    <Button size="sm" variant="ghost" onClick={() => handleSelect(standard.id)}>
                                                        查看
                                                    </Button>
                                                    <Button size="sm" variant="outline" onClick={() => handleDelete(standard.id)}>
                                                        删除
                                                    </Button>
                                                </div>
                                            </td>
                                        </tr>
                                    ))
                                ) : (
                                    <tr>
                                        <td colSpan={8} className="px-3 py-6 text-center text-sm text-muted-foreground">
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

            {selectedDetail ? (
                <Card>
                    <CardHeader>
                        <CardTitle>标准详情</CardTitle>
                    </CardHeader>
                    <CardContent className="grid gap-6 lg:grid-cols-2">
                        <div className="space-y-4">
                            <div>
                                <h3 className="text-sm font-semibold text-muted-foreground">基础信息</h3>
                                <Separator className="my-2" />
                                <div className="grid grid-cols-2 gap-2 text-sm">
                                    <span className="text-muted-foreground">名称</span>
                                    <span>{selectedDetail.name}</span>
                                    <span className="text-muted-foreground">编码</span>
                                    <span>{selectedDetail.code}</span>
                                    <span className="text-muted-foreground">所属域</span>
                                    <span>{selectedDetail.domain ?? "-"}</span>
                                    <span className="text-muted-foreground">负责人</span>
                                    <span>{selectedDetail.owner ?? "-"}</span>
                                    <span className="text-muted-foreground">数据密级（DATA_*）</span>
                                    <span>{securityLabel(selectedDetail.securityLevel)}</span>
                                    <span className="text-muted-foreground">当前版本</span>
                                    <span>{selectedDetail.currentVersion}</span>
                                    <span className="text-muted-foreground">最新更新时间</span>
                                    <span>{formatDate(selectedDetail.lastModifiedDate ?? selectedDetail.createdDate)}</span>
                                    <span className="text-muted-foreground">标签</span>
                                    <span>{selectedDetail.tags?.length ? selectedDetail.tags.join(", ") : "-"}</span>
                                </div>
                            </div>
                            <div>
                                <h3 className="text-sm font-semibold text-muted-foreground">描述与说明</h3>
                                <Separator className="my-2" />
                                <p className="rounded-md border bg-muted/10 p-3 text-sm leading-relaxed">
                                    {selectedDetail.description || "暂无描述"}
                                </p>
                            </div>
                        </div>

                        <div className="space-y-6">
                            <div>
                                <div className="flex items-center justify-between">
                                    <h3 className="text-sm font-semibold text-muted-foreground">版本记录</h3>
                                    <span className="text-xs text-muted-foreground">共 {versions.length} 个版本</span>
                                </div>
                                <Separator className="my-2" />
                                <ScrollArea className="h-48 rounded-md border p-3">
                                    {versions.length ? (
                                        <div className="space-y-3">
                                            {versions.map((version) => (
                                                <div key={version.id} className="rounded-md border p-3">
                                                    <div className="flex items-center justify-between text-sm font-medium">
                                                        <span>{version.version}</span>
                                                        <Badge variant={version.status === "PUBLISHED" ? "default" : "secondary"}>
                                                            {version.status === "PUBLISHED" ? "已发布" : version.status === "IN_REVIEW" ? "审批中" : "草稿"}
                                                        </Badge>
                                                    </div>
                                                    <div className="mt-1 text-xs text-muted-foreground">
                                                        发布：{formatDate(version.releasedAt ?? version.createdDate)} · {version.createdBy ?? "-"}
                                                    </div>
                                                    {version.changeSummary ? (
                                                        <p className="mt-2 text-sm leading-relaxed">{version.changeSummary}</p>
                                                    ) : null}
                                                </div>
                                            ))}
                                        </div>
                                    ) : (
                                        <div className="py-6 text-center text-sm text-muted-foreground">暂无版本记录</div>
                                    )}
                                </ScrollArea>
                            </div>

                            <div className="space-y-3">
                                <div className="flex items-center justify-between">
                                    <h3 className="text-sm font-semibold text-muted-foreground">附件（加密存储）</h3>
                                    <Input
                                        type="file"
                                        accept=".docx,.wps,.pdf,.xlsx,.xls,.md,.txt"
                                        onChange={handleUpload}
                                        disabled={uploading}
                                        className="max-w-xs cursor-pointer"
                                    />
                                </div>
                                <Separator />
                                <ScrollArea className="h-48 rounded-md border">
                                    {attachments.length ? (
                                        <table className="w-full table-fixed text-sm">
                                            <tbody>
                                                {attachments.map((item) => (
                                                    <tr key={item.id} className="border-b last:border-none">
                                                        <td className="px-3 py-3">
                                                            <div className="font-medium">{item.fileName}</div>
                                                            <div className="text-xs text-muted-foreground">
                                                                {humanFileSize(item.fileSize)} · 上传 {formatDate(item.createdDate)}
                                                            </div>
                                                        </td>
                                                        <td className="px-3 py-3 text-right">
                                                            <div className="flex justify-end gap-2">
                                                                <Button size="sm" variant="ghost" onClick={() => handleDownload(item)}>
                                                                    下载
                                                                </Button>
                                                                <Button
                                                                    size="sm"
                                                                    variant="outline"
                                                                    onClick={() => handleAttachmentDelete(item.id)}
                                                                >
                                                                    删除
                                                                </Button>
                                                            </div>
                                                        </td>
                                                    </tr>
                                                ))}
                                            </tbody>
                                        </table>
                                    ) : (
                                        <div className="py-6 text-center text-sm text-muted-foreground">暂无附件</div>
                                    )}
                                </ScrollArea>
                            </div>
                        </div>
                    </CardContent>
                </Card>
            ) : null}

            <Dialog open={formOpen} onOpenChange={setFormOpen}>
                <DialogContent className="max-w-2xl">
                    <DialogHeader>
                        <DialogTitle>{formMode === "create" ? "新建数据标准" : "编辑数据标准"}</DialogTitle>
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
                                    <Label className="text-sm">所属域</Label>
                                    <Input
                                        list="domain-options"
                                        value={formState.domain}
                                        onChange={(event) => setFormState((prev) => ({ ...prev, domain: event.target.value }))}
                                        placeholder="主数据域"
                                    />
                                    <datalist id="domain-options">
                                        {DOMAIN_OPTIONS.map((item) => (
                                            <option key={item} value={item} />
                                        ))}
                                    </datalist>
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
                                    <Label className="text-sm">数据密级（DATA_*）</Label>
                                    <Select
                                        value={formState.securityLevel}
                                        onValueChange={(value: DataLevel) =>
                                            setFormState((prev) => ({ ...prev, securityLevel: value }))
                                        }
                                    >
                                        <SelectTrigger>
                                            <SelectValue />
                                        </SelectTrigger>
                                        <SelectContent>
                                            {DATA_LEVELS.map((item) => (
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
                        <Button variant="outline" onClick={() => setFormOpen(false)}>
                            取消
                        </Button>
                        <Button onClick={submitForm} disabled={saving}>
                            {saving ? "保存中..." : "保存"}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
};

export default DataStandardsPage;
