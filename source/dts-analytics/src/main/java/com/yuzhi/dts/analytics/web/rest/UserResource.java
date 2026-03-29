package com.yuzhi.dts.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.config.PlatformAuthProperties;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsUserRepository;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.GroupService;
import com.yuzhi.dts.analytics.web.support.MetabaseLocale;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/user")
@Transactional
public class UserResource {

    private static final Logger LOG = LoggerFactory.getLogger(UserResource.class);

    private final AnalyticsSessionService sessionService;
    private final AnalyticsUserRepository userRepository;
    private final GroupService groupService;
    private final PasswordEncoder passwordEncoder;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String platformBaseUrl;
    private final PlatformAuthProperties authProperties;

    public UserResource(
            AnalyticsSessionService sessionService,
            AnalyticsUserRepository userRepository,
            GroupService groupService,
            PasswordEncoder passwordEncoder,
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper,
            PlatformAuthProperties authProperties,
            @Value("${dts.analytics.platform.base-url:http://dts-platform:8081}") String platformBaseUrl) {
        this.sessionService = sessionService;
        this.userRepository = userRepository;
        this.groupService = groupService;
        this.passwordEncoder = passwordEncoder;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(java.time.Duration.ofMillis(3000))
                .setReadTimeout(java.time.Duration.ofMillis(5000))
                .build();
        this.objectMapper = objectMapper;
        this.authProperties = authProperties;
        this.platformBaseUrl = platformBaseUrl == null || platformBaseUrl.isBlank()
                ? "http://dts-platform:8081" : platformBaseUrl.trim();
    }

    @GetMapping(path = "/current", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> current(HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        return ResponseEntity.ok(toMetabaseUser(user.get(), groupService, MetabaseLocale.resolve(request)));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }
        String locale = MetabaseLocale.resolve(request);
        return ResponseEntity.ok(userRepository.findAll().stream().map(u -> toMetabaseUser(u, groupService, locale)).toList());
    }

    /**
     * Lightweight user search for sharing — accessible to any authenticated user.
     * Returns only id, email and common_name for each active user matching the query.
     * Merges results from both the local analytics_user table and the platform admin API
     * so that platform users who haven't yet logged into analytics are discoverable.
     */
    @GetMapping(path = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> search(@RequestParam(name = "q", defaultValue = "") String query,
                                    HttpServletRequest request) {
        Optional<AnalyticsUser> caller = sessionService.resolveUser(request);
        if (caller.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        String q = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);

        // 1) Local analytics_user results
        Set<String> seenEmails = new LinkedHashSet<>();
        List<Map<String, Object>> localResults = userRepository.findAll().stream()
                .filter(AnalyticsUser::isActive)
                .filter(u -> {
                    if (q.isEmpty()) return true;
                    String email = u.getEmail() == null ? "" : u.getEmail().toLowerCase(java.util.Locale.ROOT);
                    String first = u.getFirstName() == null ? "" : u.getFirstName().toLowerCase(java.util.Locale.ROOT);
                    String last = u.getLastName() == null ? "" : u.getLastName().toLowerCase(java.util.Locale.ROOT);
                    String common = (first + " " + last).trim();
                    return email.contains(q) || first.contains(q) || last.contains(q) || common.contains(q);
                })
                .limit(50)
                .map(u -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", u.getId());
                    item.put("email", u.getEmail());
                    item.put("common_name", "%s %s".formatted(
                            u.getFirstName() == null ? "" : u.getFirstName(),
                            u.getLastName() == null ? "" : u.getLastName()).trim());
                    if (u.getEmail() != null) {
                        seenEmails.add(u.getEmail().toLowerCase(java.util.Locale.ROOT));
                    }
                    return item;
                })
                .toList();

        // 2) Platform admin users — provision into analytics_user if needed, then merge
        List<Map<String, Object>> merged = new java.util.ArrayList<>(localResults);
        try {
            List<Map<String, Object>> platformUsers = fetchPlatformUsers(q, request);
            for (Map<String, Object> pu : platformUsers) {
                if (merged.size() >= 50) break;

                String email = pu.get("email") == null ? null
                        : pu.get("email").toString().trim().toLowerCase(java.util.Locale.ROOT);
                String username = pu.get("username") == null ? null : pu.get("username").toString().trim();
                String fullName = pu.get("fullName") == null ? null : pu.get("fullName").toString().trim();
                if (username == null || username.isEmpty()) continue;

                // Check if already present by email or username-derived email
                boolean duplicate = email != null && !email.isEmpty() && seenEmails.contains(email);
                if (!duplicate) {
                    String lcUser = username.toLowerCase(java.util.Locale.ROOT);
                    duplicate = seenEmails.stream().anyMatch(e -> e.startsWith(lcUser + "@"));
                }
                if (duplicate) continue;

                // Auto-provision into analytics_user so we always have a numeric ID for ACL
                AnalyticsUser provisioned = provisionPlatformUser(username, fullName, email);
                if (provisioned == null) continue;

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", provisioned.getId());
                item.put("email", provisioned.getEmail());
                String commonName = "%s %s".formatted(
                        provisioned.getFirstName() == null ? "" : provisioned.getFirstName(),
                        provisioned.getLastName() == null ? "" : provisioned.getLastName()).trim();
                item.put("common_name", commonName.isEmpty() ? username : commonName);
                merged.add(item);
                if (provisioned.getEmail() != null) {
                    seenEmails.add(provisioned.getEmail().toLowerCase(java.util.Locale.ROOT));
                }
            }
        } catch (Exception ex) {
            LOG.debug("Failed to fetch platform users for search: {}", ex.getMessage());
        }

        return ResponseEntity.ok(merged);
    }

