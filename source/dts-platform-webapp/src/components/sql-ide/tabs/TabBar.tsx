import { Dropdown, type MenuProps, message } from "antd";
import { type FC, useCallback } from "react";
import { confirmCloseDirtyTab } from "./ConfirmCloseDialog";
import { TabItem } from "./TabItem";
import { useTabStore } from "./useTabStore";

export const TabBar: FC = () => {
  const { tabs, activeTabId, openTab, closeTab, updateTab, setActive } =
    useTabStore();

  const handleClose = useCallback(
    async (id: string) => {
      const tab = useTabStore.getState().tabs.find((t) => t.id === id);
      if (!tab) return;
      if (tab.dirty) {
        const ok = await confirmCloseDirtyTab();
        if (!ok) return;
      }
      void closeTab(id);
    },
    [closeTab],
  );

  const handleRename = useCallback(
    (id: string, title: string) => {
      updateTab(id, { title });
    },
    [updateTab],
  );

  const handleOpen = useCallback(() => {
    try {
      openTab();
    } catch {
      message.warning("最多同时打开 30 个 Tab，请关闭部分后再新开");
    }
  }, [openTab]);

  const contextMenuItems = useCallback(
    (_id: string): MenuProps["items"] => [
      { key: "rename", label: "重命名" },
      { key: "close", label: "关闭" },
      { key: "close-others", label: "关闭其他" },
      { key: "close-all", label: "关闭全部" },
    ],
    [],
  );

  const onContextSelect = useCallback(
    (id: string, key: string) => {
      const state = useTabStore.getState();
      switch (key) {
        case "rename": {
          // Rename is handled by TabItem double-click; no-op here
          break;
        }
        case "close":
          void handleClose(id);
          break;
        case "close-others":
          state.tabs
            .filter((t) => t.id !== id)
            .forEach((t) => void handleClose(t.id));
          break;
        case "close-all":
          state.tabs.forEach((t) => void handleClose(t.id));
          break;
      }
    },
    [handleClose],
  );

  return (
    <div
      role="tablist"
      aria-label="SQL IDE Tabs"
      data-testid="sqlide-tab-bar"
      style={{
        display: "flex",
        alignItems: "center",
        gap: 2,
        height: 36,
        padding: "0 8px",
        borderBottom: "1px solid var(--ant-color-border)",
        background: "var(--ant-color-bg-layout)",
        overflowX: "auto",
      }}
    >
      {tabs.map((t) => (
        <Dropdown
          key={t.id}
          trigger={["contextMenu"]}
          menu={{
            items: contextMenuItems(t.id),
            onClick: ({ key }) => onContextSelect(t.id, key),
          }}
        >
          <div>
            <TabItem
              id={t.id}
              title={t.title}
              active={activeTabId === t.id}
              dirty={t.dirty}
              onActivate={setActive}
              onClose={handleClose}
              onRename={handleRename}
            />
          </div>
        </Dropdown>
      ))}
      <button
        type="button"
        aria-label="新建 Tab"
        onClick={handleOpen}
        disabled={tabs.length >= 30}
        style={{
          border: "none",
          background: "transparent",
          cursor: tabs.length >= 30 ? "not-allowed" : "pointer",
          color: "var(--ant-color-text-secondary)",
          fontSize: 16,
          padding: "0 10px",
          height: 28,
        }}
      >
        +
      </button>
    </div>
  );
};
