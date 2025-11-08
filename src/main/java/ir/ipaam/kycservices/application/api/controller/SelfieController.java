package ir.ipaam.kycservices.application.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.ipaam.kycservices.application.service.SelfieService;
import ir.ipaam.kycservices.application.service.dto.SelfieUploadResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/kyc")
@Tag(name = "Biometric Service", description = "Capture and persist the customer's selfie used for identity verification.")
public class SelfieController {

    private final SelfieService selfieService;

    @Operation(
            summary = "Upload a selfie",
            description = "Receives a single selfie image for the active KYC process, enforces file size limits, and "
                    + "publishes a SELFIE_UPLOADED message to the workflow engine."
    )
    @PostMapping(path = "/selfie", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadSelfie(
            @RequestPart("selfie") MultipartFile selfie,
            @RequestPart("processInstanceId") String processInstanceId) {
        SelfieUploadResult result = selfieService.uploadSelfie(selfie, processInstanceId);
        return ResponseEntity.status(result.status()).body(result.body());
    }
}
