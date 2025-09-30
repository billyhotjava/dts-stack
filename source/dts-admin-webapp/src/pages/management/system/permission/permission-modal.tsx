// import { useUserPermission } from "@/store/userStore";

import { AutoComplete, TreeSelect } from "antd";
import { useCallback, useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import type { Permission_Old } from "#/entity";
import { BasicStatus, PermissionType } from "#/enum";
import { Button } from "@/ui/button";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Form, FormControl, FormField, FormItem, FormLabel } from "@/ui/form";
import { Input } from "@/ui/input";
import { ToggleGroup, ToggleGroupItem } from "@/ui/toggle-group";

// Constants
const ENTRY_PATH = "/src/pages";
const PAGES = import.meta.glob("/src/pages/**/*.tsx");
const PAGE_SELECT_OPTIONS = Object.entries(PAGES).map(([path]) => {
	const pagePath = path.replace(ENTRY_PATH, "");
	return {
		label: pagePath,
		value: pagePath,
	};
});

export type PermissionModalProps = {
	formValue: Permission_Old;
	title: string;
	show: boolean;
	onOk: (values: Permission_Old) => void;
	onCancel: VoidFunction;
};

export default function PermissionModal({ title, show, formValue, onOk, onCancel }: PermissionModalProps) {
	const form = useForm<Permission_Old>({
		defaultValues: formValue,
	});

	// TODO: fix
	// const permissions = useUserPermission();
	const permissions: any[] = [];
	const [compOptions, setCompOptions] = useState(PAGE_SELECT_OPTIONS);

	const getParentNameById = useCallback((parentId: string, data: Permission_Old[] | undefined = permissions) => {
		let name = "";
		if (!data || !parentId) return name;
		for (let i = 0; i < data.length; i += 1) {
			if (data[i].id === parentId) {
				name = data[i].name;
			} else if (data[i].children) {
				name = getParentNameById(parentId, data[i].children);
			}
			if (name) {
				break;
			}
		}
		return name;
	}, []);

	const updateCompOptions = useCallback((name: string) => {
		if (!name) return;
		setCompOptions(
			PAGE_SELECT_OPTIONS.filter((path) => {
				return path.value.includes(name.toLowerCase());
			}),
		);
	}, []);

	useEffect(() => {
		form.reset(formValue);
		if (formValue.parentId) {
			const parentName = getParentNameById(formValue.parentId);
			updateCompOptions(parentName);
		}
		// 默认固定为“菜单”类型
		const currentType = form.getValues("type");
		if (currentType == null) {
			form.setValue("type", PermissionType.MENU);
		}
	}, [formValue, form, getParentNameById, updateCompOptions]);

	const onSubmit = (values: Permission_Old) => {
		onOk(values);
	};

	return (
		<Dialog open={show} onOpenChange={(open) => !open && onCancel()}>
			<DialogContent>
				<DialogHeader>
					<DialogTitle>{title}</DialogTitle>
				</DialogHeader>
				<Form {...form}>
					<form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
						<FormField
							control={form.control}
							name="type"
							render={({ field }) => (
								<FormItem>
									<FormLabel>类型</FormLabel>
									<FormControl>
										<ToggleGroup
											type="single"
											variant="outline"
											className="w-auto"
											value={String(field.value)}
											onValueChange={(value) => {
												field.onChange(value);
											}}
										>
											<ToggleGroupItem value={String(PermissionType.MENU)}>菜单</ToggleGroupItem>
										</ToggleGroup>
									</FormControl>
								</FormItem>
							)}
						/>

						<FormField
							control={form.control}
							name="name"
							render={({ field }) => (
								<FormItem>
									<FormLabel>菜单名称</FormLabel>
									<FormControl>
										<Input {...field} />
									</FormControl>
								</FormItem>
							)}
						/>

						<FormField
							control={form.control}
							name="label"
							render={({ field }) => (
								<FormItem>
									<FormLabel>菜单编号</FormLabel>
									<FormControl>
										<Input {...field} />
									</FormControl>
								</FormItem>
							)}
						/>

						<FormField
							control={form.control}
							name="parentId"
							render={({ field }) => (
								<FormItem>
									<FormLabel>上级菜单</FormLabel>
									<FormControl>
										<TreeSelect
											fieldNames={{
												label: "name",
												value: "id",
												children: "children",
											}}
											allowClear
											treeData={permissions}
											value={field.value}
											onSelect={(value, node) => {
												field.onChange(value);
												if (node?.name) {
													updateCompOptions(node.name);
												}
											}}
											onChange={(value) => {
												field.onChange(value);
											}}
										/>
									</FormControl>
								</FormItem>
							)}
						/>

						<FormField
							control={form.control}
							name="route"
							render={({ field }) => (
								<FormItem>
									<FormLabel>路径</FormLabel>
									<FormControl>
										<Input {...field} />
									</FormControl>
								</FormItem>
							)}
						/>

						{form.watch("type") === PermissionType.MENU && (
							<FormField
								control={form.control}
								name="component"
								render={({ field }) => (
									<FormItem>
										<FormLabel>页面组件</FormLabel>
										<FormControl>
											<AutoComplete
												options={compOptions}
												filterOption={(input, option) =>
													((option?.label || "") as string).toLowerCase().includes(input.toLowerCase())
												}
												value={field.value || ""}
												onChange={(value) => field.onChange(value || null)}
											/>
										</FormControl>
									</FormItem>
								)}
							/>
						)}

						<FormField
							control={form.control}
							name="icon"
							render={({ field }) => (
								<FormItem>
									<FormLabel>图标</FormLabel>
									<FormControl>
										<Input {...field} />
									</FormControl>
								</FormItem>
							)}
						/>

						<FormField
							control={form.control}
							name="hide"
							render={({ field }) => (
								<FormItem>
									<FormLabel>显示/隐藏</FormLabel>
									<FormControl>
										<ToggleGroup
											type="single"
											variant="outline"
											value={String(!!field.value)}
											onValueChange={(value) => {
												field.onChange(Boolean(value));
											}}
										>
											<ToggleGroupItem value="false">显示</ToggleGroupItem>
											<ToggleGroupItem value="true">隐藏</ToggleGroupItem>
										</ToggleGroup>
									</FormControl>
								</FormItem>
							)}
						/>

						<FormField
							control={form.control}
							name="order"
							render={({ field }) => (
								<FormItem>
									<FormLabel>排序</FormLabel>
									<FormControl>
										<Input type="number" {...field} />
									</FormControl>
								</FormItem>
							)}
						/>

						<FormField
							control={form.control}
							name="status"
							render={({ field }) => (
								<FormItem>
									<FormLabel>启用状态</FormLabel>
									<FormControl>
										<ToggleGroup
											type="single"
											variant="outline"
											value={String(field.value)}
											onValueChange={(value) => {
												field.onChange(Number(value));
											}}
										>
											<ToggleGroupItem value={String(BasicStatus.ENABLE)}>启用</ToggleGroupItem>
											<ToggleGroupItem value={String(BasicStatus.DISABLE)}>禁用</ToggleGroupItem>
										</ToggleGroup>
									</FormControl>
								</FormItem>
							)}
						/>

						<DialogFooter>
							<Button variant="outline" onClick={onCancel}>
								取消
							</Button>
							<Button type="submit" variant="default">
								确认
							</Button>
						</DialogFooter>
					</form>
				</Form>
			</DialogContent>
		</Dialog>
	);
}
