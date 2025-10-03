import { useEffect, useMemo, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";
import {
	fetchInfraFeatures,
	listConnectionTestLogs,
	listInfraDataSources,
	testHiveConnection,
	type ConnectionTestLog,
	type InfraDataSource,
	type InfraFeatureFlags,
} from "@/api/services/infraService";
import { Icon } from "@/components/icon";
import type { HiveConnectionTestResult } from "#/infra";
import { Button } from "@/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/ui/card";
import { Alert, AlertDescription, AlertTitle } from "@/ui/alert";
import { Badge } from "@/ui/badge";
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/ui/form";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Switch } from "@/ui/switch";
import { Textarea } from "@/ui/textarea";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/ui/collapsible";
import { cn } from "@/utils";

const formSchema = z
  .object({
    name: z.string().min(1, "请输入数据源名称"),
    description: z.string().optional(),
    host: z.string().min(1, "请输入 Hive 主机地址"),
    port: z.coerce.number().int().min(1).max(65535),
    database: z.string().min(1, "请输入默认数据库"),
    servicePrincipal: z.string().min(1, "请输入服务主体 principal"),
    realm: z.string().min(1, "请输入 Kerberos Realm"),
    kdcText: z.string().min(1, "至少填写一个 KDC 地址"),
    loginPrincipal: z.string().min(1, "请输入登录主体"),
    loginUser: z.string().optional(),
    authMethod: z.enum(["KEYTAB", "PASSWORD"]),
    keytabBase64: z.string().optional(),
    keytabFileName: z.string().optional(),
    password: z.string().optional(),
    krb5Conf: z.string().optional(),
    proxyUser: z.string().optional(),
    testQuery: z.string().optional(),
    extraParams: z.string().optional(),
    useHttpTransport: z.boolean(),
    httpPath: z.string().optional(),
    useSsl: z.boolean(),
    useCustomJdbc: z.boolean(),
    customJdbcUrl: z.string().optional(),
  })
  .superRefine((values, ctx) => {
    if (values.authMethod === "KEYTAB" && !values.keytabBase64) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["keytabBase64"],
        message: "请上传 Keytab 文件",
      });
    }
    if (values.authMethod === "PASSWORD" && !values.password) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["password"],
        message: "请输入 Kerberos 密码",
      });
    }
    if (values.krb5Conf?.trim()) {
      return;
    }
    const kdcs = parseKdcText(values.kdcText);
    if (!kdcs.length) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["kdcText"],
        message: "请至少配置一个 KDC 地址",
      });
    }
    if (values.useHttpTransport && !values.httpPath?.trim()) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["httpPath"],
        message: "HTTP 模式需要指定 httpPath",
      });
    }
    if (values.useCustomJdbc && !values.customJdbcUrl?.trim()) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["customJdbcUrl"],
        message: "请输入自定义 JDBC 连接串",
      });
    }
  });

type FormValues = z.infer<typeof formSchema>;

const defaultValues: FormValues = {
  name: "生产 Hive",
  description: "TDS Inceptor 集群",
  host: "inceptor-gw.example.com",
  port: 10000,
  database: "default",
  servicePrincipal: "hive/_HOST@EXAMPLE.COM",
  realm: "EXAMPLE.COM",
  kdcText: "kdc1.example.com:88,kdc2.example.com:88",
  loginPrincipal: "oadmin@EXAMPLE.COM",
  loginUser: "oadmin",
  authMethod: "KEYTAB",
  keytabBase64: "",
  keytabFileName: undefined,
  password: "",
  krb5Conf: "",
  proxyUser: "",
  testQuery: "SELECT 1",
  extraParams: "sasl.qop=auth\nprincipalOverride=true",
  useHttpTransport: false,
  httpPath: "",
  useSsl: false,
  useCustomJdbc: false,
  customJdbcUrl: "",
};

