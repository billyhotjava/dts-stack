import { type FC, type PropsWithChildren, useCallback, useEffect, useRef } from "react";
import { useShallow } from "zustand/react/shallow";
import { useLayoutStore } from "./useLayoutStore";

export const SidePanel: FC<PropsWithChildren> = ({ children }) => {
  const { sidePanelWidth, setSidePanelWidth, activeActivity } = useLayoutStore(
    useShallow((s) => ({
      sidePanelWidth: s.sidePanelWidth,
      setSidePanelWidth: s.setSidePanelWidth,
      activeActivity: s.activeActivity,
    })),
  );
  const draggingRef = useRef(false);
  const startXRef = useRef(0);
  const startWidthRef = useRef(sidePanelWidth);

  const onMouseMove = useCallback(
    (e: MouseEvent) => {
      if (!draggingRef.current) return;
      const delta = e.clientX - startXRef.current;
      setSidePanelWidth(startWidthRef.current + delta);
    },
    [setSidePanelWidth],
  );

  const onMouseUp = useCallback(() => {
    draggingRef.current = false;
    document.body.style.cursor = "";
  }, []);

  useEffect(() => {
    window.addEventListener("mousemove", onMouseMove);
    window.addEventListener("mouseup", onMouseUp);
    return () => {
      window.removeEventListener("mousemove", onMouseMove);
      window.removeEventListener("mouseup", onMouseUp);
    };
  }, [onMouseMove, onMouseUp]);

  if (activeActivity === null) return null;

  return (
    <aside
      data-testid="sqlide-side-panel"
      style={{
        width: sidePanelWidth,
        flexShrink: 0,
        height: "100%",
        borderRight: "1px solid var(--ant-color-border)",
        background: "var(--ant-color-bg-container)",
        display: "flex",
        flexDirection: "column",
        position: "relative",
      }}
    >
      <div style={{ flex: 1, overflow: "auto" }}>{children}</div>
      <div
        data-testid="sqlide-side-panel-resize-handle"
        role="separator"
        aria-orientation="vertical"
        onMouseDown={(e) => {
          draggingRef.current = true;
          startXRef.current = e.clientX;
          startWidthRef.current = sidePanelWidth;
          document.body.style.cursor = "col-resize";
        }}
        style={{
          position: "absolute",
          top: 0,
          right: -3,
          width: 6,
          height: "100%",
          cursor: "col-resize",
        }}
      />
    </aside>
  );
};
