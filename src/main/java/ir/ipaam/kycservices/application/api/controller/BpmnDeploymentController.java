package ir.ipaam.kycservices.application.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import ir.ipaam.kycservices.domain.model.entity.ProcessDeployment;
import ir.ipaam.kycservices.infrastructure.service.BpmnDeploymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@RestController
@RequestMapping("/bpmn")
@RequiredArgsConstructor
public class BpmnDeploymentController {

    private final BpmnDeploymentService service;

    @Operation(summary = "Deploy BPMN file")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                    schema = @Schema(type = "string", format = "binary"))
    )
    @PostMapping(value = "/deploy", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProcessDeployment> deploy(
            @Parameter(description = "BPMN file to deploy")
            @RequestParam("file") MultipartFile file) {

        try (InputStream is = file.getInputStream()) {
            ProcessDeployment deployment = service.deployIfChanged(is);
            return ResponseEntity.ok(deployment);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
