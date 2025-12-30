import { Icon } from "@/components/icon";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";

export default function AnalyticsPage() {
  const openAnalytics = () => {
    window.open("/analytics", "_blank", "noopener,noreferrer");
  };

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <CardTitle className="text-base">分析</CardTitle>
          <Button onClick={openAnalytics}>
            <Icon icon="solar:chart-2-bold-duotone" /> 打开
          </Button>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground space-y-2">
          <div>将以新窗口打开 Analytics（通过平台 SSO 登录）。</div>
          <div className="text-xs">
            如需将链接纳入门户菜单管理，可把菜单地址配置为 <code>/visualization/analytics</code>。
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

