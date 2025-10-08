import { useEffect, useMemo, useState } from "react";
import { Select, Spin } from "antd";
import useContextStore, { useActiveDept, useActiveScope, useContextActions } from "@/store/contextStore";
import deptService, { type DeptDto } from "@/api/services/deptService";

export default function ScopeSwitcher() {
  const activeScope = useActiveScope();
  const activeDept = useActiveDept();
  const { setActiveScope, initDefaults } = useContextActions();
  const [loading, setLoading] = useState(false);
  const [options, setOptions] = useState<DeptDto[]>([]);

  useEffect(() => {
    try { initDefaults(); } catch {}
  }, [initDefaults]);

  useEffect(() => {
    let mounted = true;
    if (activeScope === "DEPT") {
      setLoading(true);
      deptService
        .listDepartments()
        .then((list) => {
          if (!mounted) return;
          setOptions(list || []);
        })
        .finally(() => mounted && setLoading(false));
    }
    return () => {
      mounted = false;
    };
  }, [activeScope]);

  const selectOptions = useMemo(
    () =>
      options.map((d) => ({
        value: d.code,
        label: d.nameZh || d.nameEn || d.code,
      })),
    [options],
  );

  return (
    <div className="flex items-center gap-3">
      <div className="text-sm text-muted-foreground">上下文</div>
      <Select
        size="small"
        value={activeScope}
        style={{ width: 120 }}
        options={[
          { value: "DEPT", label: "部门 (DEPT)" },
          { value: "INST", label: "研究所 (INST)" },
        ]}
        onChange={(v) => setActiveScope(v as any)}
      />
      {activeScope === "DEPT" ? (
        <Select
          showSearch
          allowClear
          size="small"
          style={{ width: 220 }}
          placeholder="选择部门..."
          value={activeDept}
          notFoundContent={loading ? <Spin size="small" /> : null}
          options={selectOptions}
          filterOption={false}
          onSearch={(kw) => {
            setLoading(true);
            deptService
              .listDepartments(kw)
              .then((list) => setOptions(list || []))
              .finally(() => setLoading(false));
          }}
          onChange={(val) => useContextStore.getState().actions.setActiveDept(val || undefined)}
        />
      ) : null}
    </div>
  );
}
