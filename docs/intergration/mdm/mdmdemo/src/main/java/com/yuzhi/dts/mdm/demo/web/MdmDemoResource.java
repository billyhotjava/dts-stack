package com.yuzhi.dts.mdm.demo.web;

import com.yuzhi.dts.mdm.demo.service.MdmDemoService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MdmDemoResource {

    private final MdmDemoService service;

    public MdmDemoResource(MdmDemoService service) {
        this.service = service;
    }

    @PostMapping("/api/mdm/handshake")
    public ResponseEntity<MdmDemoService.HandshakeResult> handshake() {
        return ResponseEntity.ok(service.handshake());
    }

    @PostMapping("/api/mdm/receive")
    public ResponseEntity<MdmDemoService.CallbackResult> receive(
        @RequestParam Map<String, String> params,
        @RequestParam(name = "file", required = false) MultipartFile file,
        HttpServletRequest request
    ) throws Exception {
        return ResponseEntity.ok(service.receive(params, file, request));
    }
}
