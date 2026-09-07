import { ThemeLayout } from "#/enum";
import Brand from "@/components/brand";
import { down, useMediaQuery } from "@/hooks";
import { useSettings } from "@/store/settingStore";
import Header from "./header";
import Main from "./main";
import { NavHorizontalLayout, NavMobileLayout, NavVerticalLayout, useFilteredNavData } from "./nav";

export default function DashboardLayout() {
    const isMobile = useMediaQuery(down("md"));
    const { themeLayout } = useSettings();
    const navData = useFilteredNavData();
    const horizontal = !isMobile && themeLayout === ThemeLayout.Horizontal;
    const vertical = !isMobile && !horizontal;
    const paddingLeft = vertical
        ? themeLayout === ThemeLayout.Vertical ? "var(--layout-nav-width)" : "var(--layout-nav-width-mini)"
        : undefined;

    return (
        <div data-slot="slash-layout-root" className="w-full min-h-screen bg-background">
            {vertical ? <NavVerticalLayout data={navData} /> : null}
            <div className="relative w-full min-h-screen flex flex-col transition-[padding] duration-300 ease-in-out" style={{ paddingLeft }}>
                <Header leftSlot={isMobile ? <NavMobileLayout data={navData} /> : horizontal ? <Brand /> : undefined} />
                {horizontal ? <NavHorizontalLayout data={navData} /> : null}
                {/* Keep the route subtree mounted when the viewport changes, preserving unsaved edits. */}
                <Main />
            </div>
        </div>
    );
}
