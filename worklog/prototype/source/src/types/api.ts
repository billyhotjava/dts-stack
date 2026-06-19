/**
 * 与现网 dts-platform-webapp 对齐的统一响应信封。
 * 切换真实后端时可直接复用同名类型。
 */
export enum ResultStatus {
	SUCCESS = 200, // 适配后端 HTTP 状态码
	ERROR = -1,
	TIMEOUT = 401,
}

export interface Result<T = unknown> {
	status: ResultStatus;
	message: string;
	data: T;
}

/** 分页结果信封。pageNum 采用 1-based。 */
export interface PageResult<T> {
	list: T[];
	total: number;
	pageNum: number;
	pageSize: number;
}

export interface PageQuery {
	pageNum?: number;
	pageSize?: number;
	keyword?: string;
}
