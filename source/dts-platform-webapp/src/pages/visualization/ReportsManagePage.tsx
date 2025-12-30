import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Check, ChevronsUpDown } from "lucide-react";
import { Button } from "@/ui/button";
import { Badge } from "@/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "@/ui/popover";
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from "@/ui/command";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Switch } from "@/ui/switch";
import { Textarea } from "@/ui/textarea";
import directoryService, { type DirectoryRole } from "@/api/services/directoryService";
import deptService, { type DeptDto } from "@/api/services/deptService";
import reportsService, { type ReportLink, type ReportLinkUpsertRequest } from "@/api/services/reportsService";
import { classificationToLabelZh, normalizeClassification, type ClassificationLevel } from "@/utils/classification";
import { cn } from "@/utils";

type FormState = {
  code: string;
  title: string;
  url: string;
  engine: string;
  reportType: string;
  deptCodes: string[];
  roleCodes: string[];
  classification: ClassificationLevel;
  enabled: boolean;
  sortOrder: number;
};

const DEFAULT_FORM: FormState = {
  code: "",
  title: "",
  url: "",
  engine: "HETU",
  reportType: "",
  deptCodes: [],
  roleCodes: [],
  classification: "INTERNAL",
  enabled: true,
  sortOrder: 0,
};

function normalizeRoleCode(raw: string): string {
  const token = String(raw || "").trim();
  if (!token) return "";
  const cleaned = token.replace(/[\s-]+/g, "_");
  const upper = cleaned.toUpperCase();
  return upper.startsWith("ROLE_") ? upper : `ROLE_${upper}`;
}

function toUpsertPayload(form: FormState): ReportLinkUpsertRequest {
  const normalizedClassification = normalizeClassification(form.classification, "INTERNAL");
  return {
    code: form.code.trim(),
    title: form.title.trim(),
    url: form.url.trim(),
    engine: form.engine?.trim() || "HETU",
    reportType: form.reportType?.trim() || undefined,
    deptCodes: Array.isArray(form.deptCodes) ? form.deptCodes.filter(Boolean).slice(0, 1) : [],
    roleCodes: Array.isArray(form.roleCodes)
      ? form.roleCodes
          .map(normalizeRoleCode)
          .filter(Boolean)
          .slice(0, 200)
      : [],
    classification: normalizedClassification,
    enabled: Boolean(form.enabled),
    sortOrder: Number.isFinite(Number(form.sortOrder)) ? Number(form.sortOrder) : 0,
  };
}

function normalizeHetuUrl(rawUrl: string): string {
  const url = String(rawUrl || "").trim();
  if (!url) return "";
  if (url.startsWith("/")) return url;
  try {
    const parsed = new URL(url);
    const isHetuPort = parsed.port === "7778";
    const isHetuPath =
      parsed.pathname?.startsWith("/screen") ||
      parsed.pathname?.startsWith("/dashboards") ||
      parsed.pathname?.startsWith("/dashboard/hetu") ||
      parsed.pathname?.startsWith("/dashboard");
    if (!isHetuPort && !isHetuPath) return url;
    return `${parsed.pathname || ""}${parsed.search || ""}${parsed.hash || ""}` || url;
  } catch {
    return url;
  }
}

function normalizeMetabaseUrl(rawUrl: string): string {
  const url = String(rawUrl || "").trim();
  if (!url) return "";
  if (url.startsWith("/analytics")) return url;
  if (url.startsWith("/")) return `/analytics${url}`;
  try {
    const parsed = new URL(url);
    const path = `${parsed.pathname || ""}${parsed.search || ""}${parsed.hash || ""}`;
    if (!path) return url;
    return `/analytics${path.startsWith("/") ? "" : "/"}${path}`;
  } catch {
    return url;
  }
}

function engineLabel(engine: string | undefined | null): string {
  const upper = String(engine || "")
    .trim()
    .toUpperCase();
  if (upper === "METABASE") return "ANALYTICS";
  return upper || "-";
}

