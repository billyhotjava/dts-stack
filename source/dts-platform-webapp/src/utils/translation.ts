import type { KeycloakTranslations } from "#/keycloak";
import i18n from "@/locales/i18n";

/**
 * 更新本地化翻译资源
 * @param translations 从后端获取的翻译词表
 */
export const updateLocalTranslations = (translations: KeycloakTranslations) => {
	// 获取当前语言资源
	const currentResources = i18n.getResourceBundle(i18n.language, "translation");

	// 合并Keycloak翻译到现有资源中
	const updatedResources = {
		...currentResources,
		keycloak: translations,
	};

	// 更新i18n资源
	i18n.addResourceBundle(i18n.language, "translation", updatedResources, true, true);
};

/**
 * 获取属性的显示名称
 * @param attributeName 属性名称
 * @returns 翻译后的显示名称
 */
export const getAttributeDisplayName = (attributeName: string): string => {
	// 尝试从keycloak翻译资源中获取
	const key = `profile.attributes.${attributeName}`;
	//const key = attributeName.replace(/\$\{([^}]*)\}/g, '$1');
	const translated = i18n.t(key);

	// 如果翻译结果和key相同，说明没有找到对应的翻译，返回原属性名
	return translated === key ? attributeName : translated;
};

export default {
	updateLocalTranslations,
	getAttributeDisplayName,
};
