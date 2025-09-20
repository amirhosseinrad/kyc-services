package ir.ipaam.kycservices.application.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import ir.ipaam.kycservices.application.api.error.FileProcessingException;
import ir.ipaam.kycservices.domain.model.entity.ProcessDeployment;
import ir.ipaam.kycservices.infrastructure.service.BpmnDeploymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/bpmn")
@RequiredArgsConstructor
public class BpmnDeploymentController {

    private final BpmnDeploymentService service;

    @Operation(summary = "Deploy BPMN file")
    @PostMapping(value = "/deploy", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProcessDeployment> deploy(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("No file uploaded");
        }
        try (InputStream is = file.getInputStream()) {
            ProcessDeployment deployment = service.deployIfChanged(is);
            return ResponseEntity.ok(deployment);
        } catch (IOException e) {
            throw new FileProcessingException("Unable to read uploaded file", e);
        }
    }
}
