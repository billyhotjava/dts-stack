package com.yuzhi.dts.copilot.analytics.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsAnalysisDraft;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsAnalysisDraftRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalysisDraftService;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.EntityIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AnalysisDraftResourceTest {

    private MutableSessionService sessionService;
    private InMemoryDraftRepository draftRepository;
    private InMemoryCardRepository cardRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        sessionService = new MutableSessionService();
        draftRepository = new InMemoryDraftRepository();
        cardRepository = new InMemoryCardRepository();
        AnalysisDraftService service = new AnalysisDraftService(
                draftRepository.repository(),
                cardRepository.repository(),
                new EntityIdGenerator(),
                null,
                new ObjectMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(new AnalysisDraftResource(sessionService, service, new ObjectMapper()))
                .build();
    }

    @Test
    void createListAndArchiveShouldManageDraftLifecycleForCurrentUser() throws Exception {
        sessionService.setResolvedUser(buildUser(7L, "biadmin"));

        mockMvc.perform(post("/api/analysis-drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question":"中石油项目目前有多少在摆绿植",
                                  "database_id":6,
                                  "sql_text":"select 1",
                                  "source_type":"copilot",
                                  "response_kind":"REPORT_DRAFT",
                                  "data_surface":"L1_DBT_MART",
                                  "quality_level":"MEDIUM",
                                  "quality_notes":["2025年5月以后数据较可用"],
                                  "report_code":"prs.flowerbiz.lease_execution_monthly"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source_type").value("copilot"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.database_id").value(6))
                .andExpect(jsonPath("$.response_kind").value("REPORT_DRAFT"))
                .andExpect(jsonPath("$.data_surface").value("L1_DBT_MART"))
                .andExpect(jsonPath("$.quality_level").value("MEDIUM"))
                .andExpect(jsonPath("$.quality_notes").value("2025年5月以后数据较可用"))
                .andExpect(jsonPath("$.report_code").value("prs.flowerbiz.lease_execution_monthly"))
                .andExpect(jsonPath("$.question").value("中石油项目目前有多少在摆绿植"));

        mockMvc.perform(get("/api/analysis-drafts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("DRAFT"));

        mockMvc.perform(post("/api/analysis-drafts/1/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        mockMvc.perform(get("/api/analysis-drafts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void saveCardShouldPromoteDraftToSavedQueryAndLinkCard() throws Exception {
        sessionService.setResolvedUser(buildUser(7L, "biadmin"));

        draftRepository.put(draft(1L, 7L, "DRAFT", "采购汇总", "select * from purchase_summary", 6L));

        mockMvc.perform(post("/api/analysis-drafts/1/save-card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draft.status").value("SAVED_QUERY"))
                .andExpect(jsonPath("$.draft.linked_card_id").value(1))
                .andExpect(jsonPath("$.card.id").value(1))
                .andExpect(jsonPath("$.card.name").value("采购汇总"))
                .andExpect(jsonPath("$.card.database_id").value(6))
                .andExpect(jsonPath("$.card.dataset_query.type").value("native"));
    }

    private static AnalyticsUser buildUser(Long id, String username) {
        AnalyticsUser user = new AnalyticsUser();
        user.setId(id);
        user.setUsername(username);
        user.setFirstName("Bi");
        user.setLastName("Admin");
        user.setPasswordHash("x");
        user.setSuperuser(true);
        user.setActive(true);
        return user;
    }

    private static AnalyticsAnalysisDraft draft(Long id, Long creatorId, String status, String title, String sql, Long databaseId)
            throws Exception {
        AnalyticsAnalysisDraft draft = new AnalyticsAnalysisDraft();
        draft.setEntityId("draft-" + id);
        draft.setTitle(title);
        draft.setSourceType("copilot");
        draft.setQuestion(title);
        draft.setDatabaseId(databaseId);
        draft.setSqlText(sql);
        draft.setSuggestedDisplay("table");
        draft.setStatus(status);
        draft.setCreatorId(creatorId);
        setField(draft, "id", id);
        setField(draft, "createdAt", Instant.parse("2026-03-21T00:00:00Z"));
        setField(draft, "updatedAt", Instant.parse("2026-03-21T00:00:00Z"));
        return draft;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class MutableSessionService extends AnalyticsSessionService {

        private Optional<AnalyticsUser> resolvedUser = Optional.empty();

        private MutableSessionService() {
            super(null, null, null);
        }

        void setResolvedUser(AnalyticsUser user) {
            this.resolvedUser = Optional.ofNullable(user);
        }

        @Override
        public Optional<AnalyticsUser> resolveUser(HttpServletRequest request) {
            return resolvedUser;
        }
    }

    private static final class InMemoryDraftRepository implements InvocationHandler {

        private final AtomicLong sequence = new AtomicLong(1);
        private final List<AnalyticsAnalysisDraft> drafts = new ArrayList<>();
        private final AnalyticsAnalysisDraftRepository repository;

        private InMemoryDraftRepository() {
            this.repository = (AnalyticsAnalysisDraftRepository) Proxy.newProxyInstance(
                    AnalyticsAnalysisDraftRepository.class.getClassLoader(),
                    new Class<?>[] {AnalyticsAnalysisDraftRepository.class},
                    this);
        }

        AnalyticsAnalysisDraftRepository repository() {
            return repository;
        }

        void put(AnalyticsAnalysisDraft draft) {
            drafts.add(draft);
            sequence.set(Math.max(sequence.get(), Optional.ofNullable(draft.getId()).orElse(0L) + 1));
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("save".equals(name)) {
                AnalyticsAnalysisDraft draft = (AnalyticsAnalysisDraft) args[0];
                if (draft.getId() == null) {
                    setField(draft, "id", sequence.getAndIncrement());
                    if (draft.getCreatedAt() == null) {
                        setField(draft, "createdAt", Instant.now());
                    }
                }
                setField(draft, "updatedAt", Instant.now());
                drafts.removeIf(existing -> existing.getId().equals(draft.getId()));
                drafts.add(draft);
                return draft;
            }
            if ("findAllByCreatorIdAndStatusNotOrderByUpdatedAtDesc".equals(name)) {
                Long creatorId = (Long) args[0];
                String excludedStatus = (String) args[1];
                return drafts.stream()
                        .filter(draft -> creatorId.equals(draft.getCreatorId()))
                        .filter(draft -> !excludedStatus.equalsIgnoreCase(String.valueOf(draft.getStatus())))
                        .sorted(Comparator.comparing(
                                AnalyticsAnalysisDraft::getUpdatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .toList();
            }
            if ("findByIdAndCreatorId".equals(name)) {
                Long id = (Long) args[0];
                Long creatorId = (Long) args[1];
                return drafts.stream()
                        .filter(draft -> id.equals(draft.getId()))
                        .filter(draft -> creatorId.equals(draft.getCreatorId()))
                        .findFirst();
            }
            if ("delete".equals(name)) {
                AnalyticsAnalysisDraft draft = (AnalyticsAnalysisDraft) args[0];
                drafts.removeIf(existing -> existing.getId().equals(draft.getId()));
                return null;
            }
            if ("toString".equals(name)) {
                return "InMemoryDraftRepository";
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(this);
            }
            if ("equals".equals(name)) {
                return proxy == args[0];
            }
            throw new UnsupportedOperationException("Unexpected repository method: " + name);
        }
    }

    private static final class InMemoryCardRepository implements InvocationHandler {

        private final AtomicLong sequence = new AtomicLong(1);
        private final List<AnalyticsCard> cards = new ArrayList<>();
        private final AnalyticsCardRepository repository;

        private InMemoryCardRepository() {
            this.repository = (AnalyticsCardRepository) Proxy.newProxyInstance(
                    AnalyticsCardRepository.class.getClassLoader(),
                    new Class<?>[] {AnalyticsCardRepository.class},
                    this);
        }

        AnalyticsCardRepository repository() {
            return repository;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("save".equals(name)) {
                AnalyticsCard card = (AnalyticsCard) args[0];
                if (card.getId() == null) {
                    setField(card, "id", sequence.getAndIncrement());
                    setField(card, "createdAt", Instant.now());
                }
                setField(card, "updatedAt", Instant.now());
                cards.removeIf(existing -> existing.getId().equals(card.getId()));
                cards.add(card);
                return card;
            }
            if ("findById".equals(name)) {
                Long id = (Long) args[0];
                return cards.stream().filter(card -> id.equals(card.getId())).findFirst();
            }
            if ("toString".equals(name)) {
                return "InMemoryCardRepository";
            }
            if ("hashCode".equals(name)) {
                return System.identityHashCode(this);
            }
            if ("equals".equals(name)) {
                return proxy == args[0];
            }
            throw new UnsupportedOperationException("Unexpected repository method: " + name);
        }
    }
}
