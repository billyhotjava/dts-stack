import type { ReactNode } from "react";
import type { UploadProps } from "antd";
import { Upload as AntdUpload, message } from "antd";
import type { ItemRender, RcFile } from "antd/es/upload/interface";
import { checkFileUploadClassification } from "@/utils/classification";
import { StyledUpload } from "./styles";
import UploadIllustration from "./upload-illustration";
import UploadListItem from "./upload-list-item";

const { Dragger } = AntdUpload;

interface Props extends UploadProps {
	thumbnail?: boolean;
	/** SEC-001: Whether this upload is in a secret-classified module */
	secretModule?: boolean;
	/** SEC-001: User's classification rank (0=PUBLIC, 1=INTERNAL, 2=SECRET, 3=CONFIDENTIAL) */
	userClassificationRank?: number;
	/**
	 * Custom content rendered inside the Dragger area.
	 * When omitted, the default illustration is shown.
	 */
	children?: ReactNode;
	/**
	 * Set to false to render a plain button-style Upload instead of a Dragger.
	 * Useful for compact upload triggers (e.g. "选择文件" button).
	 * @default true
	 */
	dragger?: boolean;
}

const itemRender: (thumbnail: boolean) => ItemRender = (thumbnail) => {
	return function temp(...args) {
		const [, file, , actions] = args;
		return <UploadListItem file={file} actions={actions} thumbnail={thumbnail} />;
	};
};

export function Upload({
	thumbnail = false,
	secretModule = false,
	userClassificationRank,
	beforeUpload,
	children,
	dragger = true,
	...other
}: Props) {
	const classificationBeforeUpload = (file: RcFile, fileList: RcFile[]) => {
		const error = checkFileUploadClassification(file.name, secretModule, userClassificationRank);
		if (error) {
			message.error(error);
			return AntdUpload.LIST_IGNORE;
		}
		return beforeUpload ? beforeUpload(file, fileList) : true;
	};

	if (!dragger) {
		return (
			<AntdUpload {...other} beforeUpload={classificationBeforeUpload}>
				{children}
			</AntdUpload>
		);
	}

	return (
		<StyledUpload $thumbnail={thumbnail}>
			<Dragger {...other} beforeUpload={classificationBeforeUpload} itemRender={itemRender(thumbnail)}>
				{children ?? (
					<div className="opacity-100 hover:opacity-80">
						<p className="m-auto max-w-[200px]">
							<UploadIllustration />
						</p>
						<div>
							<h5 className="mt-4">拖拽或选择文件</h5>
							<p className="text-sm text-gray-500">
								将文件拖拽至此，或点击
								<span className="mx-2 text-primary underline">浏览</span>
								选择本地文件
							</p>
						</div>
					</div>
				)}
			</Dragger>
		</StyledUpload>
	);
}
