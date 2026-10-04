package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ArchitectureDictionaryWriteGuard {

    public static final String WRITE_EXPRESSION =
        "hasAnyAuthority('ROLE_ADMIN', 'ROLE_OP_ADMIN', 'ROLE_INST_DATA_OWNER')";

    private static final Set<String> WRITE_AUTHORITIES = Set.of(
        AuthoritiesConstants.ADMIN,
        AuthoritiesConstants.OP_ADMIN,
        AuthoritiesConstants.INST_DATA_OWNER
    );

    public void requireWriteAccess() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (
            authentication == null ||
            !authentication.isAuthenticated() ||
            authentication instanceof AnonymousAuthenticationToken
        ) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Authentication is required to write platform architecture dictionaries"
            );
        }
        boolean allowed = authentication
            .getAuthorities()
            .stream()
            .anyMatch(authority -> WRITE_AUTHORITIES.contains(authority.getAuthority()));
        if (!allowed) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Platform architecture dictionary is read-only for this role"
            );
        }
    }
}
