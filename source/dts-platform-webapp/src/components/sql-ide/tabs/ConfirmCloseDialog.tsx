import { Modal } from "antd";

export async function confirmCloseDirtyTab(): Promise<boolean> {
  return new Promise((resolve) => {
    Modal.confirm({
      title: "关闭 Tab",
      content: "该 Tab 有未同步的修改，确认关闭吗？",
      okText: "关闭",
      cancelText: "取消",
      okButtonProps: { danger: true },
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    });
  });
}

export async function confirmCloseDirtyTabs(count: number): Promise<boolean> {
  return new Promise((resolve) => {
    Modal.confirm({
      title: "关闭多个 Tab",
      content: `其中 ${count} 个 Tab 有未同步的修改，确认全部关闭吗？`,
      okText: "全部关闭",
      cancelText: "取消",
      okButtonProps: { danger: true },
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    });
  });
}
