package com.yuzhi.dts.admin.web.rest;

import com.yuzhi.dts.admin.service.audit.AdminAuditService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class ExportAliasResource {

    private final AdminAuditService auditService;

    public ExportAliasResource(AdminAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping(value = "/audit/export", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<Void> exportAlias(HttpServletResponse response) throws IOException {
        String header = "id,timestamp,actor,action,resource,outcome\n";
        StringBuilder sb = new StringBuilder(header);
        for (AdminAuditService.AuditEvent e : auditService.list(null, null, null, null, null, null)) {
            sb.append(e.id)
                .append(',')
                .append(e.timestamp)
                .append(',')
                .append(Optional.ofNullable(e.actor).orElse(""))
                .append(',')
                .append(Optional.ofNullable(e.action).orElse(""))
                .append(',')
                .append(Optional.ofNullable(e.resource).orElse(""))
                .append(',')
                .append(Optional.ofNullable(e.outcome).orElse(""))
                .append('\n');
        }
        response.setContentType("text/csv");
        response.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));
        return ResponseEntity.ok().build();
    }
}