export default function ReportsManagePage() {
  const [items, setItems] = useState<ReportLink[]>([]);
  const [loading, setLoading] = useState(false);
  const [actioningId, setActioningId] = useState<string | null>(null);
  const [keyword, setKeyword] = useState("");
  const [deptCode, setDeptCode] = useState<string>("all");
  const [reportType, setReportType] = useState<string>("all");
  const [enabledOnly, setEnabledOnly] = useState(false);
  const [departments, setDepartments] = useState<DeptDto[]>([]);
  const [roles, setRoles] = useState<DirectoryRole[]>([]);

  const [open, setOpen] = useState(false);
  const [mode, setMode] = useState<"create" | "edit">("create");
  const [editing, setEditing] = useState<ReportLink | null>(null);
  const [form, setForm] = useState<FormState>({ ...DEFAULT_FORM });
  const [deptPickerOpen, setDeptPickerOpen] = useState(false);
  const [deptSearch, setDeptSearch] = useState("");
  const [rolePickerOpen, setRolePickerOpen] = useState(false);
  const [roleSearch, setRoleSearch] = useState("");

  const deptDict = useMemo(() => {
    const m = new Map<string, DeptDto>();
    for (const d of departments) m.set(String(d.code), d);
    return m;
  }, [departments]);

  const deptItems = useMemo(() => {
    const computeDepth = (dept: DeptDto) => {
      let depth = 0;
      let cur: DeptDto | undefined = dept;
      const seen = new Set<string>();
      while (cur && typeof cur.parentId === "number") {
        const pid = String(cur.parentId);
        if (seen.has(pid)) break;
        seen.add(pid);
        const parent = deptDict.get(pid);
        if (!parent) break;
        depth += 1;
        cur = parent;
        if (depth > 20) break;
      }
      return depth;
    };

    const computePath = (dept: DeptDto) => {
      const parts: string[] = [];
      let cur: DeptDto | undefined = dept;
      const seen = new Set<string>();
      while (cur) {
        const code = String(cur.code);
        if (seen.has(code)) break;
        seen.add(code);
        parts.unshift(cur.nameZh || cur.nameEn || code);
        if (typeof cur.parentId !== "number") break;
        cur = deptDict.get(String(cur.parentId));
      }
      return parts.join(" / ");
    };

    return departments.map((d) => ({
      code: String(d.code),
      label: d.nameZh || d.nameEn || String(d.code),
      path: computePath(d),
      depth: computeDepth(d),
      isRoot: Boolean(d.isRoot) || d.parentId == null,
    }));
  }, [departments, deptDict]);

  const visibleDeptItems = useMemo(() => {
    const q = deptSearch.trim().toLowerCase();
    if (!q) return deptItems;
    return deptItems
      .filter(
        (d) =>
          d.code.toLowerCase().includes(q) ||
          d.label.toLowerCase().includes(q) ||
          d.path.toLowerCase().includes(q),
      )
      .slice(0, 200);
  }, [deptItems, deptSearch]);

  const roleItems = useMemo(() => {
    const q = roleSearch.trim().toLowerCase();
    const base = Array.isArray(roles) ? roles : [];
    const list = q
      ? base.filter(
          (r) =>
            String(r.name || "")
              .toLowerCase()
              .includes(q) ||
            String(r.description || "")
              .toLowerCase()
              .includes(q),
        )
      : base;
    return list.slice(0, 300);
  }, [roles, roleSearch]);

  const reportTypes = useMemo(() => {
    const set = new Set<string>();
    for (const r of items) {
      const t = String(r.reportType || "").trim();
      if (t) set.add(t);
    }
    return Array.from(set);
  }, [items]);

  const fetchList = async () => {
    setLoading(true);
    try {
      const data = await reportsService.listAll({
        keyword: keyword.trim() || undefined,
        deptCode: deptCode === "all" ? undefined : deptCode,
        type: reportType === "all" ? undefined : reportType,
        enabledOnly,
      });
      setItems(Array.isArray(data) ? data : []);
    } catch (e: any) {
      console.error(e);
      toast.error(e?.message || "加载失败");
      setItems([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void fetchList();
  }, []);

  useEffect(() => {
    deptService
      .listDepartments()
      .then((list) => setDepartments(Array.isArray(list) ? list : []))
      .catch(() => setDepartments([]));
  }, []);

  useEffect(() => {
    directoryService
      .listRoles()
      .then((list) => setRoles(Array.isArray(list) ? list : []))
      .catch(() => setRoles([]));
  }, []);

  const onCreate = () => {
    setMode("create");
    setEditing(null);
    setForm({ ...DEFAULT_FORM });
    setDeptSearch("");
    setRoleSearch("");
    setDeptPickerOpen(false);
    setRolePickerOpen(false);
    setOpen(true);
  };

  const onEdit = (r: ReportLink) => {
    const normalizedClassification = normalizeClassification(r.classification, "INTERNAL");
    setMode("edit");
    setEditing(r);
    setForm({
      code: r.code || "",
      title: r.title || "",
      url: r.url || "",
      engine: r.engine || "HETU",
      reportType: (r.reportType as any) || "",
      deptCodes: Array.isArray(r.deptCodes) ? r.deptCodes.filter(Boolean).slice(0, 1) : [],
      roleCodes: Array.isArray(r.roleCodes) ? r.roleCodes.filter(Boolean).map(normalizeRoleCode) : [],
      classification: normalizedClassification,
      enabled: typeof r.enabled === "boolean" ? r.enabled : true,
      sortOrder: Number.isFinite(Number(r.sortOrder)) ? Number(r.sortOrder) : 0,
    });
    setDeptSearch("");
    setRoleSearch("");
    setDeptPickerOpen(false);
    setRolePickerOpen(false);
    setOpen(true);
  };

  const onDisable = async (r: ReportLink) => {
    try {
      setActioningId(r.id);
      await reportsService.disable(r.id);
      toast.success("已禁用");
      await fetchList();
    } catch (e: any) {
      console.error(e);
      toast.error(e?.message || "禁用失败");
    } finally {
      setActioningId((id) => (id === r.id ? null : id));
    }
  };

  const onEnable = async (r: ReportLink) => {
    try {
      setActioningId(r.id);
      await reportsService.update(r.id, {
        code: r.code,
        title: r.title,
        url: r.url,
        engine: r.engine,
        reportType: (r.reportType as any) || undefined,
        deptCodes: r.deptCodes || [],
        roleCodes: r.roleCodes || [],
        classification: r.classification,
        enabled: true,
        sortOrder: Number.isFinite(Number(r.sortOrder)) ? Number(r.sortOrder) : 0,
      });
      toast.success("已启用");
      await fetchList();
    } catch (e: any) {
      console.error(e);
      toast.error(e?.message || "启用失败");
    } finally {
      setActioningId((id) => (id === r.id ? null : id));
    }
  };

  const onPurge = async (r: ReportLink) => {
    const ok = window.confirm(`确定删除链接「${r.title || r.code}」？\n此操作不可恢复。`);
    if (!ok) return;
    try {
      setActioningId(r.id);
      await reportsService.purge(r.id);
      toast.success("已删除");
      setItems((prev) => prev.filter((x) => x.id !== r.id));
    } catch (e: any) {
      console.error(e);
      const status = e?.response?.status;
      if (status === 404) {
        try {
          await reportsService.disable(r.id);
          toast.success("已删除");
          setItems((prev) => prev.filter((x) => x.id !== r.id));
          return;
        } catch (e2: any) {
          console.error(e2);
          toast.error(e2?.message || "删除失败");
          return;
        }
      }
      toast.error(e?.message || "删除失败");
    } finally {
      setActioningId((id) => (id === r.id ? null : id));
    }
  };

  const onSubmit = async () => {
    if (!form.code.trim() || !form.title.trim() || !form.url.trim() || !form.classification.trim()) {
      toast.error("请填写：编码 / 标题 / URL / 密级");
      return;
    }
    try {
      const payload = toUpsertPayload(form);
      if (mode === "create") {
        await reportsService.create(payload);
      } else if (editing) {
        await reportsService.update(editing.id, payload);
      }
      toast.success("已保存");
      setOpen(false);
      await fetchList();
    } catch (e: any) {
      console.error(e);
      toast.error(e?.message || "保存失败");
    }
  };

  const deptName = (code: string) => deptDict.get(String(code))?.nameZh || deptDict.get(String(code))?.nameEn || code;
  const selectedDeptCode = form.deptCodes?.[0] || "";
  const selectedDeptLabel = selectedDeptCode ? deptName(selectedDeptCode) : "全部部门";
  const selectedRoleCount = Array.isArray(form.roleCodes) ? form.roleCodes.length : 0;

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <CardTitle className="text-base">报表链接管理</CardTitle>
          <div className="flex flex-wrap items-center gap-2">
            <Input
              value={keyword}
              placeholder="搜索标题/编码"
              onChange={(e) => setKeyword(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && fetchList()}
              className="w-[220px]"
            />
            <Select value={deptCode} onValueChange={setDeptCode}>
              <SelectTrigger className="w-[180px]">
                <SelectValue placeholder="部门" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">全部部门</SelectItem>
                {departments.map((d) => (
                  <SelectItem key={String(d.code)} value={String(d.code)}>
                    {d.nameZh || d.nameEn || d.code}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={reportType} onValueChange={setReportType}>
              <SelectTrigger className="w-[160px]">
                <SelectValue placeholder="类型" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">全部类型</SelectItem>
                {reportTypes.map((t) => (
                  <SelectItem key={t} value={t}>
                    {t}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <div className="flex items-center gap-2 px-2">
              <Label className="text-xs text-muted-foreground">仅启用</Label>
              <Switch checked={enabledOnly} onCheckedChange={setEnabledOnly} />
            </div>
            <Button variant="outline" onClick={fetchList} disabled={loading}>
              刷新
            </Button>
            <Button onClick={onCreate}>新建</Button>
          </div>
        </CardHeader>
        <CardContent className="overflow-x-auto">
          <table className="w-full min-w-[1320px] table-fixed border-collapse text-sm">
            <thead className="bg-muted/40 text-left text-xs text-muted-foreground whitespace-nowrap tracking-wide">
              <tr>
                <th className="px-4 py-2 font-medium w-[180px]">编码</th>
                <th className="px-4 py-2 font-medium w-[260px]">标题</th>
                <th className="px-4 py-2 font-medium w-[90px]">引擎</th>
                <th className="px-4 py-2 font-medium w-[120px]">类型</th>
                <th className="px-4 py-2 font-medium w-[280px]">部门范围</th>
                <th className="px-4 py-2 font-medium w-[240px]">角色范围</th>
                <th className="px-4 py-2 font-medium w-[110px]">密级</th>
                <th className="px-4 py-2 font-medium w-[80px]">状态</th>
                <th className="px-4 py-2 font-medium w-[170px]">更新</th>
                <th className="px-4 py-2 font-medium w-[190px]">操作</th>
              </tr>
            </thead>
            <tbody>
              {items.map((r) => (
                <tr key={r.id} className="border-b last:border-b-0">
                  <td className="px-3 py-2 font-mono text-xs truncate" title={r.code}>
                    {r.code}
                  </td>
                  <td className="px-3 py-2 font-medium truncate" title={r.title}>
                    {r.title}
                  </td>
                  <td className="px-3 py-2">{engineLabel(r.engine)}</td>
                  <td className="px-3 py-2">{r.reportType || "-"}</td>
                  <td
                    className="px-3 py-2 text-xs truncate"
                    title={
                      Array.isArray(r.deptCodes) && r.deptCodes.length ? r.deptCodes.map(deptName).join(", ") : "全部"
                    }
                  >
                    {Array.isArray(r.deptCodes) && r.deptCodes.length ? r.deptCodes.map(deptName).join(", ") : "全部"}
                  </td>
                  <td
                    className="px-3 py-2 text-xs truncate"
                    title={Array.isArray(r.roleCodes) && r.roleCodes.length ? r.roleCodes.join(", ") : "全部"}
                  >
                    {Array.isArray(r.roleCodes) && r.roleCodes.length ? r.roleCodes.join(", ") : "全部"}
                  </td>
                  <td className="px-3 py-2" title={String(r.classification || "")}>
                    {classificationToLabelZh(r.classification)}
                  </td>
                  <td className="px-3 py-2">{typeof r.enabled === "boolean" ? (r.enabled ? "启用" : "禁用") : "-"}</td>
                  <td className="px-3 py-2 text-xs text-muted-foreground">
                    {r.updatedAt ? new Date(r.updatedAt).toLocaleString() : "-"}
                  </td>
                  <td className="px-3 py-2 whitespace-nowrap">
                    <Button variant="ghost" size="sm" onClick={() => onEdit(r)} disabled={actioningId === r.id}>
                      编辑
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => (r.enabled ? onDisable(r) : onEnable(r))}
                      disabled={actioningId === r.id}
                    >
                      {r.enabled ? "禁用" : "启用"}
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-destructive hover:text-destructive"
                      onClick={() => onPurge(r)}
                      disabled={actioningId === r.id}
                    >
                      删除
                    </Button>
                  </td>
                </tr>
              ))}
              {!items.length && (
                <tr>
                  <td colSpan={10} className="px-3 py-8 text-center text-xs text-muted-foreground">
                    {loading ? "加载中…" : "暂无数据"}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </CardContent>
      </Card>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{mode === "create" ? "新建报表链接" : "编辑报表链接"}</DialogTitle>
          </DialogHeader>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div className="grid gap-2">
              <Label>编码 *</Label>
              <Input value={form.code} onChange={(e) => setForm((f) => ({ ...f, code: e.target.value }))} />
            </div>
            <div className="grid gap-2">
              <Label>标题 *</Label>
              <Input value={form.title} onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))} />
            </div>

            <div className="grid gap-2">
              <Label>引擎</Label>
              <Select value={form.engine} onValueChange={(v) => setForm((f) => ({ ...f, engine: v }))}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="HETU">HETU</SelectItem>
                  <SelectItem value="METABASE">ANALYTICS</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-2">
              <Label>类型</Label>
              <Input value={form.reportType} onChange={(e) => setForm((f) => ({ ...f, reportType: e.target.value }))} />
            </div>

            <div className="grid gap-2">
              <Label>部门范围（单选；为空=全部）</Label>
              <Popover open={deptPickerOpen} onOpenChange={setDeptPickerOpen}>
                <PopoverTrigger asChild>
                  <Button
                    variant="outline"
                    role="combobox"
                    aria-expanded={deptPickerOpen}
                    className={cn("justify-between", selectedDeptCode ? "" : "text-muted-foreground")}
                  >
                    {selectedDeptLabel}
                    <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
                  </Button>
                </PopoverTrigger>
                <PopoverContent className="w-[360px] p-0" align="start">
                  <Command>
                    <CommandInput placeholder="搜索部门..." value={deptSearch} onValueChange={setDeptSearch} />
                    <CommandList>
                      <CommandEmpty>未找到匹配部门</CommandEmpty>
                      <CommandGroup heading="部门">
                        <CommandItem
                          value="__ALL__"
                          onSelect={() => {
                            setForm((f) => ({ ...f, deptCodes: [] }));
                            setDeptPickerOpen(false);
                          }}
                        >
                          <span className="font-medium">全部部门</span>
                          <Check className={cn("ml-2 h-4 w-4", !selectedDeptCode ? "opacity-100" : "opacity-0")} />
                        </CommandItem>
                        {visibleDeptItems.map((d) => (
                          <CommandItem
                            key={d.code}
                            value={`${d.code} ${d.label} ${d.path}`}
                            onSelect={() => {
                              setForm((f) => ({ ...f, deptCodes: [d.code] }));
                              setDeptPickerOpen(false);
                            }}
                          >
                            <span
                              className={cn("truncate", d.isRoot ? "font-medium" : "text-sm")}
                              title={d.path}
                              style={{ paddingLeft: `${8 + d.depth * 14}px` }}
                            >
                              {d.label}
                            </span>
                            <Check
                              className={cn("ml-auto h-4 w-4", selectedDeptCode === d.code ? "opacity-100" : "opacity-0")}
                            />
                          </CommandItem>
                        ))}
                      </CommandGroup>
                    </CommandList>
                  </Command>
                </PopoverContent>
              </Popover>
            </div>
            <div className="grid gap-2">
              <Label>角色范围（多选；为空=全部）</Label>
              <Popover open={rolePickerOpen} onOpenChange={setRolePickerOpen}>
                <PopoverTrigger asChild>
                  <Button
                    variant="outline"
                    role="combobox"
                    aria-expanded={rolePickerOpen}
                    className={cn("justify-between", selectedRoleCount ? "" : "text-muted-foreground")}
                  >
                    {selectedRoleCount ? `已选 ${selectedRoleCount} 个角色` : "全部角色"}
                    <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
                  </Button>
                </PopoverTrigger>
                <PopoverContent className="w-[360px] p-0" align="start">
                  <Command>
                    <CommandInput placeholder="搜索角色..." value={roleSearch} onValueChange={setRoleSearch} />
                    <CommandList>
                      <CommandEmpty>未找到匹配角色</CommandEmpty>
                      <CommandGroup heading="角色">
                        <CommandItem
                          value="__ALL__"
                          onSelect={() => {
                            setForm((f) => ({ ...f, roleCodes: [] }));
                          }}
                        >
                          <span className="font-medium">全部角色</span>
                          <Check className={cn("ml-2 h-4 w-4", selectedRoleCount === 0 ? "opacity-100" : "opacity-0")} />
                        </CommandItem>
                        {roleItems.map((r) => {
                          const code = normalizeRoleCode(r.name);
                          const selected = (form.roleCodes || []).includes(code);
                          return (
                            <CommandItem
                              key={String(r.id || r.name)}
                              value={`${code} ${r.description || ""}`}
                              onSelect={() => {
                                setForm((f) => {
                                  const prev = Array.isArray(f.roleCodes) ? f.roleCodes : [];
                                  const set = new Set(prev.map(normalizeRoleCode).filter(Boolean));
                                  if (set.has(code)) set.delete(code);
                                  else set.add(code);
                                  return { ...f, roleCodes: Array.from(set) };
                                });
                              }}
                            >
                              <div className="flex flex-col overflow-hidden">
                                <span className="truncate font-medium">{code}</span>
                                {r.description ? (
                                  <span className="truncate text-xs text-muted-foreground">{r.description}</span>
                                ) : null}
                              </div>
                              <Check className={cn("ml-auto h-4 w-4", selected ? "opacity-100" : "opacity-0")} />
                            </CommandItem>
                          );
                        })}
                      </CommandGroup>
                    </CommandList>
                  </Command>
                  {selectedRoleCount ? (
                    <div className="border-t p-2">
                      <div className="flex flex-wrap gap-1">
                        {(form.roleCodes || []).slice(0, 12).map((rc) => (
                          <Badge key={rc} variant="secondary" className="max-w-[320px] truncate">
                            {rc}
                          </Badge>
                        ))}
                        {selectedRoleCount > 12 ? <Badge variant="outline">+{selectedRoleCount - 12}</Badge> : null}
                      </div>
                      <div className="mt-2 flex justify-end">
                        <Button type="button" size="sm" variant="ghost" onClick={() => setForm((f) => ({ ...f, roleCodes: [] }))}>
                          清空
                        </Button>
                      </div>
                    </div>
                  ) : null}
                </PopoverContent>
              </Popover>
            </div>

            <div className="grid gap-2">
              <Label>密级 *</Label>
              <Select
                value={form.classification}
                onValueChange={(v) => setForm((f) => ({ ...f, classification: v as ClassificationLevel }))}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="PUBLIC">公开（PUBLIC）</SelectItem>
                  <SelectItem value="INTERNAL">内部（INTERNAL）</SelectItem>
                  <SelectItem value="SECRET">秘密（SECRET）</SelectItem>
                  <SelectItem value="CONFIDENTIAL">机密（CONFIDENTIAL）</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-2">
              <Label>排序</Label>
              <Input
                type="number"
                value={String(form.sortOrder)}
                onChange={(e) => setForm((f) => ({ ...f, sortOrder: Number(e.target.value) }))}
              />
            </div>

            <div className="md:col-span-2 grid gap-2">
              <div className="flex items-center justify-between gap-2">
                <Label>URL *</Label>
                {String(form.engine || "").toUpperCase() === "HETU" ? (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => setForm((f) => ({ ...f, url: normalizeHetuUrl(f.url) }))}
                    title="将 http(s)://IP:7778/... 转成 /screen/... 或 /dashboards/...，便于关闭 7778 端口并通过 Traefik 访问"
                  >
                    规范化河图链接
                  </Button>
                ) : String(form.engine || "").toUpperCase() === "METABASE" ? (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => setForm((f) => ({ ...f, url: normalizeMetabaseUrl(f.url) }))}
                    title="将 https://analytics.xxx/... 转成 /analytics/...，统一挂载到平台域名下"
                  >
                    规范化 Analytics 链接
                  </Button>
                ) : null}
              </div>
              <Textarea
                value={form.url}
                onChange={(e) => setForm((f) => ({ ...f, url: e.target.value }))}
                placeholder="例如：https://analytics.xxx/dashboard/1 或 http(s)://河图/share/..."
              />
              {String(form.engine || "").toUpperCase() === "HETU" ? (
                <div className="text-xs text-muted-foreground">
                  建议：河图 URL 尽量保存为以 <code>/screen</code> 或 <code>/dashboards</code> 开头的相对路径，便于统一走平台域名反代并关闭 7778 直连。
                </div>
              ) : String(form.engine || "").toUpperCase() === "METABASE" ? (
                <div className="text-xs text-muted-foreground">
                  建议：Analytics URL 保存为以 <code>/analytics</code> 开头的相对路径，便于统一走平台域名反代（同域名免跨域）。
                </div>
              ) : null}
            </div>

            <div className="md:col-span-2 flex items-center gap-2">
              <Switch checked={form.enabled} onCheckedChange={(checked) => setForm((f) => ({ ...f, enabled: checked }))} />
              <Label className="text-sm">启用</Label>
            </div>
          </div>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setOpen(false)}>
              取消
            </Button>
            <Button onClick={onSubmit}>保存</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
