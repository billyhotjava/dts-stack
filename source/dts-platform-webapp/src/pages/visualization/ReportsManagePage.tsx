import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Switch } from "@/ui/switch";
import { Textarea } from "@/ui/textarea";
import deptService, { type DeptDto } from "@/api/services/deptService";
import reportsService, { type ReportLink, type ReportLinkUpsertRequest } from "@/api/services/reportsService";

type FormState = {
  code: string;
  title: string;
  url: string;
  engine: string;
  reportType: string;
  deptCodesCsv: string;
  roleCodesCsv: string;
  classification: string;
  enabled: boolean;
  sortOrder: number;
};

const DEFAULT_FORM: FormState = {
  code: "",
  title: "",
  url: "",
  engine: "HETU",
  reportType: "",
  deptCodesCsv: "",
  roleCodesCsv: "",
  classification: "INTERNAL",
  enabled: true,
  sortOrder: 0,
};

function splitCsv(raw: string): string[] {
  const s = String(raw || "").trim();
  if (!s) return [];
  return s
    .split(/[,，]/)
    .map((x) => x.trim())
    .filter(Boolean);
}

function toUpsertPayload(form: FormState): ReportLinkUpsertRequest {
  return {
    code: form.code.trim(),
    title: form.title.trim(),
    url: form.url.trim(),
    engine: form.engine?.trim() || "HETU",
    reportType: form.reportType?.trim() || undefined,
    deptCodes: splitCsv(form.deptCodesCsv),
    roleCodes: splitCsv(form.roleCodesCsv),
    classification: form.classification?.trim() || "INTERNAL",
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

export default function ReportsManagePage() {
  const [items, setItems] = useState<ReportLink[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [deptCode, setDeptCode] = useState<string>("all");
  const [reportType, setReportType] = useState<string>("all");
  const [enabledOnly, setEnabledOnly] = useState(false);
  const [departments, setDepartments] = useState<DeptDto[]>([]);

  const [open, setOpen] = useState(false);
  const [mode, setMode] = useState<"create" | "edit">("create");
  const [editing, setEditing] = useState<ReportLink | null>(null);
  const [form, setForm] = useState<FormState>({ ...DEFAULT_FORM });

  const deptDict = useMemo(() => {
    const m = new Map<string, DeptDto>();
    for (const d of departments) m.set(String(d.code), d);
    return m;
  }, [departments]);

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

  const onCreate = () => {
    setMode("create");
    setEditing(null);
    setForm({ ...DEFAULT_FORM });
    setOpen(true);
  };

  const onEdit = (r: ReportLink) => {
    setMode("edit");
    setEditing(r);
    setForm({
      code: r.code || "",
      title: r.title || "",
      url: r.url || "",
      engine: r.engine || "HETU",
      reportType: (r.reportType as any) || "",
      deptCodesCsv: Array.isArray(r.deptCodes) ? r.deptCodes.join(",") : "",
      roleCodesCsv: Array.isArray(r.roleCodes) ? r.roleCodes.join(",") : "",
      classification: r.classification || "INTERNAL",
      enabled: typeof r.enabled === "boolean" ? r.enabled : true,
      sortOrder: Number.isFinite(Number(r.sortOrder)) ? Number(r.sortOrder) : 0,
    });
    setOpen(true);
  };

  const onDisable = async (r: ReportLink) => {
    try {
      await reportsService.disable(r.id);
      toast.success("已禁用");
      await fetchList();
    } catch (e: any) {
      console.error(e);
      toast.error(e?.message || "禁用失败");
    }
  };

  const onEnable = async (r: ReportLink) => {
    try {
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
	          <table className="w-full min-w-[1100px] table-fixed border-collapse text-sm">
	            <thead className="bg-muted/40 text-left text-xs uppercase text-muted-foreground">
	              <tr>
	                <th className="px-3 py-2 font-medium w-[180px]">编码</th>
	                <th className="px-3 py-2 font-medium">标题</th>
	                <th className="px-3 py-2 font-medium w-[110px]">引擎</th>
	                <th className="px-3 py-2 font-medium w-[120px]">类型</th>
	                <th className="px-3 py-2 font-medium w-[220px]">部门范围</th>
	                <th className="px-3 py-2 font-medium w-[120px]">角色范围</th>
	                <th className="px-3 py-2 font-medium w-[90px]">密级</th>
	                <th className="px-3 py-2 font-medium w-[80px]">状态</th>
	                <th className="px-3 py-2 font-medium w-[160px]">更新</th>
	                <th className="px-3 py-2 font-medium w-[160px]">操作</th>
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
                  <td className="px-3 py-2">{r.engine}</td>
                  <td className="px-3 py-2">{r.reportType || "-"}</td>
                  <td className="px-3 py-2 text-xs">
                    {Array.isArray(r.deptCodes) && r.deptCodes.length ? r.deptCodes.map(deptName).join(", ") : "全部"}
	                  </td>
	                  <td className="px-3 py-2 text-xs">
	                    {Array.isArray(r.roleCodes) && r.roleCodes.length ? r.roleCodes.join(", ") : "全部"}
	                  </td>
	                  <td className="px-3 py-2">{r.classification}</td>
	                  <td className="px-3 py-2">{typeof r.enabled === "boolean" ? (r.enabled ? "启用" : "禁用") : "-"}</td>
	                  <td className="px-3 py-2 text-xs text-muted-foreground">
	                    {r.updatedAt ? new Date(r.updatedAt).toLocaleString() : "-"}
	                  </td>
	                  <td className="px-3 py-2">
	                    <Button variant="ghost" size="sm" onClick={() => onEdit(r)}>
	                      编辑
	                    </Button>
	                    <Button variant="ghost" size="sm" onClick={() => (r.enabled ? onDisable(r) : onEnable(r))}>
	                      {r.enabled ? "禁用" : "启用"}
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
                  <SelectItem value="METABASE">METABASE</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-2">
              <Label>类型</Label>
              <Input value={form.reportType} onChange={(e) => setForm((f) => ({ ...f, reportType: e.target.value }))} />
            </div>

            <div className="grid gap-2">
              <Label>部门范围（逗号分隔；为空=全部）</Label>
              <Input
                value={form.deptCodesCsv}
                onChange={(e) => setForm((f) => ({ ...f, deptCodesCsv: e.target.value }))}
                placeholder="如：1001,1002"
              />
            </div>
            <div className="grid gap-2">
              <Label>角色范围（逗号分隔；为空=全部）</Label>
              <Input
                value={form.roleCodesCsv}
                onChange={(e) => setForm((f) => ({ ...f, roleCodesCsv: e.target.value }))}
                placeholder="如：ROLE_EMPLOYEE,ROLE_DEPT_DATA_VIEWER"
              />
            </div>

            <div className="grid gap-2">
              <Label>密级 *</Label>
              <Select value={form.classification} onValueChange={(v) => setForm((f) => ({ ...f, classification: v }))}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="PUBLIC">PUBLIC</SelectItem>
                  <SelectItem value="INTERNAL">INTERNAL</SelectItem>
                  <SelectItem value="SECRET">SECRET</SelectItem>
                  <SelectItem value="CONFIDENTIAL">CONFIDENTIAL</SelectItem>
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
                    title="将 https://metabase.xxx/... 转成 /analytics/...，统一挂载到平台域名下"
                  >
                    规范化 Metabase 链接
                  </Button>
                ) : null}
              </div>
              <Textarea
                value={form.url}
                onChange={(e) => setForm((f) => ({ ...f, url: e.target.value }))}
                placeholder="例如：https://metabase.xxx/dashboard/1 或 http(s)://河图/share/..."
              />
              {String(form.engine || "").toUpperCase() === "HETU" ? (
                <div className="text-xs text-muted-foreground">
                  建议：河图 URL 尽量保存为以 <code>/screen</code> 或 <code>/dashboards</code> 开头的相对路径，便于统一走平台域名反代并关闭 7778 直连。
                </div>
              ) : String(form.engine || "").toUpperCase() === "METABASE" ? (
                <div className="text-xs text-muted-foreground">
                  建议：Metabase URL 保存为以 <code>/analytics</code> 开头的相对路径，便于统一走平台域名反代（同域名免跨域）。
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
