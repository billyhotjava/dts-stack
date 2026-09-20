package com.yuzhi.dts.admin.service.personnel;

import com.yuzhi.dts.admin.domain.AdminKeycloakUser;
import com.yuzhi.dts.admin.repository.AdminKeycloakUserRepository;
import java.math.BigInteger;
import java.util.Locale;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 为人员编码分配 Keycloak 用户名。
 *
 * <p>Keycloak 的用户名一律小写存储、按小写做唯一性，只差大小写的两个编码无法共存。
 * 现场约定 userCode 不同即为不同账号（大小写不同的写法用于标记部门变更），因此这里给
 * 撞车的那一个分配带后缀的用户名，让两个账号都能存在：
 *
 * <ol>
 *   <li>该编码已有账号 → 沿用已记录的用户名，保证反复同步结果稳定；</li>
 *   <li>小写名没被别的编码占用 → 直接用小写名（绝大多数人走这条，含纯大写编码的人，
 *       其 Keycloak 用户名与现状一致，登录不受影响）；</li>
 *   <li>小写名已被别的编码占用 → 小写名 + 后缀，后缀由大小写位置推导，同一编码恒定。</li>
 * </ol>
 *
 * <p>注意：带后缀的账号无法用原始编码登录（Keycloak 查用户名不区分大小写，会命中不带
 * 后缀的那个）。按现场约定，这类账号仅用于标记部门变更，不用于登录。
 */
@Service
public class KeycloakUsernameAllocator {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakUsernameAllocator.class);
    /** Keycloak 的用户名校验拒绝 {@code ~} 等字符（实测返回 error-username-invalid-character）；
     *  {@code .} 在默认校验下可用。 */
    private static final char SUFFIX_SEPARATOR = '.';
    private static final int MAX_DISAMBIGUATION_ATTEMPTS = 20;
    /** Keycloak 用户名列长度上限，与 admin_keycloak_user.username 一致。 */
    private static final int MAX_USERNAME_LENGTH = 64;

    private final AdminKeycloakUserRepository userRepository;

    public KeycloakUsernameAllocator(AdminKeycloakUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * @param personCode 原始人员编码（保留大小写），为空时退化为按账号名分配
     * @param account    人员账号，MDM 场景与 personCode 相同
     * @return 该人员在 Keycloak 中使用的用户名（全小写）
     */
    public String allocate(String personCode, String account) {
        String code = StringUtils.trimToNull(personCode);
        String plain = normalize(StringUtils.firstNonBlank(StringUtils.trimToNull(account), code));
        if (plain == null) {
            return null;
        }
        if (code == null) {
            return plain;
        }
        Optional<AdminKeycloakUser> byCode = userRepository.findFirstByPersonCode(code);
        if (byCode.isPresent()) {
            String existing = StringUtils.trimToNull(byCode.orElseThrow().getUsername());
            if (existing != null) {
                return existing;
            }
        }
        Optional<AdminKeycloakUser> occupant = userRepository.findByUsernameIgnoreCase(plain);
        if (occupant.isEmpty()) {
            return plain;
        }
        String occupantCode = StringUtils.trimToNull(occupant.orElseThrow().getPersonCode());
        // 占用者没有记录过编码（历史数据或 DTS 内建账号）时视为可认领，避免为存量账号凭空改名。
        if (occupantCode == null || occupantCode.equals(code)) {
            return plain;
        }
        String allocated = ensureFree(withSuffix(plain, code), code);
        LOG.info("allocate suffixed keycloak username personCode={} username={} occupiedBy={}", code, allocated, occupantCode);
        return allocated;
    }

    /**
     * 推导出的名字仍可能被占用（编码本身就含分隔符时），逐次追加标记直到空闲。
     */
    private String ensureFree(String candidate, String code) {
        String name = candidate;
        for (int attempt = 0; attempt < MAX_DISAMBIGUATION_ATTEMPTS; attempt++) {
            Optional<AdminKeycloakUser> holder = userRepository.findByUsernameIgnoreCase(name);
            if (holder.isEmpty()) {
                return name;
            }
            String holderCode = StringUtils.trimToNull(holder.orElseThrow().getPersonCode());
            if (holderCode == null || holderCode.equals(code)) {
                return name;
            }
            name = withSuffix(candidate, code + attempt);
        }
        throw new PersonnelImportException("无法为人员编码 " + code + " 分配可用的 Keycloak 用户名");
    }

    private String normalize(String value) {
        String trimmed = StringUtils.trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    /**
     * 后缀由编码中大写字母的位置推导（位图转 36 进制），同一编码恒定、不同写法必不相同，
     * 与导入顺序无关。
     */
    private String withSuffix(String plain, String code) {
        BigInteger mask = BigInteger.ZERO;
        for (int i = 0; i < code.length(); i++) {
            if (Character.isUpperCase(code.charAt(i))) {
                mask = mask.setBit(i);
            }
        }
        String suffix = SUFFIX_SEPARATOR + mask.toString(36);
        if (plain.length() + suffix.length() <= MAX_USERNAME_LENGTH) {
            return plain + suffix;
        }
        return plain.substring(0, MAX_USERNAME_LENGTH - suffix.length()) + suffix;
    }
}
