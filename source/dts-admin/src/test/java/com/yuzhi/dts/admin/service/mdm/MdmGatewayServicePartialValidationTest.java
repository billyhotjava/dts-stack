package com.yuzhi.dts.admin.service.mdm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.config.MdmGatewayProperties;
import com.yuzhi.dts.admin.repository.OrganizationRepository;
import com.yuzhi.dts.admin.service.OrganizationService;
import com.yuzhi.dts.admin.service.dto.personnel.PersonnelImportResult;
import com.yuzhi.dts.admin.service.dto.personnel.PersonnelPayload;
import com.yuzhi.dts.admin.service.personnel.PersonnelImportService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 必填校验按记录进行：缺字段的记录剔除并记录，完整记录照常导入，不拦截整个文件。
 */
class MdmGatewayServicePartialValidationTest {

    @TempDir
    Path storage;

    private PersonnelImportService personnelImportService;
    private OrganizationService organizationService;
    private MdmGatewayService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        personnelImportService = mock(PersonnelImportService.class);
        organizationService = mock(OrganizationService.class);
        when(organizationService.syncFromMdm(anyList())).thenAnswer(call -> ((List<?>) call.getArgument(0)).size());
        when(personnelImportService.importFromMdm(anyString(), anyList(), anyList(), anyMap()))
            .thenReturn(new PersonnelImportResult(1L, "COMPLETED_WITH_ERRORS", 2, 1, 1, 0, false));

        ObjectProvider<Executor> executorProvider = mock(ObjectProvider.class);
        when(executorProvider.getIfAvailable(any(Supplier.class))).thenReturn((Executor) Runnable::run);

        MdmGatewayProperties properties = new MdmGatewayProperties();
        properties.setEnabled(true);
        properties.setStoragePath(storage.toString());

        service = new MdmGatewayService(
            new RestTemplateBuilder(),
            properties,
            new ObjectMapper(),
            personnelImportService,
            organizationService,
            mock(OrganizationRepository.class),
            executorProvider
        );
    }

    @Test
    @DisplayName("缺字段的人员与组织被剔除并记录，其余记录照常导入")
    @SuppressWarnings("unchecked")
    void incompleteRecordsAreSkippedWithoutBlockingFile() throws Exception {
        String json = """
            {
              "depts": [
                {"deptCode": "D001", "deptName": "完整部门", "parentCode": "", "status": "1"},
                {"deptCode": "D002", "parentCode": "", "status": "1"}
              ],
              "users": [
                {"userCode": "u-ok", "userName": "完整人员", "deptCode": "D001", "status": "1"},
                {"userCode": "u-bad", "userName": "缺部门人员", "status": "1"}
              ]
            }
            """;

        MdmGatewayService.CallbackResult result = receive(json);

        ArgumentCaptor<List<OrganizationService.MdmOrgRecord>> orgs = ArgumentCaptor.forClass(List.class);
        verify(organizationService).syncFromMdm(orgs.capture());
        assertThat(orgs.getValue()).extracting(OrganizationService.MdmOrgRecord::deptCode).containsExactly("D001");

        ArgumentCaptor<List<PersonnelPayload>> accepted = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<PersonnelImportService.RejectedPayload>> rejected = ArgumentCaptor.forClass(List.class);
        verify(personnelImportService).importFromMdm(anyString(), accepted.capture(), rejected.capture(), anyMap());
        assertThat(accepted.getValue()).extracting(PersonnelPayload::account).containsExactly("u-ok");
        assertThat(rejected.getValue()).hasSize(1);
        assertThat(rejected.getValue().get(0).payload().account()).isEqualTo("u-bad");
        assertThat(rejected.getValue().get(0).reason()).contains("第 2 条").contains("deptCode");

        assertThat(result.invalidUsers).isEqualTo(1);
        assertThat(result.invalidDepts).isEqualTo(1);
        assertThat(result.missingRequired).containsExactlyInAnyOrder("deptCode", "deptName");
    }

    @Test
    @DisplayName("全部人员都不完整时仍创建批次，失败明细可查")
    @SuppressWarnings("unchecked")
    void allUsersRejectedStillRecordsBatch() throws Exception {
        String json = """
            {
              "users": [
                {"userName": "缺账号", "deptCode": "D001"},
                {"deptCode": "D001"}
              ]
            }
            """;

        receive(json);

        ArgumentCaptor<List<PersonnelPayload>> accepted = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<PersonnelImportService.RejectedPayload>> rejected = ArgumentCaptor.forClass(List.class);
        verify(personnelImportService).importFromMdm(anyString(), accepted.capture(), rejected.capture(), anyMap());
        assertThat(accepted.getValue()).isEmpty();
        assertThat(rejected.getValue()).hasSize(2);
        verify(organizationService, never()).syncFromMdm(anyList());
    }

    @Test
    @DisplayName("全部记录完整时不产生剔除明细")
    @SuppressWarnings("unchecked")
    void completeFileHasNoRejectedRecords() throws Exception {
        String json = """
            {
              "depts": [{"deptCode": "D001", "deptName": "部门", "parentCode": ""}],
              "users": [{"userCode": "u1", "userName": "人员", "deptCode": "D001"}]
            }
            """;

        MdmGatewayService.CallbackResult result = receive(json);

        ArgumentCaptor<List<PersonnelImportService.RejectedPayload>> rejected = ArgumentCaptor.forClass(List.class);
        verify(personnelImportService).importFromMdm(anyString(), anyList(), rejected.capture(), anyMap());
        assertThat(rejected.getValue()).isEmpty();
        assertThat(result.invalidUsers).isZero();
        assertThat(result.invalidDepts).isZero();
        assertThat(result.missingRequired).isEmpty();
    }

    @Test
    @DisplayName("本批新建部门的名称在组织同步后补齐，而不是留空")
    @SuppressWarnings("unchecked")
    void newDepartmentNameIsResolvedAfterOrgSync() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean synced = new java.util.concurrent.atomic.AtomicBoolean(false);
        when(organizationService.syncFromMdm(anyList())).thenAnswer(call -> {
            synced.set(true);
            return 1;
        });
        com.yuzhi.dts.admin.domain.OrganizationNode node = new com.yuzhi.dts.admin.domain.OrganizationNode();
        node.setDeptCode("D009");
        node.setName("本批新建部门");
        when(organizationService.findByDeptCodeIgnoreCase("D009"))
            .thenAnswer(call -> synced.get() ? java.util.Optional.of(node) : java.util.Optional.empty());
        String json = """
            {
              "orgIt": [{"deptCode": "D009", "deptName": "本批新建部门", "parentCode": "90"}],
              "user": [{"userCode": "u-new", "userName": "新部门人员", "deptCode": "D009", "status": "1"}]
            }
            """;

        receive(json);

        ArgumentCaptor<List<PersonnelPayload>> accepted = ArgumentCaptor.forClass(List.class);
        verify(personnelImportService).importFromMdm(anyString(), accepted.capture(), anyList(), anyMap());
        assertThat(accepted.getValue()).hasSize(1);
        assertThat(accepted.getValue().get(0).deptCode()).isEqualTo("D009");
        assertThat(accepted.getValue().get(0).deptName()).isEqualTo("本批新建部门");
    }

    private MdmGatewayService.CallbackResult receive(String json) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "mdm-full.json",
            "application/json",
            json.getBytes(StandardCharsets.UTF_8)
        );
        return service.handleReceive(Map.of("dataType", "full"), null, file, null, null);
    }
}
