import { useEffect, useState } from "react";
import { getStandardSettings, updateStandardSettings } from "@/api/platformApi";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/ui/card";
import { Button } from "@/ui/button";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Separator } from "@/ui/separator";
import { toast } from "sonner";

export default function DataSettingsPage() {
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [maxSizeMb, setMaxSizeMb] = useState<number>(200);
    const [extensionsText, setExtensionsText] = useState("docx,wps,pdf,xlsx,xls,md,txt");

    useEffect(() => {
        const load = async () => {
            setLoading(true);
            try {
                const data = await getStandardSettings();
                if (data?.maxFileSize) {
                    setMaxSizeMb(Math.round(data.maxFileSize / (1024 * 1024)) || 1);
                }
                if (Array.isArray(data?.allowedExtensions)) {
                    setExtensionsText(data.allowedExtensions.join(","));
                }
            } catch (error: any) {
                console.error(error);
                toast.error(error?.message ?? "加载配置失败");
            } finally {
                setLoading(false);
            }
        };
        load();
    }, []);

    const handleSave = async () => {
        const sizeMb = Number(maxSizeMb) || 1;
        if (sizeMb < 1 || sizeMb > 512) {
            toast.error("文件大小上限需在 1~512MB 之间");
            return;
        }
        const extensions = extensionsText
            .split(",")
            .map((item) => item.trim())
            .filter(Boolean);
        if (!extensions.length) {
            toast.error("请至少保留一种附件后缀名");
            return;
        }
        setSaving(true);
        try {
            await updateStandardSettings({
                maxFileSize: sizeMb * 1024 * 1024,
                allowedExtensions: extensions,
            });
            toast.success("配置已更新");
        } catch (error: any) {
            console.error(error);
            toast.error(error?.message ?? "保存失败");
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="space-y-6">
            <Card>
                <CardHeader>
                    <CardTitle>基础数据设置</CardTitle>
                    <CardDescription>配置数据标准附件上传约束与安全策略</CardDescription>
                </CardHeader>
                <CardContent className="space-y-6">
                    <section className="space-y-3">
                        <div>
                            <Label className="text-sm">附件大小上限（MB）</Label>
                            <Input
                                type="number"
                                min={1}
                                max={512}
                                value={maxSizeMb}
                                onChange={(event) => setMaxSizeMb(Number(event.target.value))}
                                disabled={loading}
                                className="max-w-xs"
                            />
                            <p className="mt-1 text-xs text-muted-foreground">默认 200MB，平台会在上传时阻止超过限制的文件。</p>
                        </div>
                        <Separator />
                        <div>
                            <Label className="text-sm">允许的附件后缀</Label>
                            <Input
                                value={extensionsText}
                                onChange={(event) => setExtensionsText(event.target.value)}
                                disabled={loading}
                                placeholder="docx,wps,pdf,xlsx,xls,md,txt"
                            />
                            <p className="mt-1 text-xs text-muted-foreground">
                                以逗号分隔，例如 docx,wps,pdf,xlsx,xls,md,txt；更新后立即生效。
                            </p>
                        </div>
                    </section>
                    <section className="rounded-md border bg-muted/10 p-4 text-sm leading-relaxed">
                        <p>附件内容将使用平台的 AES-GCM 密钥进行透明加密存储。若需轮换加密密钥，请先在配置中心更新</p>
                        <p className="mt-1 font-mono text-xs">dts.platform.data-standard.encryption-key</p>
                        <p className="mt-1">并重启服务，所有新增附件会自动使用新的密钥版本。</p>
                    </section>
                    <div className="flex justify-end">
                        <Button onClick={handleSave} disabled={saving || loading}>
                            {saving ? "保存中..." : "保存设置"}
                        </Button>
                    </div>
                </CardContent>
            </Card>
        </div>
    );
}
