import { Button, Checkbox, Drawer, Empty, List, Space, Typography } from "antd";
import { useEffect, useMemo, useState } from "react";
import type { WorkbenchComponentDescriptor, WorkbenchPreferenceItem } from "@/api/services/workbenchService";
import { moveWorkbenchPreferenceItem, normalizeWorkbenchPreferenceItems } from "../workbenchPersonalizationModel";

export type WorkbenchCustomizeDrawerProps = {
	open: boolean;
	availableComponents: WorkbenchComponentDescriptor[];
	items: WorkbenchPreferenceItem[];
	saving?: boolean;
	onClose: () => void;
	onSave: (items: WorkbenchPreferenceItem[]) => void | Promise<void>;
	onReset: () => void | Promise<void>;
};

export function WorkbenchCustomizeDrawer({
	open,
	availableComponents,
	items,
	saving = false,
	onClose,
	onSave,
	onReset,
}: WorkbenchCustomizeDrawerProps) {
	const [draftItems, setDraftItems] = useState<WorkbenchPreferenceItem[]>(() =>
		normalizeWorkbenchPreferenceItems(items, availableComponents),
	);
	const componentByKey = useMemo(
		() => new Map(availableComponents.map((component) => [component.key, component])),
		[availableComponents],
	);
	const selectedCount = draftItems.filter((item) => item.visible).length;

	useEffect(() => {
		if (open) {
			setDraftItems(normalizeWorkbenchPreferenceItems(items, availableComponents));
		}
	}, [availableComponents, items, open]);

	const toggleItem = (key: string, visible: boolean): void => {
		setDraftItems((prev) => prev.map((item) => (item.key === key ? { ...item, visible } : item)));
	};

	return (
		<Drawer
			title="自定义工作台"
			open={open}
			width={520}
			data-testid="workbench-customize-drawer"
			onClose={onClose}
			destroyOnClose
			footer={
				<Space style={{ width: "100%", justifyContent: "space-between" }}>
					<Button onClick={onReset} disabled={saving}>
						恢复默认
					</Button>
					<Space>
						<Button onClick={onClose} disabled={saving}>
							取消
						</Button>
						<Button type="primary" loading={saving} onClick={() => onSave(draftItems)}>
							保存
						</Button>
					</Space>
				</Space>
			}
		>
			<Space direction="vertical" size={16} style={{ width: "100%" }}>
				<Typography.Text type="secondary">
					已选择 {selectedCount} 个组件。每个人保存自己的工作台显示项和顺序。
				</Typography.Text>

				{draftItems.length === 0 ? (
					<Empty description="当前角色暂无可配置组件" />
				) : (
					<List
						dataSource={draftItems}
						rowKey="key"
						renderItem={(item, index) => {
							const component = componentByKey.get(item.key);
							const title = component?.title ?? item.key;
							return (
								<List.Item
									actions={[
										<Button
											key="up"
											size="small"
											disabled={index === 0 || saving}
											onClick={() => setDraftItems((prev) => moveWorkbenchPreferenceItem(prev, item.key, -1))}
										>
											上移
										</Button>,
										<Button
											key="down"
											size="small"
											disabled={index === draftItems.length - 1 || saving}
											onClick={() => setDraftItems((prev) => moveWorkbenchPreferenceItem(prev, item.key, 1))}
										>
											下移
										</Button>,
									]}
								>
									<Checkbox
										checked={item.visible}
										disabled={saving || component?.enabled === false}
										onChange={(event) => toggleItem(item.key, event.target.checked)}
									>
										<Space direction="vertical" size={0}>
											<Typography.Text strong>{title}</Typography.Text>
											<Typography.Text type="secondary" style={{ fontSize: 12 }}>
												{component?.disabledReason || component?.description || "工作台组件"}
											</Typography.Text>
										</Space>
									</Checkbox>
								</List.Item>
							);
						}}
					/>
				)}
			</Space>
		</Drawer>
	);
}

export default WorkbenchCustomizeDrawer;
