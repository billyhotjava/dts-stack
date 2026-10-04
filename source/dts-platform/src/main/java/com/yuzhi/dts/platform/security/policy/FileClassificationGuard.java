package com.yuzhi.dts.platform.security.policy;

import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * SEC-001: File upload classification guard.
 * <p>
 * Non-secret modules must reject files whose name contains classification keywords
 * (e.g. "机密", "秘密"). Secret modules must verify that the user's clearance level
 * is equal to or higher than the file's detected classification.
 */
public final class FileClassificationGuard {

	private static final List<ClassificationKeyword> KEYWORDS = List.of(
		new ClassificationKeyword("机密", DataLevel.DATA_CONFIDENTIAL),
		new ClassificationKeyword("CONFIDENTIAL", DataLevel.DATA_CONFIDENTIAL),
		new ClassificationKeyword("秘密", DataLevel.DATA_SECRET),
		new ClassificationKeyword("SECRET", DataLevel.DATA_SECRET)
	);

	private FileClassificationGuard() {}

	/**
	 * Detect the highest classification level from the file name.
	 * Returns null if no classification keyword is found.
	 */
	public static DataLevel detectFromFileName(String fileName) {
		if (!StringUtils.hasText(fileName)) {
			return null;
		}
		String upper = fileName.toUpperCase(Locale.ROOT);
		DataLevel highest = null;
		for (ClassificationKeyword kw : KEYWORDS) {
			if (upper.contains(kw.keyword.toUpperCase(Locale.ROOT))) {
				if (highest == null || kw.level.rank() > highest.rank()) {
					highest = kw.level;
				}
			}
		}
		return highest;
	}

	/**
	 * Check if a file can be uploaded in a non-secret module.
	 * Returns an error message if rejected, null if allowed.
	 */
	public static String checkNonSecretModule(String fileName) {
		DataLevel detected = detectFromFileName(fileName);
		if (detected != null && detected.rank() >= DataLevel.DATA_SECRET.rank()) {
			return "非密模块禁止上传含\"" + detected.classification() + "\"字样的附件: " + fileName;
		}
		return null;
	}

	/**
	 * Check if a user with the given personnel level can upload a file
	 * with the detected classification in a secret module.
	 * Returns an error message if rejected, null if allowed.
	 */
	public static String checkSecretModule(String fileName, PersonnelLevel userLevel) {
		if (userLevel == null) {
			userLevel = PersonnelLevel.GENERAL;
		}
		DataLevel detected = detectFromFileName(fileName);
		if (detected == null) {
			return null;
		}
		if (detected.rank() > userLevel.rank()) {
			return "您的密级(" + userLevel.name() + ")低于附件密级(" + detected.classification() + ")，禁止上传: " + fileName;
		}
		return null;
	}

	private record ClassificationKeyword(String keyword, DataLevel level) {}
}
