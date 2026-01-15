import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import { GLOBAL_CONFIG } from "@/global-config";
import userStore from "@/store/userStore";
import {
  archiveStandard,
  createStandard,
  deleteStandard,
  importStandards,
  listStandards,
  updateStandard,
} from "@/api/platformApi";
import { Icon } from "@/components/icon";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { ScrollArea } from "@/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Textarea } from "@/ui/textarea";
import { useCatalogDomainOptions } from "@/hooks/useCatalogDomainOptions";
import {
  type DataStandardDto,
  type DataStandardStatus,
  STATUS_OPTIONS,
  formatDate,
  statusLabel,
  toTagList,
} from "@/pages/modeling/data-standards-utils";
import { useUserInfo } from "@/store/userStore";

type DomainFilter = "ALL" | string;

type FilterState = {
  keyword: string;
  domain: DomainFilter;
  status: "ALL" | DataStandardStatus;
};

type FormState = {
  code: string;
  name: string;
  domain: string;
  scope: string;
  owner: string;
  dataType: string;
  nullable: "UNSPECIFIED" | "true" | "false";
  codeSet: string;
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
  domain: "",
  scope: "",
  owner: "",
  dataType: "",
  nullable: "UNSPECIFIED",
  codeSet: "",
  tagsText: "",
  status: "DRAFT",
  version: "v1",
  versionNotes: "",
  changeSummary: "",
  description: "",
};

const buildUpdatePayload = (standard: DataStandardDto, patch: Record<string, unknown>) => ({
  code: standard.code,
  name: standard.name,
  domain: standard.domain || undefined,
  scope: standard.scope || undefined,
  owner: standard.owner || undefined,
  dataType: standard.dataType ?? null,
  nullable: typeof standard.nullable === "boolean" ? standard.nullable : null,
  codeSet: standard.codeSet ?? null,
  tags: Array.isArray(standard.tags) ? standard.tags : [],
  status: standard.status,
  version: standard.currentVersion ?? "v1",
  versionNotes: standard.versionNotes || undefined,
  changeSummary: undefined,
  description: standard.description || undefined,
  ...patch,
});

const DOMAIN_SELECT_UNSET = "__UNSET__";
const DOMAIN_SELECT_CUSTOM = "__CUSTOM__";

