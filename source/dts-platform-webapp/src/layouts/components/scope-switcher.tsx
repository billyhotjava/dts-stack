import { useCallback, useEffect, useMemo, useState } from "react";
import { Modal, Select, Spin, Tag } from "antd";
import useContextStore, { useActiveDept, useActiveScope, useContextActions } from "@/store/contextStore";
import { useUserInfo } from "@/store/userStore";
import { PERSON_SECURITY_LEVELS } from "@/constants/governance";
import deptService, { type DeptDto } from "@/api/services/deptService";

export default function ScopeSwitcher() {
  const activeScope = useActiveScope();
  const activeDept = useActiveDept();
  const { setActiveScope, initDefaults } = useContextActions();
  const [loading, setLoading] = useState(false);
  const [options, setOptions] = useState<DeptDto[]>([]);
  const userInfo = useUserInfo();

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

  const resolvePersonnelLevel = useCallback(() => {
    try {
      const attrs: any = (userInfo as any)?.attributes || {};
      const arr = Array.isArray(attrs.personnel_security_level)
        ? attrs.personnel_security_level
        : attrs.person_security_level
        ? [attrs.person_security_level]
        : [];
      const raw = String((arr && arr[0]) || "").toUpperCase();
      if (["GENERAL", "IMPORTANT", "CORE"].includes(raw)) return raw as "GENERAL" | "IMPORTANT" | "CORE";
    } catch {}
    return undefined;
  }, [userInfo]);

  const level = resolvePersonnelLevel();
  const levelZh = useMemo(() => {
    if (!level) return undefined;
    const map = new Map(PERSON_SECURITY_LEVELS.map((it) => [it.value, it.label] as const));
    return map.get(level) || level;
  }, [level]);

  const confirmSwitch = useCallback(async (): Promise<boolean> => {
    return new Promise((resolve) => {
      Modal.confirm({
        title: "切换上下文",
        content: "切换上下文将影响访问权限与可见范围，是否继续？",
        okText: "继续",
        cancelText: "取消",
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      });
    });
  }, []);

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
        onChange={async (v) => {
          const ok = await confirmSwitch();
          if (ok) setActiveScope(v as any);
        }}
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
          onChange={async (val) => {
            const ok = await confirmSwitch();
            if (ok) useContextStore.getState().actions.setActiveDept((val as string) || undefined);
          }}
        />
      ) : null}
      {level ? (
        <Tag color={level === "CORE" ? "red" : level === "IMPORTANT" ? "gold" : "default"} style={{ marginLeft: 8 }}>
          人员密级: {levelZh}
        </Tag>
      ) : null}
    </div>
  );
}