    /**
     * Query platform admin API for users matching the search keyword.
     * Forwards the caller's auth headers so the platform can authenticate the request.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchPlatformUsers(String keyword, HttpServletRequest request) {
        URI uri = UriComponentsBuilder.fromHttpUrl(platformBaseUrl)
                .path("/api/admin/users")
                .queryParam("page", 0)
                .queryParam("size", 50)
                .queryParam("keyword", keyword)
                .build()
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        // Forward authentication headers from the original request
        String authorization = request.getHeader("Authorization");
        if (authorization != null && !authorization.isBlank()) {
            headers.set("Authorization", authorization);
        }
        String cookie = request.getHeader("Cookie");
        if (cookie != null && !cookie.isBlank()) {
            headers.set("Cookie", cookie);
        }
        // Forward platform identity headers set by the reverse proxy
        for (String hdr : List.of("X-DTS-User", "X-DTS-Display-Name", "X-DTS-User-Id", "X-DTS-Roles", "X-DTS-Dept-Code")) {
            String val = request.getHeader(hdr);
            if (val != null && !val.isBlank()) {
                headers.set(hdr, val);
            }
        }
        headers.set("X-DTS-Service", "dts-analytics");

        ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        Map<String, Object> body = response.getBody();
        if (body == null) return List.of();

        // ApiResponse wrapper: { code, message, data: { content: [...], ... } }
        Object dataObj = body.get("data");
        if (dataObj instanceof Map<?, ?> dataMap) {
            Object contentObj = dataMap.get("content");
            if (contentObj instanceof List<?> contentList) {
                return contentList.stream()
                        .filter(o -> o instanceof Map)
                        .map(o -> (Map<String, Object>) o)
                        .toList();
            }
        }
        return List.of();
    }

    /**
     * Provision a platform user into the local analytics_user table so they can
     * be referenced by numeric ID in ACL entries. Idempotent — returns the
     * existing row if the email is already taken.
     */
    private AnalyticsUser provisionPlatformUser(String username, String fullName, String email) {
        String effectiveEmail;
        if (email != null && !email.isBlank() && email.contains("@")) {
            effectiveEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
        } else {
            effectiveEmail = (username.trim() + "@" + authProperties.emailDomain())
                    .toLowerCase(java.util.Locale.ROOT);
        }

        // Check if already exists
        Optional<AnalyticsUser> existing = userRepository.findByEmailIgnoreCase(effectiveEmail);
        if (existing.isPresent()) {
            return existing.get();
        }

        String displayName = (fullName != null && !fullName.isBlank()) ? fullName.trim() : username.trim();

        AnalyticsUser user = new AnalyticsUser();
        user.setEmail(effectiveEmail);
        user.setFirstName(displayName);
        user.setLastName("");
        user.setSuperuser(false);
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));

        try {
            user = userRepository.save(user);
            groupService.ensureUserInDefaultGroups(user);
            return user;
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // Concurrent insert — refetch
            return userRepository.findByEmailIgnoreCase(effectiveEmail).orElse(null);
        }
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        // Any authenticated user can look up basic user info (needed for ACL display names).
        // Full details are still protected — toMetabaseUser only exposes safe fields.
        return userRepository.findById(id)
                .<ResponseEntity<?>>map(u -> ResponseEntity.ok(toMetabaseUser(u, groupService, MetabaseLocale.resolve(request))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@RequestBody JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }

        String email = body == null ? null : Optional.ofNullable(body.get("email")).map(JsonNode::asText).map(String::trim).orElse(null);
        String firstName = body == null ? null : Optional.ofNullable(body.get("first_name")).map(JsonNode::asText).map(String::trim).orElse(null);
        String lastName = body == null ? null : Optional.ofNullable(body.get("last_name")).map(JsonNode::asText).map(String::trim).orElse(null);
        String password = body == null ? null : Optional.ofNullable(body.get("password")).map(JsonNode::asText).orElse(null);
        boolean isSuperuser = body != null && Optional.ofNullable(body.get("is_superuser")).map(JsonNode::asBoolean).orElse(false);

        Map<String, String> errors = new LinkedHashMap<>();
        if (email == null || email.isBlank()) {
            errors.put("email", "value must be a non-blank string.");
        }
        if (firstName == null || firstName.isBlank()) {
            errors.put("first_name", "value must be a non-blank string.");
        }
        if (lastName == null || lastName.isBlank()) {
            errors.put("last_name", "value must be a non-blank string.");
        }
        if (email != null && userRepository.findByEmailIgnoreCase(email).isPresent()) {
            errors.put("email", "email already in use.");
        }
        if (!errors.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("errors", errors));
        }

        String effectivePassword = password == null || password.isBlank() ? generateTemporaryPassword() : password;
        if (!isPasswordAcceptable(effectivePassword)) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("password", "password is too common.")));
        }

        AnalyticsUser created = new AnalyticsUser();
        created.setEmail(email);
        created.setFirstName(firstName);
        created.setLastName(lastName);
        created.setPasswordHash(passwordEncoder.encode(effectivePassword));
        created.setSuperuser(isSuperuser);
        created.setActive(true);
        created = userRepository.save(created);
        groupService.ensureUserInDefaultGroups(created);

        return ResponseEntity.ok(toMetabaseUser(created, groupService, MetabaseLocale.resolve(request)));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(@PathVariable("id") long id, @RequestBody JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }
        Optional<AnalyticsUser> existing = userRepository.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        AnalyticsUser target = existing.get();
        String firstName = body == null ? null : Optional.ofNullable(body.get("first_name")).map(JsonNode::asText).map(String::trim).orElse(null);
        String lastName = body == null ? null : Optional.ofNullable(body.get("last_name")).map(JsonNode::asText).map(String::trim).orElse(null);
        Boolean isActive = body == null ? null : Optional.ofNullable(body.get("is_active")).map(JsonNode::asBoolean).orElse(null);
        Boolean isSuperuser = body == null ? null : Optional.ofNullable(body.get("is_superuser")).map(JsonNode::asBoolean).orElse(null);

        if (firstName != null && !firstName.isBlank()) {
            target.setFirstName(firstName);
        }
        if (lastName != null && !lastName.isBlank()) {
            target.setLastName(lastName);
        }
        if (isActive != null) {
            target.setActive(isActive);
        }
        if (isSuperuser != null) {
            target.setSuperuser(isSuperuser);
        }
        target = userRepository.save(target);
        groupService.ensureUserInDefaultGroups(target);
        return ResponseEntity.ok(toMetabaseUser(target, groupService, MetabaseLocale.resolve(request)));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<?> deactivate(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }
        Optional<AnalyticsUser> existing = userRepository.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        AnalyticsUser target = existing.get();
        target.setActive(false);
        userRepository.save(target);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(path = "/{id}/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> setPassword(@PathVariable("id") long id, @RequestBody JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser() && user.get().getId() != id) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }
        Optional<AnalyticsUser> existing = userRepository.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String password = body == null ? null : Optional.ofNullable(body.get("password")).map(JsonNode::asText).orElse(null);
        if (!isPasswordAcceptable(password)) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("password", "password is too common.")));
        }
        AnalyticsUser target = existing.get();
        target.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(target);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(path = "/{id}/modal/qbnewb", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> qbnewbModal(@PathVariable("id") long id, @RequestBody JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser() && user.get().getId() != id) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }
        return ResponseEntity.noContent().build();
    }

    @PutMapping(path = "/{id}/reactivate")
    public ResponseEntity<?> reactivate(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }
        Optional<AnalyticsUser> existing = userRepository.findById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        AnalyticsUser target = existing.get();
        target.setActive(true);
        userRepository.save(target);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/{id}/send_invite")
    public ResponseEntity<?> sendInvite(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }
        if (userRepository.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    static Map<String, Object> toMetabaseUser(AnalyticsUser user, GroupService groupService, String locale) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("email", user.getEmail());
        result.put("first_name", user.getFirstName());
        result.put("last_name", user.getLastName());
        result.put("common_name", "%s %s".formatted(user.getFirstName(), user.getLastName()).trim());
        result.put("is_active", user.isActive());
        result.put("is_superuser", user.isSuperuser());
        result.put("is_installer", true);
        result.put("is_qbnewb", false);
        result.put("group_ids", groupService.groupIdsForUser(user));
        result.put("personal_collection_id", user.getId());
        result.put("google_auth", false);
        result.put("ldap_auth", false);
        result.put("sso_source", null);
        result.put("login_attributes", Map.of());
        result.put("has_invited_second_user", false);
        result.put("has_question_and_dashboard", false);
        result.put("locale", locale == null || locale.isBlank() ? MetabaseLocale.ZH : locale);
        result.put("date_joined", user.getCreatedAt() == null ? null : user.getCreatedAt().toString());
        result.put("updated_at", user.getUpdatedAt() == null ? null : user.getUpdatedAt().toString());
        result.put("last_login", user.getUpdatedAt() == null ? null : user.getUpdatedAt().toString());
        result.put("first_login", user.getCreatedAt() == null ? null : user.getCreatedAt().toString());
        return result;
    }

    private static boolean isPasswordAcceptable(String password) {
        if (password == null || password.length() < 6) {
            return false;
        }
        for (int i = 0; i < password.length(); i++) {
            if (Character.isDigit(password.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String generateTemporaryPassword() {
        SecureRandom random = new SecureRandom();
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 14; i++) {
            builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        builder.append(random.nextInt(10));
        return builder.toString();
    }
}
