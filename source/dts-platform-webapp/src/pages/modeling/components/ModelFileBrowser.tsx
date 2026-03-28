import type { Key } from "react";
import { DeleteOutlined } from "@ant-design/icons";
import { Button, Input, Skeleton, Tree, Typography } from "antd";
import { EmptyState } from "@/components/empty-state";

const { Text } = Typography;

type ModelFileBrowserProps = {
	keyword: string;
	onKeywordChange: (value: string) => void;
	treeData: any[];
	selectedKeys: string[];
	checkedKeys: string[];
	onSelect: (keys: Key[]) => void;
	onCheck: (checkedKeys: string[]) => void;
	selectedCount: number;
	onSelectAllCurrent: () => void;
	onClearSelection: () => void;
	onBatchDelete: () => void;
	batchDeleteDisabled: boolean;
	loading: boolean;
	showEmptyModelsHint: boolean;
};

export default function ModelFileBrowser({
	keyword,
	onKeywordChange,
	treeData,
	selectedKeys,
	checkedKeys,
	onSelect,
	onCheck,
	selectedCount,
	onSelectAllCurrent,
	onClearSelection,
	onBatchDelete,
	batchDeleteDisabled,
	loading,
	showEmptyModelsHint,
}: ModelFileBrowserProps) {
	return (
		<div className="w-64 min-w-[256px] max-w-[256px] border-r border-border bg-card p-4 overflow-x-auto overflow-y-auto [&_.ant-tree-title]:block [&_.ant-tree-title]:whitespace-nowrap [&_.ant-tree-switcher]:flex-shrink-0">
			<div className="mb-3 text-xs font-bold uppercase text-muted-foreground">项目目录</div>
			<Input
				size="small"
				placeholder="搜索模型..."
				value={keyword}
				onChange={(event) => onKeywordChange(event.target.value)}
				className="mb-2"
			/>
			<div className="mb-3 rounded-md border border-border bg-background px-2 py-2">
				<div className="mb-2 flex items-center justify-between gap-2">
					<Text className="text-xs text-muted-foreground">已选 {selectedCount} 项</Text>
					{selectedCount > 0 ? (
						<Button type="link" size="small" className="px-0" onClick={onClearSelection}>
							清空选择
						</Button>
					) : null}
				</div>
				<div className="flex flex-wrap gap-2">
					<Button size="small" onClick={onSelectAllCurrent}>
						全选当前结果
					</Button>
					<Button size="small" danger icon={<DeleteOutlined />} disabled={batchDeleteDisabled} onClick={onBatchDelete}>
						删除所选
					</Button>
				</div>
			</div>
			{loading ? (
				<div style={{ padding: 16 }}>
					<Skeleton active paragraph={{ rows: 8 }} />
				</div>
			) : treeData.length === 0 ? (
				<EmptyState title="暂无项目空间" description="请先在项目空间管理中创建项目空间。" />
			) : (
				<>
					<Tree
						checkable
						defaultExpandAll
						treeData={treeData}
						selectedKeys={selectedKeys}
						checkedKeys={checkedKeys}
						onSelect={(keys) => onSelect(keys as Key[])}
						onCheck={(keys) => onCheck((Array.isArray(keys) ? keys : keys.checked).map((key) => String(key)))}
					/>
					{showEmptyModelsHint ? <div className="mt-3 text-xs text-muted-foreground">当前项目暂无模型。</div> : null}
				</>
			)}
		</div>
	);
}
