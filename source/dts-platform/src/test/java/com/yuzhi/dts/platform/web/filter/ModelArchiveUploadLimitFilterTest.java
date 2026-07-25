package com.yuzhi.dts.platform.web.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ModelArchiveUploadLimitFilterTest {

    private final ModelArchiveUploadLimitFilter filter = new ModelArchiveUploadLimitFilter(new ObjectMapper());

    @Test
    void rejectsOversizedRequestsBeforeTheMultipartChain() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getServletPath()).thenReturn(ModelArchiveUploadLimitFilter.ARCHIVE_INSPECT_PATH);
        when(request.getContentLengthLong()).thenReturn(ModelArchiveUploadLimitFilter.MAX_REQUEST_BYTES + 1);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("MODEL_IMPORT_ARCHIVE_TOO_LARGE");
        verifyNoInteractions(chain);
    }

    @Test
    void rejectsChunkedRequestsWithoutADeclaredLength() throws Exception {
        MockHttpServletRequest request = archiveRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(411);
        assertThat(response.getContentAsString()).contains("MODEL_IMPORT_ARCHIVE_LENGTH_REQUIRED");
        verifyNoInteractions(chain);
    }

    @Test
    void passesBoundedRequestsToMultipartResolution() throws Exception {
        MockHttpServletRequest request = archiveRequest();
        request.setContent(new byte[] { 1, 2, 3 });
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    private static MockHttpServletRequest archiveRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", ModelArchiveUploadLimitFilter.ARCHIVE_INSPECT_PATH);
        request.setServletPath(ModelArchiveUploadLimitFilter.ARCHIVE_INSPECT_PATH);
        request.setContentType("multipart/form-data; boundary=test");
        return request;
    }
}
