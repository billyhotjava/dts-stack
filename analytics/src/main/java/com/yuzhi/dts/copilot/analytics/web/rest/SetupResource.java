package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsUserRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.CollectionService;
import com.yuzhi.dts.copilot.analytics.service.GroupService;
import com.yuzhi.dts.copilot.analytics.service.SetupStateService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseCookies;
import jakarta.servlet.http.HttpServletRequest;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/setup")
public class SetupResource {

    private static final Pattern BASIC_USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-]{2,50}$");

    private final SetupStateService setupStateService;
    private final AnalyticsUserRepository userRepository;
    private final AnalyticsSessionService sessionService;
    private final CollectionService collectionService;
    private final PasswordEncoder passwordEncoder;
    private final GroupService groupService;

    public SetupResource(
            SetupStateService setupStateService,
            AnalyticsUserRepository userRepository,
            AnalyticsSessionService sessionService,
            CollectionService collectionService,
            PasswordEncoder passwordEncoder,
            GroupService groupService) {
        this.setupStateService = setupStateService;
        this.userRepository = userRepository;
        this.sessionService = sessionService;
        this.collectionService = collectionService;
        this.passwordEncoder = passwordEncoder;
        this.groupService = groupService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> setup(@RequestBody SetupRequest request, HttpServletRequest servletRequest) {
        if (setupStateService.isSetupCompleted() || userRepository.count() > 0) {
            return ResponseEntity.status(403)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("The /api/setup route can only be used to create the first user, however a user currently exists.");
        }

        Map<String, String> errors = new LinkedHashMap<>();

        String siteName = Optional.ofNullable(request)
                .map(SetupRequest::prefs)
                .map(SetupPrefs::siteName)
                .map(String::trim)
                .orElse("");
        if (siteName.isBlank()) {
            errors.put("site_name", "value must be a non-blank string.");
        }

        SetupUser user = request == null ? null : request.user();
        String username = user == null ? "" : Objects.toString(user.username(), "").trim();
        if (!BASIC_USERNAME_PATTERN.matcher(username).matches()) {
            errors.put("username", "用户名只能包含字母、数字、下划线和连字符，长度 2-50。");
        }

        String firstName = user == null ? "" : Objects.toString(user.firstName(), "").trim();
        if (firstName.isBlank()) {
            errors.put("first_name", "value must be a non-blank string.");
        }

        String lastName = user == null ? "" : Objects.toString(user.lastName(), "").trim();
        if (lastName.isBlank()) {
            errors.put("last_name", "value must be a non-blank string.");
        }

        String password = user == null ? "" : Objects.toString(user.password(), "");
        if (!isPasswordAcceptable(password)) {
            errors.put("password", "password is too common.");
        }

        String token = request == null ? null : request.token();
        if (!Objects.equals(token, setupStateService.getOrCreateSetupToken())) {
            errors.put("token", "Token does not match the setup token.");
        }

        if (!errors.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("errors", errors));
        }

        if (userRepository.findByUsernameIgnoreCase(username).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("username", "用户名已存在。")));
        }

        AnalyticsUser admin = new AnalyticsUser();
        admin.setUsername(username);
        admin.setFirstName(firstName);
        admin.setLastName(lastName);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setSuperuser(true);
        admin = userRepository.save(admin);
        groupService.ensureUserInDefaultGroups(admin);
        collectionService.ensurePersonalCollection(admin);

        setupStateService.setSiteName(siteName);
        setupStateService.markSetupCompleted();

        UUID sessionId = sessionService.createSession(admin.getId());
        UUID deviceId = UUID.randomUUID();

        boolean secure = "https".equalsIgnoreCase(servletRequest.getHeader("X-Forwarded-Proto"))
                || "https".equalsIgnoreCase(servletRequest.getScheme());

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        for (String cookie : MetabaseCookies.loginCookieHeaders(sessionId, deviceId, secure)) {
            builder.header("Set-Cookie", cookie);
        }
        return builder.body(Map.of("id", sessionId.toString()));
    }

    @PostMapping(path = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> validate(@RequestBody SetupValidateRequest request) {
        if (setupStateService.isSetupCompleted() || userRepository.count() > 0) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("Instance already initialized");
        }

        String token = request == null ? null : request.token();
        if (!Objects.equals(token, setupStateService.getOrCreateSetupToken())) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("token", "Token does not match the setup token.")));
        }

        String engine = Optional.ofNullable(request)
                .map(SetupValidateRequest::details)
                .map(details -> details.get("engine"))
                .map(Object::toString)
                .map(String::trim)
                .orElse("");
        if (engine.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("errors", Map.of("engine", "value must be a valid database engine.")));
        }

        return ResponseEntity.ok(Map.of());
    }

    @GetMapping(path = "/admin_checklist", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> adminChecklist(HttpServletRequest request) {
        Optional<AnalyticsUser> user = sessionService.resolveUser(request);
        if (user.isEmpty() || !user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("You don't have permissions to do that.");
        }
        return ResponseEntity.ok(defaultAdminChecklist());
    }

    @GetMapping(path = "/user_defaults", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> userDefaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("site_name", setupStateService.getSiteName().orElse(""));
        defaults.put("site_locale", "zh");
        defaults.put("report_timezone", ZoneId.systemDefault().getId());
        defaults.put("allow_tracking", false);
        return ResponseEntity.ok(defaults);
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

    private static List<Map<String, Object>> defaultAdminChecklist() {
        List<Map<String, Object>> groups = new ArrayList<>();
        groups.add(group(
                "Get connected",
                task("Add a database", "Get connected", "Connect to your data so your whole team can start to explore.", "/admin/databases/create", true, true),
                task("Set up email", "Get connected", "Add email credentials so you can more easily invite team members and get updates via Pulses.", "/admin/settings/email", true, false),
                task("Set Slack credentials", "Get connected", "Does your team use Slack? If so, you can send automated updates via dashboard subscriptions.", "/admin/settings/slack", true, false),
                task("Invite team members", "Get connected", "Share answers and data with the rest of your team.", "/admin/people/", false, false)));
        groups.add(group(
                "Curate your data",
                task("Hide irrelevant tables", "Curate your data", "If your data contains technical or irrelevant info you can hide it.", "/admin/datamodel/database", false, false),
                task("Organize questions", "Curate your data", "Have a lot of saved questions in Metabase? Create collections to help manage them and add context.", "/collection/root", false, false),
                task("Create metrics", "Curate your data", "Define canonical metrics to make it easier for the rest of your team to get the right answers.", "/admin/datamodel/metrics", false, false),
                task("Create segments", "Curate your data", "Keep everyone on the same page by creating canonical sets of filters anyone can use while asking questions.", "/admin/datamodel/segments", false, false)));
        return groups;
    }

    private static Map<String, Object> group(String name, Map<String, Object>... tasks) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("name", name);
        group.put("tasks", List.of(tasks));
        return group;
    }

    private static Map<String, Object> task(
            String title, String group, String description, String link, boolean triggered, boolean isNextStep) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("title", title);
        task.put("group", group);
        task.put("description", description);
        task.put("link", link);
        task.put("completed", false);
        task.put("triggered", triggered);
        task.put("is_next_step", isNextStep);
        return task;
    }

    public record SetupRequest(@JsonProperty("token") String token, @JsonProperty("prefs") SetupPrefs prefs, @JsonProperty("user") SetupUser user) {}

    public record SetupPrefs(@JsonProperty("site_name") String siteName, @JsonProperty("allow_tracking") Boolean allowTracking) {}

    public record SetupUser(
            @JsonProperty("username") String username,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            @JsonProperty("password") String password) {}

    public record SetupValidateRequest(@JsonProperty("token") String token, @JsonProperty("details") Map<String, Object> details) {}
}