export default function DataSourcesPage() {
  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues,
  });

  const authMethod = useWatch({ control: form.control, name: "authMethod" });
  const useHttpTransport = useWatch({ control: form.control, name: "useHttpTransport" });
  const useCustomJdbc = useWatch({ control: form.control, name: "useCustomJdbc" });
  const watched = useWatch({ control: form.control });

  const jdbcPreview = useMemo(() => buildJdbcUrl(watched), [watched]);

  const [features, setFeatures] = useState<InfraFeatureFlags | null>(null);
  const [sources, setSources] = useState<InfraDataSource[]>([]);
  const [logs, setLogs] = useState<ConnectionTestLog[]>([]);
  const [loadingFeatures, setLoadingFeatures] = useState(false);
  const [loadingSources, setLoadingSources] = useState(false);
  const [loadingLogs, setLoadingLogs] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<HiveConnectionTestResult | null>(null);
  const [connectionAdvancedOpen, setConnectionAdvancedOpen] = useState(false);
  const [kerberosAdvancedOpen, setKerberosAdvancedOpen] = useState(false);

  const loadFeatures = async () => {
    setLoadingFeatures(true);
    try {
      const data = await fetchInfraFeatures();
      setFeatures(data);
    } finally {
      setLoadingFeatures(false);
    }
  };

  const loadSources = async () => {
    setLoadingSources(true);
    try {
      const data = await listInfraDataSources();
      setSources(data);
    } finally {
      setLoadingSources(false);
    }
  };

  const loadLogs = async () => {
    setLoadingLogs(true);
    try {
      const data = await listConnectionTestLogs();
      setLogs(data);
    } finally {
      setLoadingLogs(false);
    }
  };

  useEffect(() => {
    void loadFeatures();
    void loadSources();
    void loadLogs();
  }, []);

  const handleKeytabUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;
    if (file.size > 2 * 1024 * 1024) {
      form.setError("keytabBase64", { message: "Keytab 文件过大（>2MB）" });
      return;
    }
    const buffer = await file.arrayBuffer();
    const bytes = new Uint8Array(buffer);
    let binary = "";
    for (let i = 0; i < bytes.byteLength; i += 1) {
      binary += String.fromCharCode(bytes[i]);
    }
    const base64 = btoa(binary);
    form.clearErrors("keytabBase64");
    form.setValue("keytabBase64", base64, { shouldDirty: true });
    form.setValue("keytabFileName", file.name, { shouldDirty: true });
  };

  const resetKeytab = () => {
    form.setValue("keytabBase64", "", { shouldDirty: true });
    form.setValue("keytabFileName", undefined, { shouldDirty: true });
  };

  const onSubmit = form.handleSubmit(async (values) => {
    const jdbcUrl = values.useCustomJdbc && values.customJdbcUrl?.trim() ? values.customJdbcUrl.trim() : jdbcPreview;
    if (!jdbcUrl) {
      toast.error("无法生成 JDBC 连接串，请检查基础配置");
      return;
    }
    const kdcs = values.krb5Conf?.trim() ? undefined : parseKdcText(values.kdcText);
    const jdbcPropsEntries = parseKeyValueEntries(values.extraParams);
    const jdbcProps: Record<string, string> = Object.fromEntries(jdbcPropsEntries);
    if (values.useSsl) {
      jdbcProps.ssl = "true";
    }

    const payload = {
      jdbcUrl,
      loginPrincipal: values.loginPrincipal.trim(),
      loginUser: values.loginUser?.trim() || undefined,
      realm: values.krb5Conf?.trim() ? undefined : values.realm.trim(),
      kdcs,
      krb5Conf: values.krb5Conf?.trim() || undefined,
      authMethod: values.authMethod,
      keytabBase64: values.authMethod === "KEYTAB" ? values.keytabBase64 : undefined,
      keytabFileName: values.authMethod === "KEYTAB" ? values.keytabFileName : undefined,
      password: values.authMethod === "PASSWORD" ? values.password : undefined,
      proxyUser: values.proxyUser?.trim() || undefined,
      testQuery: values.testQuery?.trim() || undefined,
      jdbcProperties: Object.keys(jdbcProps).length ? jdbcProps : undefined,
      remarks: values.description?.trim() || undefined,
    } satisfies Parameters<typeof testHiveConnection>[0];

    setTesting(true);
    setTestResult(null);
    try {
      const result = await testHiveConnection(payload);
      setTestResult(result);
      await loadLogs();
      if (result.success) {
        toast.success(`连接成功，用时 ${result.elapsedMillis} ms`);
      } else {
        toast.error(result.message || "连接失败");
      }
    } catch (error: any) {
      const message = error?.message || "连接测试失败";
      setTestResult({ success: false, message, elapsedMillis: 0, warnings: [] });
      toast.error(message);
    } finally {
      setTesting(false);
    }
  });

  const handleReset = () => {
    form.reset(defaultValues);
    setTestResult(null);
  };

  return (
    <div className="space-y-4">
      <Form {...form}>
        <form onSubmit={onSubmit} className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">必填信息</CardTitle>
              <CardDescription>先完成核心字段，确保能够与 Hive/Inceptor 建立 Kerberos 连接。</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid gap-4 md:grid-cols-2">
                <FormField
                  control={form.control}
                  name="name"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>数据源名称</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder="如：生产 Hive 集群" />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="host"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>主机 / 网关</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder="如：inceptor-gw.example.com" />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="port"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>端口</FormLabel>
                      <FormControl>
                        <Input type="number" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="database"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>默认数据库</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder="default" />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>

              <div className="grid gap-4 md:grid-cols-2">
                <FormField
                  control={form.control}
                  name="servicePrincipal"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>服务 Principal</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder="hive/_HOST@EXAMPLE.COM" />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="realm"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Realm</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder="EXAMPLE.COM" />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>

              <FormField
                control={form.control}
                name="kdcText"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>KDC 地址列表</FormLabel>
                    <FormControl>
                      <Textarea
                        rows={3}
                        {...field}
                        placeholder={`支持换行或逗号分隔，如：\nkdc1.example.com:88\nkdc2.example.com:88`}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <div className="grid gap-4 md:grid-cols-2">
                <FormField
                  control={form.control}
                  name="loginPrincipal"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>登录 Principal</FormLabel>
                      <FormControl>
                        <Input {...field} placeholder="oadmin@EXAMPLE.COM" />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <div className="space-y-2">
                  <FormLabel>认证方式</FormLabel>
                  <div className="flex flex-wrap items-center gap-2">
                    <Select
                      value={authMethod}
                      onValueChange={(value) => form.setValue("authMethod", value as FormValues["authMethod"], { shouldDirty: true })}
                    >
                      <SelectTrigger className="w-48">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="KEYTAB">Keytab</SelectItem>
                        <SelectItem value="PASSWORD">密码</SelectItem>
                      </SelectContent>
                    </Select>
                    <Badge variant="outline">推荐：Keytab</Badge>
                  </div>
                </div>
              </div>

              {authMethod === "KEYTAB" ? (
                <FormField
                  control={form.control}
                  name="keytabBase64"
                  render={() => (
                    <FormItem>
                      <FormLabel>上传 Keytab</FormLabel>
                      <FormControl>
                        <Input type="file" accept=".keytab" onChange={handleKeytabUpload} />
                      </FormControl>
                      <FormMessage />
                      {watched.keytabFileName ? (
                        <div className="flex items-center gap-2 pt-1 text-xs text-muted-foreground">
                          <Icon icon="solar:check-circle-bold" className="h-4 w-4 text-emerald-500" />
                          <span>已选择：{watched.keytabFileName}</span>
                          <Button type="button" variant="ghost" size="sm" onClick={resetKeytab}>清除</Button>
                        </div>
                      ) : (
                        <p className="text-xs text-muted-foreground">请选择平台运维下发的客户端 keytab。</p>
                      )}
                    </FormItem>
                  )}
                />
              ) : (
                <FormField
                  control={form.control}
                  name="password"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Kerberos 密码</FormLabel>
                      <FormControl>
                        <Input type="password" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              )}
            </CardContent>
          </Card>

          <Card>
            <Collapsible open={connectionAdvancedOpen} onOpenChange={setConnectionAdvancedOpen}>
              <CardHeader className="flex flex-row items-start justify-between space-y-0">
                <div>
                  <CardTitle className="text-base">高级连接选项</CardTitle>
                  <CardDescription>可选：描述信息、HTTP/SSL、附加 JDBC 参数、手动连接串。</CardDescription>
                </div>
                <CollapsibleTrigger asChild>
                  <Button type="button" variant="ghost" size="sm" className="transition-none">
                    <Icon
                      icon="solar:alt-arrow-down-bold"
                      className={cn("h-4 w-4 transition-transform", connectionAdvancedOpen ? "rotate-180" : "")}
                    />
                  </Button>
                </CollapsibleTrigger>
              </CardHeader>
              <CollapsibleContent>
                <CardContent className="space-y-4">
                  <FormField
                    control={form.control}
                    name="description"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>描述</FormLabel>
                        <FormControl>
                          <Input {...field} placeholder="可选：说明用途" />
                        </FormControl>
                      </FormItem>
                    )}
                  />

                  <div className="grid gap-4 md:grid-cols-2">
                    <FormField
                      control={form.control}
                      name="useHttpTransport"
                      render={({ field }) => (
                        <FormItem className="flex flex-col space-y-2">
                          <FormLabel>传输模式</FormLabel>
                          <div className="flex items-center gap-2">
                            <Switch checked={field.value} onCheckedChange={field.onChange} />
                            <span className="text-sm text-muted-foreground">HTTP 模式（默认 Binary）</span>
                          </div>
                        </FormItem>
                      )}
                    />
                    {useHttpTransport ? (
                      <FormField
                        control={form.control}
                        name="httpPath"
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>HTTP Path</FormLabel>
                            <FormControl>
                              <Input {...field} placeholder="cliservice" />
                            </FormControl>
                            <FormMessage />
                          </FormItem>
                        )}
                      />
                    ) : (
                      <div className="h-full rounded-md border border-dashed border-muted-foreground/30 px-3 py-2 text-sm text-muted-foreground">
                        默认采用二进制端口 10000 直连，可切换至 HTTP 以适配代理网关。
                      </div>
                    )}
                  </div>

                  <FormField
                    control={form.control}
                    name="useSsl"
                    render={({ field }) => (
                      <FormItem className="flex items-center justify-between rounded-md border border-border px-4 py-3">
                        <div>
                          <FormLabel className="flex items-center gap-2">
                            <Icon icon="solar:shield-check-bold" className="h-4 w-4" />
                            启用 TLS/SSL
                          </FormLabel>
                          <p className="text-xs text-muted-foreground">勾选后将自动追加 ssl=true，可在附加参数中补充 truststore 设置。</p>
                        </div>
                        <FormControl>
                          <Switch checked={field.value} onCheckedChange={field.onChange} />
                        </FormControl>
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name="extraParams"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>附加 JDBC 参数</FormLabel>
                        <FormControl>
                          <Textarea
                            rows={4}
                            {...field}
                            placeholder={`每行一个 k=v，如：\ntransportMode=http\nhttpPath=cliservice`}
                          />
                        </FormControl>
                        <FormMessage />
                        <p className="text-xs text-muted-foreground">支持添加 sasl.qop、principalOverride 等高级参数，# 开头的行将忽略。</p>
                      </FormItem>
                    )}
                  />

                  <div className="rounded-md border border-dashed border-muted-foreground/30 bg-muted/20 p-4">
                    <div className="flex items-center justify-between gap-2">
                      <div>
                        <Label className="text-sm font-medium">自动生成的 JDBC 连接串</Label>
                        <p className="text-xs text-muted-foreground">根据上方参数实时生成，可切换为手动编辑。</p>
                      </div>
                      <div className="flex items-center gap-2 text-sm text-muted-foreground">
                        <span>手动编辑</span>
                        <Switch checked={useCustomJdbc} onCheckedChange={(checked) => form.setValue("useCustomJdbc", checked)} />
                      </div>
                    </div>
                    <Textarea
                      className="mt-2 h-24 font-mono text-xs"
                      value={useCustomJdbc ? watched.customJdbcUrl : jdbcPreview}
                      onChange={(event) => form.setValue("customJdbcUrl", event.target.value, { shouldDirty: true })}
                      readOnly={!useCustomJdbc}
                    />
                  </div>
                </CardContent>
              </CollapsibleContent>
            </Collapsible>
          </Card>

          <Card>
            <Collapsible open={kerberosAdvancedOpen} onOpenChange={setKerberosAdvancedOpen}>
              <CardHeader className="flex flex-row items-start justify-between space-y-0">
                <div>
                  <CardTitle className="text-base">高级 Kerberos 设置</CardTitle>
                  <CardDescription>可选：JDBC 用户、代理用户、自定义 krb5.conf 等。</CardDescription>
                </div>
                <CollapsibleTrigger asChild>
                  <Button type="button" variant="ghost" size="sm" className="transition-none">
                    <Icon
                      icon="solar:alt-arrow-down-bold"
                      className={cn("h-4 w-4 transition-transform", kerberosAdvancedOpen ? "rotate-180" : "")}
                    />
                  </Button>
                </CollapsibleTrigger>
              </CardHeader>
              <CollapsibleContent>
                <CardContent className="space-y-4">
                  <div className="grid gap-4 md:grid-cols-2">
                    <FormField
                      control={form.control}
                      name="loginUser"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>JDBC 用户名（可选）</FormLabel>
                          <FormControl>
                            <Input {...field} placeholder="oadmin" />
                          </FormControl>
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={form.control}
                      name="proxyUser"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>代理用户（可选）</FormLabel>
                          <FormControl>
                            <Input {...field} placeholder="如需代理执行，可填写具体用户名" />
                          </FormControl>
                        </FormItem>
                      )}
                    />
                  </div>

                  <FormField
                    control={form.control}
                    name="krb5Conf"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>自定义 krb5.conf（可选）</FormLabel>
                        <FormControl>
                          <Textarea
                            rows={6}
                            {...field}
                            placeholder={`不填写则根据 Realm/KDC 自动生成。示例：\n[libdefaults]\n  default_realm = EXAMPLE.COM`}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </CardContent>
              </CollapsibleContent>
            </Collapsible>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">测试与提示</CardTitle>
              <CardDescription>配置完成后可立即测试 Kerberos 链路与 Hive 可达性。</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <FormField
                control={form.control}
                name="testQuery"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>验证 SQL</FormLabel>
                    <FormControl>
                      <Input {...field} placeholder="SELECT 1" />
                    </FormControl>
                  </FormItem>
                )}
              />

              <div className="rounded-md border border-dashed border-muted-foreground/40 bg-muted/30 p-4 text-sm text-muted-foreground">
                <ul className="list-disc space-y-2 pl-5">
                  <li>启动测试会在后端临时写入 keytab/krb5 文件并登录，再执行验证 SQL。</li>
                  <li>若后端返回 "无法生成 JDBC 连接串"，请检查主机、principal 与 HTTP 模式配置。</li>
                  <li>建议在生产环境使用最小权限技术账号，并关闭 Ticket 自动续期。</li>
                </ul>
              </div>

              <div className="flex flex-wrap items-center gap-2">
                <Button type="submit" disabled={testing}>
                  <Icon icon={testing ? "eos-icons:three-dots-loading" : "solar:wifi-router-bold"} className="mr-1 h-4 w-4" />
                  {testing ? "测试中..." : "测试连接"}
                </Button>
                <Button type="button" variant="outline" onClick={handleReset} disabled={testing}>
                  <Icon icon="solar:refresh-bold" className="mr-1 h-4 w-4" /> 重置示例
                </Button>
              </div>

              {testResult && (
                <Alert variant={testResult.success ? "default" : "destructive"}>
                  <AlertTitle>{testResult.success ? "连接成功" : "连接失败"}</AlertTitle>
                  <AlertDescription className="space-y-1 text-sm">
                    <p>{testResult.message}</p>
                    <div className="text-xs text-muted-foreground">
                      <span>耗时：{testResult.elapsedMillis} ms</span>
                      {testResult.engineVersion && <span className="ml-3">引擎版本：{testResult.engineVersion}</span>}
                      {testResult.driverVersion && <span className="ml-3">驱动：{testResult.driverVersion}</span>}
                    </div>
                    {testResult.warnings?.length ? (
                      <ul className="list-disc space-y-1 pl-5 text-xs text-muted-foreground">
                        {testResult.warnings.map((warn, index) => (
                          <li key={index}>{warn}</li>
                        ))}
                      </ul>
                    ) : null}
                  </AlertDescription>
                </Alert>
              )}
            </CardContent>
          </Card>
        </form>
      </Form>

      <Card>
        <CardHeader className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
          <div>
            <CardTitle className="text-base">基础能力开关</CardTitle>
            <CardDescription>多源支持等基础能力状态来自后端配置。</CardDescription>
          </div>
          <Button type="button" variant="outline" size="sm" onClick={loadFeatures} disabled={loadingFeatures}>
            <Icon icon="solar:refresh-bold" className="mr-1 h-4 w-4" /> 刷新
          </Button>
        </CardHeader>
        <CardContent className="flex flex-wrap items-center gap-4 text-sm">
          <div className="flex items-center gap-2">
            <Badge variant={features?.multiSourceEnabled ? "default" : "secondary"}>
              多数据源 {features?.multiSourceEnabled ? "已启用" : "未启用"}
            </Badge>
          </div>
          <p className="text-xs text-muted-foreground">
            如需支持多个外部 Hive 集群，请联系运维开启多源能力并补充密钥。
          </p>
        </CardContent>
      </Card>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between">
            <div>
              <CardTitle className="text-base">已登记数据源</CardTitle>
              <CardDescription>展示后端注册的数据源摘要（不包含敏感信息）。</CardDescription>
            </div>
            <Button type="button" variant="ghost" size="sm" onClick={loadSources} disabled={loadingSources}>
              <Icon icon="solar:refresh-bold" className="mr-1 h-4 w-4" /> 刷新
            </Button>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="max-h-[280px] overflow-y-auto rounded-md border">
              <table className="w-full table-fixed border-collapse text-sm">
                <thead className="bg-muted/40 text-left text-xs text-muted-foreground">
                  <tr>
                    <th className="px-3 py-2 font-medium">名称</th>
                    <th className="px-3 py-2 font-medium">类型</th>
                    <th className="px-3 py-2 font-medium">连接串</th>
                    <th className="px-3 py-2 font-medium">密钥</th>
                  </tr>
                </thead>
                <tbody>
                  {sources.map((item) => (
                    <tr key={item.id} className="border-b last:border-b-0">
                      <td className="px-3 py-2 font-medium">{item.name}</td>
                      <td className="px-3 py-2 text-xs text-muted-foreground">{item.type}</td>
                      <td className="px-3 py-2 text-xs font-mono text-muted-foreground break-all">{item.jdbcUrl}</td>
                      <td className="px-3 py-2">
                        <Badge variant="outline">{item.hasSecrets ? "已加密" : "未配置"}</Badge>
                      </td>
                    </tr>
                  ))}
                  {!sources.length && (
                    <tr>
                      <td colSpan={4} className="px-3 py-6 text-center text-xs text-muted-foreground">
                        {loadingSources ? "加载中…" : "暂无已登记数据源"}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
            <p className="text-xs text-muted-foreground">
              该列表仅展示概要信息，如需新增/编辑数据源，请通过后端 API 或配置文件管理。
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between">
            <div>
              <CardTitle className="text-base">最近连接测试</CardTitle>
              <CardDescription>展示最近 20 次后端记录的 Hive 连接自测结果。</CardDescription>
            </div>
            <Button type="button" variant="ghost" size="sm" onClick={loadLogs} disabled={loadingLogs}>
              <Icon icon="solar:refresh-bold" className="mr-1 h-4 w-4" /> 刷新
            </Button>
          </CardHeader>
          <CardContent className="max-h-[280px] overflow-y-auto">
            <table className="w-full table-fixed border-collapse text-sm">
              <thead className="bg-muted/40 text-left text-xs text-muted-foreground">
                <tr>
                  <th className="px-3 py-2 font-medium">时间</th>
                  <th className="px-3 py-2 font-medium">结果</th>
                  <th className="px-3 py-2 font-medium">耗时</th>
                  <th className="px-3 py-2 font-medium">备注</th>
                </tr>
              </thead>
              <tbody>
                {logs.map((log) => (
                  <tr key={log.id} className="border-b last:border-b-0">
                    <td className="px-3 py-2 text-xs text-muted-foreground">
                      {log.createdAt ? new Date(log.createdAt).toLocaleString() : "-"}
                    </td>
                    <td className="px-3 py-2">
                      <Badge variant={log.result === "SUCCESS" ? "default" : "destructive"}>{log.result}</Badge>
                    </td>
                    <td className="px-3 py-2 text-xs text-muted-foreground">{log.elapsedMs ?? "-"} ms</td>
                    <td className="px-3 py-2 text-xs text-muted-foreground">{log.message || "-"}</td>
                  </tr>
                ))}
                {!logs.length && (
                  <tr>
                    <td colSpan={4} className="px-3 py-6 text-center text-xs text-muted-foreground">
                      {loadingLogs ? "加载中…" : "暂无测试记录"}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </CardContent>
        </Card>
      </div>

    </div>
  );
}

const parseKdcText = (raw: string) =>
  raw
    .split(/[\n,]/)
    .map((line) => line.trim())
    .filter(Boolean);

const parseKeyValueEntries = (raw?: string) => {
  if (!raw) return [] as [string, string][];
  const entries: [string, string][] = [];
  raw.split(/\r?\n/).forEach((line) => {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) {
      return;
    }
    const eqIndex = trimmed.indexOf("=");
    if (eqIndex <= 0) return;
    const key = trimmed.slice(0, eqIndex).trim();
    const value = trimmed.slice(eqIndex + 1).trim();
    if (key) {
      entries.push([key, value]);
    }
  });
  return entries;
};

const buildJdbcUrl = (values: Partial<FormValues>) => {
  const host = values.host?.trim();
  const principal = values.servicePrincipal?.trim();
  if (!host || !principal) {
    return "";
  }
  const port = values.port ?? 10000;
  const database = values.database?.trim() || "default";
  const params: string[] = [`principal=${principal}`, "AuthMech=1"];
  if (values.useHttpTransport) {
    params.push("transportMode=http");
    if (values.httpPath?.trim()) {
      params.push(`httpPath=${values.httpPath.trim()}`);
    }
  }
  if (values.useSsl) {
    params.push("ssl=true");
  }
  const extraEntries = parseKeyValueEntries(values.extraParams);
  extraEntries.forEach(([key, value]) => {
    if (key === "ssl" && values.useSsl) return;
    params.push(`${key}=${value}`);
  });
  return `jdbc:hive2://${host}:${port}/${database};${params.join(";")}`;
};
