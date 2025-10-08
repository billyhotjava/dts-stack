import apiClient from "../apiClient";

export interface DeptDto {
  code: string;
  nameZh?: string;
  nameEn?: string;
}

export const DeptApi = {
  List: "/departments",
};

export async function listDepartments(keyword?: string): Promise<DeptDto[]> {
  const params = keyword && keyword.trim() ? { keyword: keyword.trim() } : undefined;
  return apiClient.get<DeptDto[]>({ url: DeptApi.List, params });
}

export default {
  listDepartments,
};

