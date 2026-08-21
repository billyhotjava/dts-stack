import { Form, Input, Modal, Select, message } from "antd";
import { type FC } from "react";
import { useQueryClient, useMutation } from "@tanstack/react-query";
import { createSavedQuery } from "../api/sqlIdeSaved";

interface SaveQueryDialogProps {
  open: boolean;
  initialSql: string;
  datasourceId?: string | null;
  datasourceName?: string | null;
  existingFolders: string[];
  onClose: () => void;
}

interface FormValues {
  name: string;
  folder?: string[];
}

export const SaveQueryDialog: FC<SaveQueryDialogProps> = ({
  open,
  initialSql,
  datasourceId,
  datasourceName,
  existingFolders,
  onClose,
}) => {
  const [form] = Form.useForm<FormValues>();
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: (values: FormValues) => {
      const folderValue = Array.isArray(values.folder) ? values.folder[0] ?? null : (values.folder ?? null);
      return createSavedQuery({
        name: values.name,
        sqlText: initialSql,
        datasourceId: datasourceId ?? null,
        datasourceName: datasourceName ?? null,
        folder: folderValue || null,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["sqlide", "saved"] });
      message.success("查询已保存");
      form.resetFields();
      onClose();
    },
    onError: () => {
      message.error("保存失败，请重试");
    },
  });

  const handleOk = () => {
    form
      .validateFields()
      .then((values) => {
        mutation.mutate(values);
      })
      .catch(() => {
        // validation errors shown inline
      });
  };

  const handleCancel = () => {
    form.resetFields();
    onClose();
  };

  const folderOptions = existingFolders.map((f) => ({ label: f, value: f }));

  return (
    <Modal
      title="保存查询"
      open={open}
      onOk={handleOk}
      onCancel={handleCancel}
      okText="保存"
      cancelText="取消"
      confirmLoading={mutation.isPending}
      destroyOnClose
    >
      <Form form={form} layout="vertical" style={{ marginTop: 8 }}>
        <Form.Item label="当前数据源">
          <Input value={datasourceName || "未选择数据源"} disabled />
        </Form.Item>
        <Form.Item
          name="name"
          label="查询名称"
          rules={[{ required: true, message: "请输入查询名称" }]}
        >
          <Input placeholder="输入查询名称" autoFocus />
        </Form.Item>
        <Form.Item name="folder" label="文件夹（可选）">
          <Select
            mode="tags"
            placeholder="选择或输入新文件夹名"
            options={folderOptions}
            maxCount={1}
            allowClear
          />
        </Form.Item>
      </Form>
    </Modal>
  );
};
