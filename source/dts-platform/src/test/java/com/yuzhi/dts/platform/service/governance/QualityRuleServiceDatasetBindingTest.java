package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.GovernanceProperties;
import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import com.yuzhi.dts.platform.domain.governance.GovRuleVersion;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleBindingRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleVersionRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.governance.request.QualityRuleUpsertRequest;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class QualityRuleServiceDatasetBindingTest {

    private static final UUID RULE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID VERSION_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID BINDING_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID DATASET_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");

    @Mock
    private GovRuleRepository ruleRepository;

    @Mock
    private GovRuleVersionRepository versionRepository;

    @Mock
    private GovRuleBindingRepository bindingRepository;

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private DefaultLakeDatasetGuard defaultLakeDatasetGuard;

    @Mock
    private AuditService auditService;

    @Mock
    private GovernanceProperties properties;

    @Mock
    private AccessChecker accessChecker;

    @Mock
    private OrganizationVisibilityService organizationVisibilityService;

    private QualityRuleService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder
            .getContext()
            .setAuthentication(
                new TestingAuthenticationToken("actor", "n/a", AuthoritiesConstants.ADMIN)
            );
        service = new QualityRuleService(
            ruleRepository,
            versionRepository,
            bindingRepository,
            datasetRepository,
            defaultLakeDatasetGuard,
            auditService,
            new ObjectMapper().findAndRegisterModules(),
            properties,
            accessChecker,
            organizationVisibilityService
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsVersionBindingFromTheRuleDatasetWhenBindingsAreOmitted() {
        AtomicReference<GovRule> savedRule = new AtomicReference<>();
        when(ruleRepository.findByCode(any())).thenReturn(Optional.empty());
        when(ruleRepository.save(any(GovRule.class))).thenAnswer(invocation -> {
            GovRule rule = invocation.getArgument(0);
            rule.setId(RULE_ID);
            savedRule.set(rule);
            return rule;
        });
        when(ruleRepository.findById(RULE_ID)).thenAnswer(invocation -> Optional.of(savedRule.get()));
        when(versionRepository.save(any(GovRuleVersion.class))).thenAnswer(invocation -> {
            GovRuleVersion version = invocation.getArgument(0);
            version.setId(VERSION_ID);
            return version;
        });
        when(versionRepository.findByRuleIdOrderByVersionDesc(RULE_ID)).thenReturn(List.of());
        when(bindingRepository.save(any(GovRuleBinding.class))).thenAnswer(invocation -> {
            GovRuleBinding binding = invocation.getArgument(0);
            binding.setId(BINDING_ID);
            return binding;
        });

        QualityRuleUpsertRequest request = new QualityRuleUpsertRequest();
        request.setName("订单完整性检查");
        request.setType("COMPLETENESS");
        request.setSeverity("MEDIUM");
        request.setExecutor("hive");
        request.setEnabled(true);
        request.setPublishNow(true);
        request.setDatasetId(DATASET_ID);

        service.createRule(request, "actor");

        ArgumentCaptor<GovRuleBinding> binding = ArgumentCaptor.forClass(GovRuleBinding.class);
        verify(bindingRepository).save(binding.capture());
        assertThat(binding.getValue().getDatasetId()).isEqualTo(DATASET_ID);
        assertThat(binding.getValue().getRuleVersion().getId()).isEqualTo(VERSION_ID);
    }
}
