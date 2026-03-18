import type { UploadProps } from "antd";
import { Upload as AntdUpload, message } from "antd";
import type { ItemRender, RcFile } from "antd/es/upload/interface";
import { StyledUpload } from "./styles";
import UploadIllustration from "./upload-illustration";
import UploadListItem from "./upload-list-item";

const { Dragger } = AntdUpload;

const CLASSIFIED_KEYWORDS = ["机密", "秘密"];

interface Props extends UploadProps {
	thumbnail?: boolean;
}

const itemRender: (thumbnail: boolean) => ItemRender = (thumbnail) => {
	return function temp(...args) {
		const [, file, , actions] = args;
		return <UploadListItem file={file} actions={actions} thumbnail={thumbnail} />;
	};
};
export function Upload({ thumbnail = false, beforeUpload, ...other }: Props) {
	const classificationBeforeUpload = (file: RcFile, fileList: RcFile[]) => {
		const matched = CLASSIFIED_KEYWORDS.find((kw) => file.name.includes(kw));
		if (matched) {
			message.error(`非密模块禁止上传含"${matched}"字样的附件`);
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
