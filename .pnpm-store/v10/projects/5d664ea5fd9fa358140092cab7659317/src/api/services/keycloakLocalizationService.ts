import apiClient from "../apiClient";

/**
 * Keycloak本地化翻译服务
 * 用于获取Keycloak相关的中文翻译词表
 */

export interface KeycloakTranslations {
	userManagement: Record<string, string>;
	roleManagement: Record<string, string>;
	groupManagement: Record<string, string>;
	commonActions: Record<string, string>;
	statusMessages: Record<string, string>;
	formLabels: Record<string, string>;
	pagination: Record<string, string>;
}

/**
 * Keycloak本地化翻译API服务
 */
export class KeycloakLocalizationService {
	private static readonly BASE_URL = "/keycloak/localization";

	/**
	 * 获取Keycloak中文翻译词表
	 */
	static getChineseTranslations(): Promise<KeycloakTranslations> {
		return apiClient.get<KeycloakTranslations>({
			url: `${KeycloakLocalizationService.BASE_URL}/zh-CN`,
		});
	}
}

export default KeycloakLocalizationService;