export default function DataStandardsPage() {
  const navigate = useNavigate();
  const userInfo = useUserInfo() as any;
  const roleSet = useMemo(() => {
    const raw = Array.isArray(userInfo?.roles) ? userInfo.roles : [];
    return new Set(raw.map((r: any) => String(r ?? "").toUpperCase()).filter(Boolean));
  }, [userInfo]);
  const canMaintain = useMemo(() => {
    const candidates = [
      "ROLE_OP_ADMIN",
      "OP_ADMIN",
      "ROLE_ADMIN",
      "ADMIN",
      "ROLE_INST_DATA_OWNER",
      "INST_DATA_OWNER",
      "ROLE_INST_DATA_DEV",
      "INST_DATA_DEV",
      "ROLE_DEPT_DATA_OWNER",
      "DEPT_DATA_OWNER",
      "ROLE_DEPT_DATA_DEV",
      "DEPT_DATA_DEV",
      "ROLE_INST_LEADER",
      "INST_LEADER",
      "ROLE_DEPT_LEADER",
      "DEPT_LEADER",
    ];
    return candidates.some((r) => roleSet.has(r));
  }, [roleSet]);

  const [loading, setLoading] = useState(false);
  const [standards, setStandards] = useState<DataStandardDto[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState<FilterState>({ keyword: "", domain: "ALL", status: "ALL" });

  const [createOpen, setCreateOpen] = useState(false);
  const [formState, setFormState] = useState<FormState>(DEFAULT_FORM);
  const [creating, setCreating] = useState(false);

  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [importing, setImporting] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [importResult, setImportResult] = useState<any | null>(null);

  const {
    options: domainTreeOptions,
    keyByName: domainKeyByName,
    nameByKey: domainNameByKey,
    labelByKey: domainLabelByKey,
  } = useCatalogDomainOptions();

  const domainOptions = useMemo(() => {
    const list = [...domainTreeOptions];
    list.sort((a, b) => a.label.localeCompare(b.label, "zh-Hans-CN"));
    return list;
  }, [domainTreeOptions]);

  const renderDomainLabel = useCallback(
    (domain?: string | null) => {
      const raw = String(domain ?? "").trim();
      if (!raw) return "-";
      const key = domainKeyByName[raw];
      return key ? domainLabelByKey[key] ?? raw : raw;
    },
    [domainKeyByName, domainLabelByKey],
  );

  const loadStandards = useCallback(async () => {
    setLoading(true);
    try {
      const params: Record<string, any> = { page, size: PAGE_SIZE };
      if (filters.keyword.trim()) params.keyword = filters.keyword.trim();
      if (filters.domain !== "ALL") params.domain = filters.domain;
      if (filters.status !== "ALL") params.status = filters.status;
      const data: any = await listStandards(params);
      const content = Array.isArray(data?.content) ? data.content : [];
      setStandards(content as DataStandardDto[]);
      setTotal(Number(data?.total ?? 0));
    } catch (e: any) {
      console.error(e);
      toast.error(e?.message ?? "加载数据标准失败");
    } finally {
      setLoading(false);
    }
  }, [filters, page]);

  useEffect(() => {
    void loadStandards();
  }, [loadStandards]);

  const downloadTemplate = useCallback(async () => {
    try {
      const { userToken } = userStore.getState() as any;
      const raw = String(userToken?.accessToken || "").trim();
      const token = raw.startsWith("Bearer ") ? raw.slice(7).trim() : raw;
      const resp = await fetch(`${GLOBAL_CONFIG.apiBaseUrl}/modeling/standards/import-template`, {
        method: "GET",
        headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      });
      if (!resp.ok) {
        throw new Error(`下载失败（${resp.status}）`);
      }
      const blob = await resp.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "数据标准导入模板.csv";
      a.click();
      URL.revokeObjectURL(url);
      toast.success("已下载导入模板");
    } catch (e: any) {
      console.error(e);
      toast.error(e?.message ?? "下载模板失败");
    }
  }, []);

  const handleImportFile = useCallback(
    async (event: any) => {
      const file: File | undefined = event?.target?.files?.[0];
      if (!file) return;
      event.target.value = "";
      setImporting(true);
      try {
        const formData = new FormData();
        formData.append("file", file);
        const res: any = await importStandards(formData);
        setImportResult(res);
        setImportOpen(true);
        toast.success("导入完成");
        await loadStandards();
      } catch (e: any) {
        console.error(e);
        toast.error(e?.message ?? "导入失败");
      } finally {
        setImporting(false);
      }
    },
    [loadStandards],
  );

  const domainFilterSelectValue = useMemo(() => {
    if (filters.domain === "ALL") return "ALL";
    const key = domainKeyByName[String(filters.domain ?? "").trim()];
    return key ?? DOMAIN_SELECT_CUSTOM;
  }, [domainKeyByName, filters.domain]);

  const domainFormSelectValue = useMemo(() => {
    const raw = String(formState.domain ?? "").trim();
    if (!raw) return DOMAIN_SELECT_UNSET;
    const key = domainKeyByName[raw];
    return key ?? DOMAIN_SELECT_CUSTOM;
  }, [domainKeyByName, formState.domain]);

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
    const nullable =
      formState.nullable === "true" ? true : formState.nullable === "false" ? false : undefined;
    const payload: any = {
      code: formState.code.trim(),
      name: formState.name.trim(),
      domain: formState.domain.trim() || undefined,
      scope: formState.scope || undefined,
      owner: formState.owner || undefined,
      dataType: formState.dataType.trim() || undefined,
      nullable,
      codeSet: formState.codeSet.trim() || undefined,
      tags: toTagList(formState.tagsText),
      status: formState.status,
      version: formState.version.trim() || "v1",
      versionNotes: formState.versionNotes || undefined,
      changeSummary: formState.changeSummary || undefined,
      description: formState.description || undefined,
      versionStatus: "DRAFT",
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
    } catch (e: any) {
      console.error(e);
      toast.error(e?.message ?? "保存失败");
    } finally {
      setCreating(false);
    }
  };

  const handlePublish = async (standard: DataStandardDto) => {
    if (!window.confirm("确认发布该数据标准？")) return;
    try {
      await updateStandard(
        standard.id,
        buildUpdatePayload(standard, {
          status: "ACTIVE" as DataStandardStatus,
          versionStatus: "PUBLISHED" as const,
        }),
      );
      toast.success("已发布");
      await loadStandards();
    } catch (e: any) {
      console.error(e);
      toast.error(e?.message ?? "发布失败");
    }
  };

  const handleArchive = async (standard: DataStandardDto) => {
    if (!window.confirm("确认归档该数据标准？")) return;
    try {
      await archiveStandard(standard.id);
      toast.success("已归档");
      await loadStandards();
    } catch (e: any) {
      console.error(e);
      toast.error(e?.message ?? "归档失败");
    }
  };

  const handleDelete = async (id: string) => {
    if (!window.confirm("确认删除该数据标准？")) return;
    try {
      await deleteStandard(id);
      toast.success("已删除");
      await loadStandards();
    } catch (e: any) {
      console.error(e);
      toast.error(e?.message ?? "删除失败");
    }
  };

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

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
            <p className="text-sm text-muted-foreground">主题域分组 + CSV 导入（Excel 可另存为 CSV）</p>
          </div>
          <div className="flex gap-2">
            {canMaintain && (
              <>
                <Button variant="outline" onClick={() => void downloadTemplate()} disabled={importing}>
                  下载模板
                </Button>
                <Button variant="outline" onClick={() => fileInputRef.current?.click()} disabled={importing}>
                  {importing ? "导入中…" : "导入"}
                </Button>
                <input ref={fileInputRef} type="file" accept=".csv,text/csv" className="hidden" onChange={handleImportFile} />
                <Button onClick={openCreate} disabled={importing}>
                  新建标准
                </Button>
              </>
            )}
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-3 md:grid-cols-3">
            <Input
              placeholder="搜索名称 / 编码 / 负责人"
              value={filters.keyword}
              onChange={(event) => {
                setFilters((prev) => ({ ...prev, keyword: event.target.value }));
                setPage(0);
              }}
            />
            <Select
              value={domainFilterSelectValue}
              onValueChange={(value) => {
                if (value === "ALL") {
                  setFilters((prev) => ({ ...prev, domain: "ALL" }));
                  setPage(0);
                  return;
                }
                if (value === DOMAIN_SELECT_CUSTOM) {
                  return;
                }
                const domainName = domainNameByKey[value];
                setFilters((prev) => ({ ...prev, domain: (domainName || "ALL") as DomainFilter }));
                setPage(0);
              }}
            >
              <SelectTrigger>
                <SelectValue placeholder="主题域" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ALL">全部主题域</SelectItem>
                {domainFilterSelectValue === DOMAIN_SELECT_CUSTOM && filters.domain !== "ALL" && (
                  <SelectItem value={DOMAIN_SELECT_CUSTOM}>当前值：{filters.domain}（已不存在）</SelectItem>
                )}
                {domainOptions.map((opt) => (
                  <SelectItem key={opt.key} value={opt.key}>
                    {opt.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select
              value={filters.status}
              onValueChange={(value) => {
                setFilters((prev) => ({ ...prev, status: value as any }));
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
            <table className="w-full min-w-[920px] border-collapse text-sm">
              <thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
                <tr>
                  <th className="w-40 px-3 py-3">编码</th>
                  <th className="min-w-[200px] px-3 py-3">名称</th>
                  <th className="w-40 px-3 py-3">数据类型</th>
                  <th className="w-24 px-3 py-3">可空</th>
                  <th className="w-40 px-3 py-3">主题域</th>
                  <th className="w-40 px-3 py-3">负责人</th>
                  <th className="w-28 px-3 py-3">状态</th>
                  <th className="w-24 px-3 py-3">版本</th>
                  <th className="w-52 px-3 py-3">更新时间</th>
                  <th className="w-56 px-3 py-3 text-right">操作</th>
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  <tr>
                    <td colSpan={10} className="px-3 py-6 text-center text-muted-foreground">
                      加载中…
                    </td>
                  </tr>
                ) : standards.length ? (
                  standards.map((standard) => (
                    <tr key={standard.id} className="border-b border-border/40 last:border-b-0">
                      <td className="px-3 py-3 font-mono text-xs">{standard.code}</td>
                      <td className="px-3 py-3">
                        <button className="text-left font-medium hover:underline" onClick={() => navigate(`/modeling/standards/${standard.id}`)}>
                          {standard.name}
                        </button>
                      </td>
                      <td className="px-3 py-3 text-xs text-muted-foreground">{standard.dataType || "-"}</td>
                      <td className="px-3 py-3 text-xs text-muted-foreground">
                        {standard.nullable === true ? "是" : standard.nullable === false ? "否" : "-"}
                      </td>
                      <td className="px-3 py-3">{renderDomainLabel(standard.domain)}</td>
                      <td className="px-3 py-3">{standard.owner || "-"}</td>
                      <td className="px-3 py-3">{statusLabel(standard.status)}</td>
                      <td className="px-3 py-3">{standard.currentVersion || "-"}</td>
                      <td className="px-3 py-3 text-xs text-muted-foreground">{formatDate(standard.lastModifiedDate || standard.createdDate)}</td>
                      <td className="px-3 py-3">
                        <div className="flex justify-end gap-2">
                          <Button size="sm" variant="outline" onClick={() => navigate(`/modeling/standards/${standard.id}`)}>
                            查看
                          </Button>
                          {canMaintain ? (
                            <>
                              <Button size="sm" variant="outline" disabled={standard.status === "ACTIVE"} onClick={() => void handlePublish(standard)}>
                                发布
                              </Button>
                              <Button size="sm" variant="outline" disabled={standard.status === "ARCHIVED"} onClick={() => void handleArchive(standard)}>
                                归档
                              </Button>
                              <Button size="sm" variant="destructive" onClick={() => void handleDelete(standard.id)}>
                                删除
                              </Button>
                            </>
                          ) : null}
                        </div>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={10} className="px-3 py-6 text-center text-muted-foreground">
                      暂无数据
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="flex items-center justify-between text-sm">
            <div className="text-muted-foreground">
              共 {total} 条，第 {page + 1} / {totalPages} 页
            </div>
            <div className="flex gap-2">
              <Button variant="outline" disabled={page <= 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
                上一页
              </Button>
              <Button
                variant="outline"
                disabled={page + 1 >= totalPages}
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              >
                下一页
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>新建数据标准</DialogTitle>
          </DialogHeader>
          <div className="grid gap-4 md:grid-cols-2">
            <div className="grid gap-2">
              <Label>标准名称</Label>
              <Input value={formState.name} onChange={(e) => setFormState((p) => ({ ...p, name: e.target.value }))} />
            </div>
            <div className="grid gap-2">
              <Label>标准编码</Label>
              <Input value={formState.code} onChange={(e) => setFormState((p) => ({ ...p, code: e.target.value }))} />
            </div>
            <div className="grid gap-2">
              <Label>数据类型</Label>
              <Input value={formState.dataType} onChange={(e) => setFormState((p) => ({ ...p, dataType: e.target.value }))} placeholder="如：string / bigint / date" />
            </div>
            <div className="grid gap-2">
              <Label>可空</Label>
              <Select value={formState.nullable} onValueChange={(v: any) => setFormState((p) => ({ ...p, nullable: v }))}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="UNSPECIFIED">（未指定）</SelectItem>
                  <SelectItem value="true">是</SelectItem>
                  <SelectItem value="false">否</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-2">
              <Label>主题域</Label>
              <Select
                value={domainFormSelectValue}
                onValueChange={(value) => {
                  if (value === DOMAIN_SELECT_UNSET) {
                    setFormState((p) => ({ ...p, domain: "" }));
                    return;
                  }
                  if (value === DOMAIN_SELECT_CUSTOM) {
                    return;
                  }
                  const domainName = domainNameByKey[value];
                  setFormState((p) => ({ ...p, domain: domainName || "" }));
                }}
              >
                <SelectTrigger>
                  <SelectValue placeholder="选择主题域" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={DOMAIN_SELECT_UNSET}>（未选择）</SelectItem>
                  {domainFormSelectValue === DOMAIN_SELECT_CUSTOM && !!formState.domain.trim() && (
                    <SelectItem value={DOMAIN_SELECT_CUSTOM}>当前值：{formState.domain}（已不存在）</SelectItem>
                  )}
                  {domainOptions.map((opt) => (
                    <SelectItem key={opt.key} value={opt.key}>
                      {opt.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-2">
              <Label>负责人</Label>
              <Input value={formState.owner} onChange={(e) => setFormState((p) => ({ ...p, owner: e.target.value }))} />
            </div>
            <div className="grid gap-2 md:col-span-2">
              <Label>码表/取值范围</Label>
              <Input value={formState.codeSet} onChange={(e) => setFormState((p) => ({ ...p, codeSet: e.target.value }))} placeholder="如：YES/NO，或引用码表编码" />
            </div>
            <div className="grid gap-2">
              <Label>状态</Label>
              <Select value={formState.status} onValueChange={(v: DataStandardStatus) => setFormState((p) => ({ ...p, status: v }))}>
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
            <div className="grid gap-2">
              <Label>适用范围</Label>
              <Input value={formState.scope} onChange={(e) => setFormState((p) => ({ ...p, scope: e.target.value }))} />
            </div>
            <div className="grid gap-2 md:col-span-2">
              <Label>标签（逗号分隔）</Label>
              <Input value={formState.tagsText} onChange={(e) => setFormState((p) => ({ ...p, tagsText: e.target.value }))} />
            </div>
            <div className="grid gap-2">
              <Label>版本号</Label>
              <Input value={formState.version} onChange={(e) => setFormState((p) => ({ ...p, version: e.target.value }))} />
            </div>
            <div className="grid gap-2">
              <Label>版本说明</Label>
              <Input value={formState.versionNotes} onChange={(e) => setFormState((p) => ({ ...p, versionNotes: e.target.value }))} />
            </div>
            <div className="grid gap-2 md:col-span-2">
              <Label>变更摘要</Label>
              <Textarea value={formState.changeSummary} onChange={(e) => setFormState((p) => ({ ...p, changeSummary: e.target.value }))} rows={3} />
            </div>
            <div className="grid gap-2 md:col-span-2">
              <Label>标准描述</Label>
              <Textarea value={formState.description} onChange={(e) => setFormState((p) => ({ ...p, description: e.target.value }))} rows={4} />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateOpen(false)} disabled={creating}>
              取消
            </Button>
            <Button onClick={() => void submitCreateForm()} disabled={creating}>
              {creating ? "保存中…" : "保存"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={importOpen} onOpenChange={setImportOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>导入结果</DialogTitle>
          </DialogHeader>
          <div className="grid gap-2 text-sm">
            <div>总行数：{importResult?.totalRows ?? 0}</div>
            <div>新增：{importResult?.created ?? 0}，更新：{importResult?.updated ?? 0}，跳过：{importResult?.skipped ?? 0}</div>
            {Array.isArray(importResult?.errors) && importResult.errors.length ? (
              <div className="space-y-2">
                <div className="text-muted-foreground">错误：</div>
                <ScrollArea className="h-48 rounded border p-2">
                  <ul className="list-disc space-y-1 pl-4">
                    {importResult.errors.map((err: string, idx: number) => (
                      <li key={idx} className="text-xs text-red-600 dark:text-red-300">
                        {err}
                      </li>
                    ))}
                  </ul>
                </ScrollArea>
              </div>
            ) : (
              <div className="text-muted-foreground">无错误</div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setImportOpen(false)}>
              关闭
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
