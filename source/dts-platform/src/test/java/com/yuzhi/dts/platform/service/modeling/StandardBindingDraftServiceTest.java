package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.modeling.StandardBindingDraft;
import com.yuzhi.dts.platform.repository.modeling.StandardBindingDraftRepository;
import com.yuzhi.dts.platform.service.modeling.StandardBindingDraftService.StandardBindingDraftField;
import com.yuzhi.dts.platform.service.modeling.StandardBindingDraftService.StandardBindingDraftRequest;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StandardBindingDraftServiceTest {

    @Mock
    private StandardBindingDraftRepository repository;

    private StandardBindingDraftService service;

    @BeforeEach
    void setUp() {
        service = new StandardBindingDraftService(repository, new ObjectMapper());
    }

    @Test
    @DisplayName("生成字段落标草稿：规范化字段名并持久化完整 payload")
    void create_normalizesAndPersistsPayload() {
        UUID draftId = UUID.randomUUID();
        when(repository.save(any(StandardBindingDraft.class)))
            .thenAnswer(invocation -> {
                StandardBindingDraft draft = invocation.getArgument(0);
                draft.setId(draftId);
                return draft;
            });

        var dto = service.create(
            new StandardBindingDraftRequest(
                "metadata-elements",
                "客户标准",
                List.of(
                    new StandardBindingDraftField(
                        "Customer ID",
                        UUID.randomUUID(),
                        "customer_id",
                        "客户编号",
                        "varchar",
                        false,
                        null,
                        "INTERNAL",
                        "客户主键",
                        "客户",
                        "CRM",
                        true
                    )
                ),
                Map.of("keyword", "客户")
            )
        );

        assertThat(dto.id()).isEqualTo(draftId);
        assertThat(dto.fieldCount()).isEqualTo(1);
        assertThat(dto.fields()).extracting(StandardBindingDraftField::columnName).containsExactly("customer_id");
        assertThat(dto.fields()).extracting(StandardBindingDraftField::dataType).containsExactly("VARCHAR");

        ArgumentCaptor<StandardBindingDraft> captor = ArgumentCaptor.forClass(StandardBindingDraft.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPayloadJson()).contains("customer_id").contains("客户编号");
    }

    @Test
    @DisplayName("读取字段落标草稿：解析 payload 并返回前端可直接消费的字段")
    void get_parsesPayload() throws Exception {
        UUID draftId = UUID.randomUUID();
        UUID standardId = UUID.randomUUID();
        StandardBindingDraft draft = new StandardBindingDraft();
        draft.setId(draftId);
        draft.setSource("metadata-elements");
        draft.setTitle("客户标准");
        draft.setStatus("DRAFT");
        draft.setFieldCount(1);
        draft.setPayloadJson(
            new ObjectMapper()
                .writeValueAsString(
                    Map.of(
                        "fields",
                        List.of(Map.of("columnName", "customer_id", "standardId", standardId, "standardName", "客户编号", "dataType", "VARCHAR")),
                        "metadata",
                        Map.of("keyword", "客户")
                    )
                )
        );
        when(repository.findById(draftId)).thenReturn(Optional.of(draft));

        var dto = service.get(draftId);

        assertThat(dto.id()).isEqualTo(draftId);
        assertThat(dto.fields()).hasSize(1);
        assertThat(dto.fields().get(0).standardId()).isEqualTo(standardId);
        assertThat(dto.metadata()).containsEntry("keyword", "客户");
    }

    @Test
    @DisplayName("无字段时拒绝生成草稿")
    void create_withoutFieldsThrows() {
        assertThatThrownBy(() -> service.create(new StandardBindingDraftRequest("metadata-elements", "空草稿", List.of(), Map.of())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("至少需要一个字段");
    }

    @Test
    @DisplayName("草稿不存在时报 404 上层异常")
    void get_missingThrows() {
        UUID draftId = UUID.randomUUID();
        when(repository.findById(draftId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(draftId)).isInstanceOf(EntityNotFoundException.class).hasMessageContaining("不存在");
    }
}
