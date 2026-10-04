package com.yuzhi.dts.platform.web.rest.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.repository.catalog.CatalogAssetTagBatchWriter;
import com.yuzhi.dts.platform.repository.catalog.CatalogAssetTagRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTagRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagReadVisibilityService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetTagService;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CatalogAssetTagSearchResourceTest {

    private final CatalogAssetTagRepository assetTagRepository = org.mockito.Mockito.mock(CatalogAssetTagRepository.class);
    private final CatalogAssetTagBatchWriter assetTagBatchWriter = org.mockito.Mockito.mock(
        CatalogAssetTagBatchWriter.class
    );
    private final CatalogTagRepository tagRepository = org.mockito.Mockito.mock(CatalogTagRepository.class);
    private final CatalogAssetTagReadVisibilityService readVisibility = org.mockito.Mockito.mock(
        CatalogAssetTagReadVisibilityService.class
    );
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CatalogAssetTagService service = new CatalogAssetTagService(
            tagRepository,
            assetTagRepository,
            assetTagBatchWriter
        );
        mockMvc = MockMvcBuilders.standaloneSetup(new CatalogAssetTagSearchResource(service, readVisibility)).build();
    }

    @Test
    void repeatedTagIdsBindAndFilterPermissionBeforeTotalAndPagination() throws Exception {
        UUID firstTagId = UUID.randomUUID();
        UUID secondTagId = UUID.randomUUID();
        org.mockito.Mockito.when(
            assetTagRepository.findAssetRefsHavingAllTags(
                List.of(firstTagId, secondTagId),
                2,
                null,
                PageRequest.of(0, 500)
            )
        )
            .thenReturn(
                new PageImpl<>(
                    List.of(projection("DATASET", "dataset:orders"), projection("METRIC", "metric:core/revenue")),
                    PageRequest.of(0, 500),
                    2
                )
            );
        org.mockito.Mockito.when(
            readVisibility.filterReadable(
                List.of(new AssetRef("DATASET", "dataset:orders"), new AssetRef("METRIC", "metric:core/revenue")),
                null
            )
        )
            .thenReturn(List.of(new AssetRef("DATASET", "dataset:orders")));

        mockMvc
            .perform(
                get("/api/catalog/asset-tags/search")
                    .param("tagIds", firstTagId.toString(), secondTagId.toString(), firstTagId.toString())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(10))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.content[0].assetType").value("DATASET"))
            .andExpect(jsonPath("$.data.content[1]").doesNotExist());
    }

    @Test
    void invalidUuidReturnsBadRequest() throws Exception {
        mockMvc
            .perform(get("/api/catalog/asset-tags/search").param("tagIds", "not-a-uuid"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void moreThanFiftyDistinctTagsReturnsBadRequest() throws Exception {
        String[] tagIds = IntStream.range(0, 51).mapToObj(index -> UUID.randomUUID().toString()).toArray(String[]::new);

        mockMvc
            .perform(get("/api/catalog/asset-tags/search").param("tagIds", tagIds))
            .andExpect(status().isBadRequest());
    }

    private CatalogAssetTagRepository.AssetRefProjection projection(String assetType, String assetKey) {
        return new CatalogAssetTagRepository.AssetRefProjection() {
            @Override
            public String getAssetType() {
                return assetType;
            }

            @Override
            public String getAssetKey() {
                return assetKey;
            }
        };
    }
}
