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
}

const itemRender: (thumbnail: boolean) => ItemRender = (thumbnail) => {
	return function temp(...args) {
		const [, file, , actions] = args;
		return <UploadListItem file={file} actions={actions} thumbnail={thumbnail} />;
	};
};
export function Upload({ thumbnail = false, secretModule = false, userClassificationRank, beforeUpload, ...other }: Props) {
	const classificationBeforeUpload = (file: RcFile, fileList: RcFile[]) => {
		const error = checkFileUploadClassification(file.name, secretModule, userClassificationRank);
		if (error) {
			message.error(error);
			return AntdUpload.LIST_IGNORE;
		}
		return beforeUpload ? beforeUpload(file, fileList) : true;
	};

	return (
		<StyledUpload $thumbnail={thumbnail}>
			<Dragger {...other} beforeUpload={classificationBeforeUpload} itemRender={itemRender(thumbnail)}>
				<div className="opacity-100 hover:opacity-80">
					<p className="m-auto max-w-[200px]">
						<UploadIllustration />
					</p>
					<div>
						<h5 className="mt-4">Drop or Select file</h5>
						<p className="text-sm text-gray-500">
							Drop files here or click
							<span className="mx-2 text-primary underline">browse</span>
							thorough your machine
						</p>
					</div>
				</div>
			</Dragger>
		</StyledUpload>
	);
}
