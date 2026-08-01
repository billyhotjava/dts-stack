package com.yuzhi.dts.ingestion.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yuzhi.dts.ingestion.service.etl.FileUploadService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

class FileUploadResourceTest {

    private FileUploadService fileUploadService;
    private FileUploadResource resource;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        fileUploadService = mock(FileUploadService.class);
        resource = new FileUploadResource(fileUploadService);
        logger = (Logger) LoggerFactory.getLogger(FileUploadResource.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void uploadLogsMustExcludeOriginalNameSheetAndStoragePaths() {
        var file = new MockMultipartFile(
            "file",
            "confidential-payroll.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "payload".getBytes(StandardCharsets.UTF_8)
        );
        when(fileUploadService.handleUpload(file, "trace-1")).thenReturn(result());

        resource.uploadFile(file, "trace-1");

        String logs = formattedLogs();
        assertThat(logs).contains("traceId=trace-1", "fileId=file-123");
        assertThat(logs).doesNotContain(
            "confidential-payroll.xlsx",
            "Executive Secrets",
            "/host/private/upload.enc",
            "/container/private/upload.enc"
        );
    }

    @Test
    void uploadFailuresMustLogOnlyStableCodeAndSanitizedTraceIdentifier() {
        var file = new MockMultipartFile(
            "file",
            "secret-file.xlsx",
            null,
            "payload".getBytes(StandardCharsets.UTF_8)
        );
        when(fileUploadService.handleUpload(eq(file), anyString()))
            .thenThrow(new IllegalStateException("password=super-secret at /host/private/upload.enc"));

        assertThatThrownBy(() -> resource.uploadFile(file, "unsafe\npassword=trace-secret"))
            .isInstanceOf(IllegalStateException.class);

        String logs = formattedLogs();
        assertThat(logs).contains("errorCode=FILE_UPLOAD_FAILED");
        assertThat(logs).doesNotContain(
            "secret-file.xlsx",
            "super-secret",
            "trace-secret",
            "/host/private/upload.enc",
            "IllegalStateException"
        );
    }

    @Test
    void parseEndpointsMustNotLogSheetOriginalNamePathsOrFullExceptions() {
        var file = new MockMultipartFile("file", "secret.xlsx", null, new byte[] { 1 });
        when(fileUploadService.handleUploadAndParse(eq(file), eq(10), eq(2), eq("Executive Secrets"), eq("trace-2")))
            .thenThrow(new IllegalArgumentException("token=sheet-secret at /container/private/upload.enc"));

        var response = resource.uploadAndParse(file, 10, 2, "Executive Secrets", "trace-2");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        when(fileUploadService.parseById(eq("file-123"), eq(20), eq(3), eq("Board"), eq("payroll.xlsx")))
            .thenReturn(result());
        resource.parseUploadedFile(
            new FileUploadResource.FileParseByIdRequest("file-123", 20, 3, "Board", "payroll.xlsx"),
            "trace-3"
        );
        when(fileUploadService.parseById(eq("file-456"), eq(30), eq(4), eq("Audit"), eq("audit.xlsx")))
            .thenThrow(new IllegalStateException("clientSecret=parse-secret at /host/private/parse.enc"));
        assertThatThrownBy(() ->
            resource.parseUploadedFile(
                new FileUploadResource.FileParseByIdRequest("file-456", 30, 4, "Audit", "audit.xlsx"),
                "trace-4"
            )
        ).isInstanceOf(IllegalStateException.class);

        String logs = formattedLogs();
        assertThat(logs).contains(
            "errorCode=FILE_UPLOAD_AND_PARSE_FAILED",
            "errorCode=FILE_PARSE_BY_ID_FAILED",
            "fileId=file-123"
        );
        assertThat(logs).doesNotContain(
            "secret.xlsx",
            "Executive Secrets",
            "sheet-secret",
            "/container/private/upload.enc",
            "Board",
            "payroll.xlsx",
            "Audit",
            "audit.xlsx",
            "parse-secret",
            "/host/private/parse.enc",
            "/host/private/upload.enc",
            "IllegalArgumentException",
            "IllegalStateException"
        );
    }

    private FileUploadService.FileUploadResult result() {
        return new FileUploadService.FileUploadResult(
            "/host/private/upload.enc",
            "/container/private/upload.enc",
            "excel",
            List.of(new FileUploadService.FileColumn("id", "string", "id")),
            "confidential-payroll.xlsx",
            "file-123",
            "batch-1",
            "Executive Secrets",
            2,
            List.of(new FileUploadService.SheetMetadata(2, "Executive Secrets")),
            "v1",
            true,
            "hash",
            7L,
            1,
            0,
            List.of(List.of("value")),
            ","
        );
    }

    private String formattedLogs() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (left, right) -> left + "\n" + right);
    }
}
