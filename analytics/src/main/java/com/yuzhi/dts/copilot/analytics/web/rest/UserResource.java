package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsUserRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.GroupService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseLocale;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/user")
@Transactional
public class UserResource {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-]{2,50}$");

    private final AnalyticsSessionService sessionService;
    private final AnalyticsUserRepository userRepository;
    private final GroupService groupService;
    private final PasswordEncoder passwordEncoder;

    public UserResource(
            AnalyticsSessionService sessionService,
            AnalyticsUserRepository userRepository,
            GroupService groupService,
            PasswordEncoder passwordEncoder) {
        this.sessionService = sessionService;
        this.userRepository = userRepository;
        this.groupService = groupService;
        this.passwordEncoder = passwordEncoder;
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

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!user.get().isSuperuser() && user.get().getId() != id) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }
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

        String username = body == null ? null : Optional.ofNullable(body.get("username")).map(JsonNode::asText).map(String::trim).orElse(null);
        String firstName = body == null ? null : Optional.ofNullable(body.get("first_name")).map(JsonNode::asText).map(String::trim).orElse(null);
        String lastName = body == null ? null : Optional.ofNullable(body.get("last_name")).map(JsonNode::asText).map(String::trim).orElse(null);
        String password = body == null ? null : Optional.ofNullable(body.get("password")).map(JsonNode::asText).orElse(null);
        boolean isSuperuser = body != null && Optional.ofNullable(body.get("is_superuser")).map(JsonNode::asBoolean).orElse(false);

        Map<String, String> errors = new LinkedHashMap<>();
        if (username == null || username.isBlank()) {
            errors.put("username", "value must be a non-blank string.");
        } else if (!USERNAME_PATTERN.matcher(username).matches()) {
            errors.put("username", "用户名只能包含字母、数字、下划线和连字符，长度 2-50。");
        }
        if (firstName == null || firstName.isBlank()) {
            errors.put("first_name", "value must be a non-blank string.");
        }
        if (lastName == null || lastName.isBlank()) {
            errors.put("last_name", "value must be a non-blank string.");
        }
        if (username != null && userRepository.findByUsernameIgnoreCase(username).isPresent()) {
            errors.put("username", "用户名已存在。");
        }
        if (!errors.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("errors", errors));
        }

        String effectivePassword = password == null || password.isBlank() ? generateTemporaryPassword() : password;
        if (!isPasswordAcceptable(effectivePassword)) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("password", "password is too common.")));
        }

        AnalyticsUser created = new AnalyticsUser();
        created.setUsername(username);
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
        target = userRepository.save(target);
        return ResponseEntity.ok(toMetabaseUser(target, groupService, MetabaseLocale.resolve(request)));
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
        result.put("username", user.getUsername());
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
