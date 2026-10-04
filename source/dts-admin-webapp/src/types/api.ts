import type { ResultStatus } from "./enum";

export type ResultStatusLike = ResultStatus | number | string;

export interface Result<T = unknown> {
	status: ResultStatusLike;
	message: string;
	data: T;
}
