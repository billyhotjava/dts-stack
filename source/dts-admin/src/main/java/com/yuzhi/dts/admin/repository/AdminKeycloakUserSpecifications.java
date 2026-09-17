package com.yuzhi.dts.admin.repository;

import com.yuzhi.dts.admin.domain.AdminKeycloakUser;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;

/**
 * 用户快照列表的组合过滤条件。
 *
 * <p>固定 JPQL 方法按「关键词 × 院级状态」已有 4 个组合，再叠加账号状态会翻倍；
 * 这里用 Criteria 按需拼接，未给出的条件不参与查询，也不涉及 null 参数绑定。
 */
public final class AdminKeycloakUserSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private AdminKeycloakUserSpecifications() {}

    /**
     * @param keyword          用户名关键词（包含匹配，忽略大小写）；空则不过滤
     * @param mdmEnabled       院级状态 0/1；{@code null} 不过滤
     * @param enabled          账号状态；{@code null} 不过滤
     * @param excludedUsernames 需隐藏的用户名（小写）
     */
    public static Specification<AdminKeycloakUser> snapshotFilter(
        String keyword,
        Integer mdmEnabled,
        Boolean enabled,
        Collection<String> excludedUsernames
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Expression<String> username = cb.lower(root.get("username"));
            predicates.add(cb.isNotNull(root.get("username")));
            if (excludedUsernames != null && !excludedUsernames.isEmpty()) {
                predicates.add(cb.not(username.in(excludedUsernames)));
            }
            if (StringUtils.isNotBlank(keyword)) {
                String pattern = "%" + escapeLike(keyword.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.like(username, pattern, LIKE_ESCAPE));
            }
            if (mdmEnabled != null) {
                predicates.add(cb.equal(root.get("mdmEnabled"), mdmEnabled));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
