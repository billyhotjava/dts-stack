import { useCallback, useEffect, useRef, useState, type ChangeEvent } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router";
import { toast } from "sonner";
import {
    getStandard,
    listStandardAttachments,
    listStandardVersions,
    uploadStandardAttachment,
    deleteStandardAttachment,
    getStandardHealth,
    deleteStandard,
    updateStandard,
} from "@/api/platformApi";
import { GLOBAL_CONFIG } from "@/global-config";
import userStore from "@/store/userStore";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { ScrollArea } from "@/ui/scroll-area";
import { Separator } from "@/ui/separator";
import { Label } from "@/ui/label";
import { Textarea } from "@/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import {
    DataStandardAttachmentDto,
    DataStandardDto,
    DataStandardVersionDto,
    DataStandardStatus,
    STATUS_OPTIONS,
    formatDate,
    humanFileSize,
    statusLabel,
    toTagList,
    fromTagList,
} from "@/pages/modeling/data-standards-utils";

const attachmentExtensions = ["docx", "wps", "pdf", "xlsx", "xls", "md", "txt"];

type EditFormState = {
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

const buildEditFormState = (detail: DataStandardDto): EditFormState => ({
    code: detail.code ?? "",
    name: detail.name ?? "",
    scope: detail.scope ?? "",
    owner: detail.owner ?? "",
    tagsText: fromTagList(detail.tags),
    status: detail.status,
    version: detail.currentVersion ?? "v1",
    versionNotes: detail.versionNotes ?? "",
    changeSummary: "",
    description: detail.description ?? "",
});

const DataStandardDetailPage = () => {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();

    const [loading, setLoading] = useState(true);
    const [detail, setDetail] = useState<DataStandardDto | null>(null);
    const [versions, setVersions] = useState<DataStandardVersionDto[]>([]);
    const [attachments, setAttachments] = useState<DataStandardAttachmentDto[]>([]);
    const [health, setHealth] = useState<any | null>(null);
    const [uploading, setUploading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [isEditing, setIsEditing] = useState(searchParams.get("edit") === "true");
    const [activeModule, setActiveModule] = useState(searchParams.get("module") ?? "basic");
    const [editForm, setEditForm] = useState<EditFormState | null>(null);

    const basicInfoRef = useRef<HTMLDivElement | null>(null);
    const versionsRef = useRef<HTMLDivElement | null>(null);
    const attachmentsRef = useRef<HTMLDivElement | null>(null);

    const loadDetail = useCallback(async () => {
        if (!id) return;
        setLoading(true);
        try {
            const [detailRes, versionList, attachmentList] = await Promise.all([
                getStandard(id),
                listStandardVersions(id),
                listStandardAttachments(id),
            ]);
            setDetail(detailRes as DataStandardDto);
            setVersions(Array.isArray(versionList) ? (versionList as DataStandardVersionDto[]) : []);
            setAttachments(Array.isArray(attachmentList) ? (attachmentList as DataStandardAttachmentDto[]) : []);
        } catch (error: any) {
            console.error(error);
            toast.error(error?.message ?? "加载数据标准详情失败");
            navigate("/modeling/standards", { replace: true });
        } finally {
            setLoading(false);
        }
    }, [id, navigate]);

    useEffect(() => {
        if (!id) {
            navigate("/modeling/standards", { replace: true });
            return;
        }
        void loadDetail();
    }, [id, loadDetail, navigate]);

    useEffect(() => {
        (async () => {
            try {
                const healthRes = await getStandardHealth();
                setHealth(healthRes);
            } catch {
                setHealth(null);
            }
        })();
    }, []);

    useEffect(() => {
        setIsEditing(searchParams.get("edit") === "true");
    }, [searchParams]);

    useEffect(() => {
        setActiveModule(searchParams.get("module") ?? "basic");
    }, [searchParams]);

    useEffect(() => {
        if (detail) {
            setEditForm(buildEditFormState(detail));
        } else {
            setEditForm(null);
        }
    }, [detail]);

    useEffect(() => {
        if (!isEditing) {
            return;
        }
        if (detail) {
            setEditForm((prev) => prev ?? buildEditFormState(detail));
        }
    }, [isEditing, detail]);

    useEffect(() => {
        const target =
            activeModule === "versions"
                ? versionsRef.current
                : activeModule === "attachments"
                ? attachmentsRef.current
                : basicInfoRef.current;
        if (target) {
            target.scrollIntoView({ behavior: "smooth", block: "start" });
        }
    }, [activeModule]);

    const updateSearchParams = (module: string, editing: boolean) => {
        const next = new URLSearchParams(searchParams);
        next.set("module", module);
        if (editing) {
            next.set("edit", "true");
        } else {
            next.delete("edit");
        }
        setSearchParams(next, { replace: true });
    };

    const startEdit = (module: string) => {
        if (!detail) return;
        setActiveModule(module);
        setIsEditing(true);
        setEditForm(buildEditFormState(detail));
        updateSearchParams(module, true);
    };

    const handleEditCancel = () => {
        if (detail) {
            setEditForm(buildEditFormState(detail));
        }
        setIsEditing(false);
        setSaving(false);
        updateSearchParams(activeModule || "basic", false);
    };

    const handleSave = async () => {
        if (!detail || !editForm) return;
        if (!editForm.code.trim() || !editForm.name.trim()) {
            toast.error("请填写名称和编码");
            return;
        }
        setSaving(true);
        const payload = {
            code: editForm.code.trim(),
            name: editForm.name.trim(),
            scope: editForm.scope || undefined,
            owner: editForm.owner || undefined,
            tags: toTagList(editForm.tagsText),
            status: editForm.status,
            version: editForm.version.trim() || "v1",
            versionNotes: editForm.versionNotes || undefined,
            changeSummary: editForm.changeSummary || undefined,
            description: editForm.description || undefined,
        };
        try {
            await updateStandard(detail.id, payload);
            toast.success("已更新数据标准");
            await loadDetail();
            setIsEditing(false);
            updateSearchParams(activeModule || "basic", false);
        } catch (error: any) {
            console.error(error);
            toast.error(error?.message ?? "保存失败");
        } finally {
            setSaving(false);
        }
    };

    const handleUpload = async (event: ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        if (!file || !id) {
            return;
        }
        const name = file.name || "";
        const ext = name.includes(".") ? name.split(".").pop()!.toLowerCase() : "";
        if (ext && !attachmentExtensions.includes(ext)) {
            toast.error(`不支持的文件类型: ${ext}，请上传 ${attachmentExtensions.join(", ")} 文件`);
            try {
                (event.target as HTMLInputElement).value = "";
            } catch {
                // ignore
            }
            return;
        }
        const formData = new FormData();
        formData.append("file", file);
        if (detail?.currentVersion) {
            formData.append("version", detail.currentVersion);
        }
        setUploading(true);
        try {
            await uploadStandardAttachment(id, formData);
            toast.success("附件上传成功（已加密存储）");
            await loadDetail();
        } catch (error: any) {
            console.error(error);
            toast.error(error?.message ?? "附件上传失败");
        } finally {
            setUploading(false);
            event.target.value = "";
        }
    };

    const handleAttachmentDelete = async (attachmentId: string) => {
        if (!id) return;
        if (!window.confirm("确认删除该附件？")) return;
        try {
            await deleteStandardAttachment(id, attachmentId);
            toast.success("附件已删除");
            await loadDetail();
        } catch (error: any) {
            console.error(error);
            toast.error(error?.message ?? "删除附件失败");
        }
    };

    const handleDownload = async (attachment: DataStandardAttachmentDto) => {
        if (!id) return;
        try {
            const url = `${GLOBAL_CONFIG.apiBaseUrl}/modeling/standards/${id}/attachments/${attachment.id}/download`;
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

    const handleDelete = async () => {
        if (!id) return;
        if (!window.confirm("确认删除该数据标准？")) return;
        try {
            await deleteStandard(id);
            toast.success("已删除数据标准");
            navigate("/modeling/standards");
        } catch (error: any) {
            console.error(error);
            toast.error(error?.message ?? "删除失败");
        }
    };

    if (loading) {
        return (
            <div className="space-y-6">
                <div className="flex items-center justify-between">
                    <Button variant="outline" onClick={() => navigate("/modeling/standards")}>
                        返回列表
                    </Button>
                </div>
                <Card>
                    <CardHeader>
                        <CardTitle>数据标准详情</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <div className="py-10 text-center text-sm text-muted-foreground">正在加载详情...</div>
                    </CardContent>
                </Card>
            </div>
        );
    }

    if (!detail) {
        return (
            <div className="space-y-6">
                <div className="flex items-center justify-between">
                    <Button variant="outline" onClick={() => navigate("/modeling/standards")}>
                        返回列表
                    </Button>
                </div>
                <Card>
                    <CardHeader>
                        <CardTitle>数据标准详情</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <div className="py-10 text-center text-sm text-muted-foreground">未找到对应的数据标准</div>
                    </CardContent>
                </Card>
            </div>
        );
    }

    const hasAttachments = attachments.length > 0;
    const effectiveForm = editForm ?? (detail ? buildEditFormState(detail) : null);

    return (
        <div className="space-y-6">
            <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                <div className="space-y-1">
                    <Button variant="outline" onClick={() => navigate("/modeling/standards")}>
                        返回列表
                    </Button>
                    <div>
                        <h1 className="text-2xl font-semibold">{detail.name}</h1>
                        <p className="text-sm text-muted-foreground">{detail.code}</p>
                    </div>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                    <Badge variant={detail.status === "ACTIVE" ? "default" : "secondary"}>{statusLabel(detail.status)}</Badge>
                    {isEditing ? (
                        <>
                            <Button variant="outline" onClick={handleEditCancel} disabled={saving}>
                                取消
                            </Button>
                            <Button onClick={handleSave} disabled={saving}>
                                {saving ? "保存中..." : "保存"}
                            </Button>
                        </>
                    ) : (
                        <Button variant="outline" onClick={() => startEdit(activeModule || "basic")}>
                            编辑
                        </Button>
                    )}
                    <Button variant="destructive" onClick={handleDelete} disabled={saving}>
                        删除
                    </Button>
                </div>
            </div>

            <div ref={basicInfoRef}>
                <Card>
                    <CardHeader>
                        <CardTitle>基础信息</CardTitle>
                    </CardHeader>
                    <CardContent>
                        {isEditing && effectiveForm ? (
                            <div className="space-y-4">
                                <div className="grid gap-4 md:grid-cols-2">
                                    <div>
                                        <Label className="text-sm">名称</Label>
                                        <Input
                                            value={effectiveForm.name}
                                            onChange={(event) =>
                                                setEditForm((prev) =>
                                                    prev ? { ...prev, name: event.target.value } : prev
                                                )
                                            }
                                            placeholder="请输入标准名称"
                                        />
                                    </div>
                                    <div>
                                        <Label className="text-sm">编码</Label>
                                        <Input
                                            value={effectiveForm.code}
                                            onChange={(event) =>
                                                setEditForm((prev) =>
                                                    prev ? { ...prev, code: event.target.value } : prev
                                                )
                                            }
                                            placeholder="STD-XXX"
                                        />
                                    </div>
                                    <div>
                                        <Label className="text-sm">负责人</Label>
                                        <Input
                                            value={effectiveForm.owner}
                                            onChange={(event) =>
                                                setEditForm((prev) =>
                                                    prev ? { ...prev, owner: event.target.value } : prev
                                                )
                                            }
                                            placeholder="请输入负责人"
                                        />
                                    </div>
                                    <div>
                                        <Label className="text-sm">状态</Label>
                                        <Select
                                            value={effectiveForm.status}
                                            onValueChange={(value: DataStandardStatus) =>
                                                setEditForm((prev) =>
                                                    prev ? { ...prev, status: value } : prev
                                                )
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
                                            value={effectiveForm.scope}
                                            onChange={(event) =>
                                                setEditForm((prev) =>
                                                    prev ? { ...prev, scope: event.target.value } : prev
                                                )
                                            }
                                            placeholder="业务范围说明"
                                        />
                                    </div>
                                    <div>
                                        <Label className="text-sm">标签</Label>
                                        <Input
                                            value={effectiveForm.tagsText}
                                            onChange={(event) =>
                                                setEditForm((prev) =>
                                                    prev ? { ...prev, tagsText: event.target.value } : prev
                                                )
                                            }
                                            placeholder="逗号分隔，如：主数据,共享"
                                        />
                                    </div>
                                    <div>
                                        <Label className="text-sm">版本号</Label>
                                        <Input
                                            value={effectiveForm.version}
                                            onChange={(event) =>
                                                setEditForm((prev) =>
                                                    prev ? { ...prev, version: event.target.value } : prev
                                                )
                                            }
                                            placeholder="v1"
                                        />
                                    </div>
                                    <div>
                                        <Label className="text-sm">版本说明</Label>
                                        <Input
                                            value={effectiveForm.versionNotes}
                                            onChange={(event) =>
                                                setEditForm((prev) =>
                                                    prev ? { ...prev, versionNotes: event.target.value } : prev
                                                )
                                            }
                                            placeholder="版本说明"
                                        />
                                    </div>
                                </div>
                                <div>
                                    <Label className="text-sm">变更摘要（用于记录版本调整）</Label>
                                    <Textarea
                                        value={effectiveForm.changeSummary}
                                        onChange={(event) =>
                                            setEditForm((prev) =>
                                                prev ? { ...prev, changeSummary: event.target.value } : prev
                                            )
                                        }
                                        placeholder="简要描述本次版本的主要变更"
                                        rows={3}
                                    />
                                </div>
                                <div>
                                    <Label className="text-sm">标准描述</Label>
                                    <Textarea
                                        value={effectiveForm.description}
                                        onChange={(event) =>
                                            setEditForm((prev) =>
                                                prev ? { ...prev, description: event.target.value } : prev
                                            )
                                        }
                                        placeholder="补充标准定义、指标口径等信息"
                                        rows={5}
                                    />
                                </div>
                            </div>
                        ) : (
                            <>
                                <div className="grid gap-4 md:grid-cols-2">
                                    <div className="space-y-2">
                                        <div className="text-sm text-muted-foreground">所属域</div>
                                        <div>{detail.domain ?? "-"}</div>
                                    </div>
                                    <div className="space-y-2">
                                        <div className="text-sm text-muted-foreground">负责人</div>
                                        <div>{detail.owner ?? "-"}</div>
                                    </div>
                                    <div className="space-y-2">
                                        <div className="text-sm text-muted-foreground">当前版本</div>
                                        <div>{detail.currentVersion ?? "-"}</div>
                                    </div>
                                    <div className="space-y-2">
                                        <div className="text-sm text-muted-foreground">最新更新时间</div>
                                        <div>{formatDate(detail.lastModifiedDate ?? detail.createdDate)}</div>
                                    </div>
                                    <div className="space-y-2">
                                        <div className="text-sm text-muted-foreground">适用范围</div>
                                        <div>{detail.scope || "-"}</div>
                                    </div>
                                    <div className="space-y-2">
                                        <div className="text-sm text-muted-foreground">标签</div>
                                        <div>{detail.tags?.length ? detail.tags.join(", ") : "-"}</div>
                                    </div>
                                </div>
                                <Separator className="my-6" />
                                <div>
                                    <div className="text-sm font-semibold text-muted-foreground">描述与说明</div>
                                    <p className="mt-2 rounded-md border bg-muted/10 p-3 text-sm leading-relaxed">
                                        {detail.description || "暂无描述"}
                                    </p>
                                </div>
                            </>
                        )}
                    </CardContent>
                </Card>
            </div>

            <div className="grid gap-6 lg:grid-cols-2">
                <div ref={versionsRef}>
                    <Card>
                        <CardHeader>
                            <CardTitle>版本记录</CardTitle>
                        </CardHeader>
                        <CardContent>
                        <ScrollArea className="h-64 rounded-md border p-3">
                            {versions.length ? (
                                <div className="space-y-3">
                                    {versions.map((version) => (
                                        <div key={version.id} className="rounded-md border p-3">
                                            <div className="flex items-center justify-between text-sm font-medium">
                                                <span>{version.version}</span>
                                                {!(hasAttachments && version.status === "PUBLISHED") && (
                                                    <Badge variant={version.status === "PUBLISHED" ? "default" : "secondary"}>
                                                        {version.status === "PUBLISHED"
                                                            ? "已发布"
                                                            : version.status === "IN_REVIEW"
                                                            ? "审批中"
                                                            : version.status === "ARCHIVED"
                                                            ? "已归档"
                                                            : "草稿"}
                                                    </Badge>
                                                )}
                                            </div>
                                            <div className="mt-1 text-xs text-muted-foreground">
                                                {hasAttachments && version.status === "PUBLISHED"
                                                    ? (
                                                          <>
                                                              更新：{formatDate(version.createdDate)} · {version.createdBy ?? "-"}
                                                          </>
                                                      )
                                                    : (
                                                          <>
                                                              发布：{formatDate(version.releasedAt ?? version.createdDate)} ·{" "}
                                                              {version.createdBy ?? "-"}
                                                          </>
                                                      )}
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
                        </CardContent>
                    </Card>
                </div>

                <div ref={attachmentsRef}>
                    <Card>
                        <CardHeader>
                            <CardTitle>附件（加密存储）</CardTitle>
                        </CardHeader>
                        <CardContent className="space-y-3">
                        <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
                            <div className="text-xs text-muted-foreground">
                                仅支持：{attachmentExtensions.join(", ")}（最大 200MB）
                            </div>
                            <div className="flex flex-col gap-2 md:flex-row md:items-center md:gap-3">
                                {health && health.ok === false ? (
                                    <div className="rounded-md border border-dashed bg-amber-50 px-3 py-2 text-xs text-amber-800">
                                        附件上传配置存在问题：{health.message}
                                    </div>
                                ) : null}
                                <Input
                                    type="file"
                                    accept={attachmentExtensions.map((ext) => `.${ext}`).join(",")}
                                    onClick={(event) => {
                                        try {
                                            (event.target as HTMLInputElement).value = "";
                                        } catch {
                                            // ignore
                                        }
                                    }}
                                    onChange={handleUpload}
                                    disabled={uploading || (health && health.ok === false)}
                                    className="max-w-xs cursor-pointer"
                                />
                            </div>
                        </div>
                        <Separator />
                        <ScrollArea className="h-64 rounded-md border">
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
                        </CardContent>
                    </Card>
                </div>
            </div>
        </div>
    );
};

export default DataStandardDetailPage;
